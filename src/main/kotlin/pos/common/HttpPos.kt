package pos.common

import org.apache.http.Header
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.util.EntityUtils
import pos.RestaurantStatus
import java.util.*
import kotlin.random.Random

abstract class HttpPos(
    posName: String,
    val sleepFromSec: Int = 20,
    val sleepToSec: Int = 50,
    val useProxy: Boolean = true,
    val nbThreads: Int = 13
) : BasePos(posName) {

    var proxyHelper = HttpProxyHelper()

    var restaurantRequestQueue: Queue<RestaurantHttpRequest> = LinkedList<RestaurantHttpRequest>()
    override fun start() {

        initRequestsQueue()
        val totalItems = restaurantRequestQueue.size

        val threads = List(nbThreads) { i -> i + 1 }
        proxyHelper.proxies.subList(0, nbThreads)
            .parallelStream()
            .forEach { proxy ->
                while (restaurantRequestQueue.size > 0) {
                    val nextRequest = getNextRestaurantRequest()
                    if (nextRequest != null) {
                        val treated = totalItems - restaurantRequestQueue.size
                        val percent = treated * 100 / totalItems
                        if (database.restaurantMustBeCreatedOrUpdated(nextRequest.id)) {
                            var proxyDetails = if (useProxy) "with  proxy ${proxy.host} " else ""
                            println("Handling id ${nextRequest.id} $proxyDetails($treated/$totalItems | $percent%) (waiting between $sleepFromSec and $sleepToSec sec)")

                            Thread.sleep(Random.nextLong(sleepFromSec * 1000L, sleepToSec * 1000L))
                            try {
                                HttpHelper.createClient(if (useProxy) proxy else null)
                                    .use { closeableHttpClient ->
                                        closeableHttpClient.execute(nextRequest.request)
                                            .use { response ->
                                                var responseBody = EntityUtils.toString(response.entity)

                                                var status = determineResponseStatus(nextRequest, responseBody,response.statusLine.statusCode,response.allHeaders)
                                                if (status == RestaurantStatus.FOUND) {
                                                    val linkedRequest: HttpRequestBase? =
                                                        getLinkedRequest(nextRequest, responseBody,response.allHeaders)
                                                    val responseLinked: String? = if (linkedRequest != null) {
                                                        executeLinkedRequests(
                                                            linkedRequest,
                                                            if (useProxy) proxy else null
                                                        )
                                                    } else null

                                                    val definitiveResponse =
                                                        if (!responseLinked.isNullOrBlank()) responseLinked else responseBody
                                                    println("!! Restaurant with id #${nextRequest.id} is available !!")
                                                    saveSuccessRestaurantResponse(
                                                        nextRequest,
                                                        definitiveResponse,
                                                        nextRequest.extra
                                                    )
                                                } else {
                                                    println("Restaurant with id #${nextRequest.id} seems not available")
                                                    saveFailedRestaurantResponse(
                                                        nextRequest,
                                                        status,
                                                        responseBody,
                                                        nextRequest.extra
                                                    )
                                                }
                                            }
                                    }


                            } catch (e: Exception) {
                                println("Fail to fetch request #${nextRequest.id} : ${e.javaClass.simpleName}")
                                var content = e.javaClass.simpleName + " : " + e.localizedMessage + "\r\n" + nextRequest
                                database.saveRestaurant(
                                    nextRequest.id,
                                    RestaurantStatus.ERROR,
                                    nextRequest.request.uri.toString(),
                                    content,
                                    nextRequest.extra
                                )


                            }
                        } else {
                            println("Skip already executed ${nextRequest.id} ($treated/$totalItems | $percent%) ")

                            ""
                        }
                    }
                }

            }


    }

    protected open fun getLinkedRequest(
        previousRequest: RestaurantHttpRequest,
        previousResponse: String?,
        previousResponseHeaders: Array<Header>
    ): HttpRequestBase? {
        return null
    }

    private fun executeLinkedRequests(
        linkedRequest: HttpRequestBase,
        proxy: HttpHelper.ProxyParams?
    ): String? {
        println("Executing linked request ${linkedRequest.uri}")

        HttpHelper.createClient(if (useProxy) proxy else null)
            .use { closeableHttpClient ->
                closeableHttpClient.execute(linkedRequest)
                    .use { response ->

                        val response= EntityUtils.toString(response.entity)
                        if(!response.isNullOrBlank()) {
                            println("Got response from  linked request ${linkedRequest.uri}: ${response.length} bytes")
                            return response
                        }else{
                            return null
                        }
                    }
            }

    }

    abstract fun initRequestsQueue()

    private fun saveFailedRestaurantResponse(
        request: RestaurantHttpRequest,
        status: RestaurantStatus,
        responseBody: String?,
        extra: String
    ) {
        database.saveRestaurant(request.id, status, request.request.uri.toString(), responseBody, extra)

        if (status == RestaurantStatus.ERROR) // re-add for retry
            restaurantRequestQueue.add(request)

    }

    protected open fun saveSuccessRestaurantResponse(
        request: RestaurantHttpRequest,
        responseBody: String?,
        extra: String
    ) {
        database.saveRestaurant(request.id, RestaurantStatus.FOUND, request.request.uri.toString(), responseBody, extra)
    }


    @Synchronized
    private fun getNextRestaurantRequest(): RestaurantHttpRequest? {

        try {
            return restaurantRequestQueue.remove()
        } catch (e: Exception) {
            println("REMOVE EXCEPTION  $e")
        }
        return null;
    }

    abstract fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus


}