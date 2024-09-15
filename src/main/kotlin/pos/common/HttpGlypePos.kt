package pos.common

import pos.RestaurantStatus
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import kotlin.random.Random

abstract class HttpGlypePos(posName: String) : BasePos(posName) {
    protected var NB_PARRALLEL_THREADS = 5
    protected var USE_PROXY = true

    //Liste glype : http://free-proxy.cz/fr/web-proxylist/
    val proxies = listOf<String>(
        "http://vh12559.hv4.ru/go",
        "http://vkrosse2.ru/proxy",
        "http://maximi89.cl/red",
        "http://formobile.com.au/proxy",
        "http://www.rcdzapata.com/glype",
        "http://wms.kylos.pl/p",
        "https://proxy.knyazvs.ru",
        "https://proxyko.com",
        "https://proxy.schel.li",
        "https://www.sudoproxy.net/",
//        "https://tonyvoyce.com/",
       // "https://sitenable.org/",
        "https://lujw.azurewebsites.net/",
        "https://dnytest.azurewebsites.net/"

    )

    // val proxies = mutableListOf<String>()
    var restaurantRequestQueue: Queue<RestaurantGlypeRequest> = LinkedList<RestaurantGlypeRequest>()
    override fun start() {
        var securedProxies = proxies.filter { it.startsWith("https") }

        initRequestsQueue()
        val totalItems = restaurantRequestQueue.size

        val client = HttpClient.newBuilder().build()
        var total = 0
        val threads = List(NB_PARRALLEL_THREADS) { i -> i + 1 }
        threads.parallelStream().forEach {
            while (restaurantRequestQueue.size > 0) {
                val nextRequest = getNextRestaurantRequest()
                if (nextRequest != null) {
                    val treated = totalItems - restaurantRequestQueue.size
                    val percent = treated * 100 / totalItems
                    println("Handling id ${nextRequest.id} ($treated/$totalItems | $percent%)")
                    var extra = ""
                    if (database.restaurantMustBeCreatedOrUpdated(nextRequest.id)) {
                        Thread.sleep(Random.nextLong(500, 5000))
                        val request = if (USE_PROXY) {
                            val urlEncode = URLEncoder.encode(nextRequest.url, "utf-8")

                            val proxy = if (urlEncode.startsWith("https")) {
                                securedProxies.random()
                            } else {
                                proxies.random()
                            }
                            val url = "$proxy/browse.php?u=$urlEncode&b=224&f=norefer"
                            println("Executing ${nextRequest.url} with proxy $proxy")

                            extra += "proxy:$proxy"

                            HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("Referer", proxy)
                                .build()
                        } else {
                            println("Executing ${nextRequest.url}")

                            HttpRequest.newBuilder()
                                .uri(URI.create(nextRequest.url))
                                .build()
                        }
                        var res = ""
                        try {
                            val response = client.send(request, HttpResponse.BodyHandlers.ofString());
                            res = response.body()

                            var status = determineResponseStatus(nextRequest, res)
                            if (status == RestaurantStatus.FOUND) {
                                println("!! Restaurant with id #${nextRequest.id} is available !!")
                                saveSuccessRestaurantResponse(nextRequest, res, extra)
                            } else {
                                println("Restaurant with id #${nextRequest.id} seems not available")
                                saveFailedRestaurantResponse(nextRequest, status, res, extra)
                            }

                        } catch (e: Exception) {
                            println("Unable to create json for #${nextRequest.id} : ${e.javaClass.simpleName}")
                            var content = e.javaClass.simpleName + " : " + e.localizedMessage + "\r\n" + res
                            database.saveRestaurant(
                                nextRequest.id,
                                RestaurantStatus.ERROR,
                                nextRequest.url,
                                content,
                                extra
                            )


                        }
                    }
                }
            }

        }


    }

    abstract fun initRequestsQueue()

    private fun saveFailedRestaurantResponse(
        request: RestaurantGlypeRequest,
        status: RestaurantStatus,
        responseBody: String?,
        extra: String
    ) {
        database.saveRestaurant(request.id, status, request.url, responseBody, extra)

        if (status == RestaurantStatus.ERROR) // re-add for retry
            restaurantRequestQueue.add(request)

    }

    protected open fun saveSuccessRestaurantResponse(
        request: RestaurantGlypeRequest,
        responseBody: String?,
        extra: String
    ) {
        database.saveRestaurant(request.id, RestaurantStatus.FOUND, request.url, responseBody, extra)
    }


    @Synchronized
    private fun getNextRestaurantRequest(): RestaurantGlypeRequest? {

        try {
            return restaurantRequestQueue.remove()
        } catch (e: Exception) {
            println("REMOVE EXCEPTION  $e")
        }
        return null;
    }

    abstract fun determineResponseStatus(request: RestaurantGlypeRequest, responseBody: String?): RestaurantStatus


}