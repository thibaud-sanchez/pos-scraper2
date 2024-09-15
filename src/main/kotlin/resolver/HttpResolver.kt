package resolver

import org.apache.http.util.EntityUtils
import pos.common.BasePos
import pos.common.HttpHelper
import pos.common.HttpProxyHelper
import pos.common.RestaurantHttpRequest
import java.util.*
import kotlin.random.Random

abstract class HttpResolver(posName: String, val sleepFromSec: Int = 20, val sleepToSec: Int = 50) : BasePos(posName) {
    var proxyHelper = HttpProxyHelper()
    protected var NB_PARRALLEL_THREADS = proxyHelper.proxies.size

    protected var USE_PROXY = true

    var restaurantRequestQueue: Queue<RestaurantHttpRequest> = LinkedList<RestaurantHttpRequest>()
    override fun start() {

        initRequestsQueue()
        val totalItems = restaurantRequestQueue.size

        val threads = List(NB_PARRALLEL_THREADS) { i -> i + 1 }
        proxyHelper.proxies.subList(0, NB_PARRALLEL_THREADS)
            .parallelStream()
            .forEach { proxy ->
                while (restaurantRequestQueue.size > 0) {
                    val nextRequest = getNextRestaurantRequest()
                    if (nextRequest != null) {
                        val treated = totalItems - restaurantRequestQueue.size
                        val percent = treated * 100 / totalItems
                        var extra = ""
                        if (database.idsMustBeResolved(nextRequest.id, nextRequest.type)) {
                            println("Handling id ${nextRequest.id} with  proxy ${proxy.host} ($treated/$totalItems | $percent%) (waiting between $sleepFromSec and $sleepToSec sec)")

                            Thread.sleep(Random.nextLong(sleepFromSec * 1000L, sleepToSec * 1000L))
                            try {
                                HttpHelper.createClient(proxy)
                                    .use { closeableHttpClient ->
                                        closeableHttpClient.execute(nextRequest.request)
                                            .use { response ->
                                                val response = EntityUtils.toString(response.entity)

                                                println("!! Resolver with id #${nextRequest.id} is available !!")
                                                saveSuccessResolverResponse(nextRequest, response, extra)
                                            }
                                    }

                            } catch (e: Exception) {
                                println("Fail to fetch request #${nextRequest.id} : ${e.javaClass.simpleName}")
                            }
                        } else {
                            println("Skip already executed ${nextRequest.id} ($treated/$totalItems | $percent%) ")
                            ""
                        }
                    }
                }

            }


    }

    abstract fun initRequestsQueue()


    protected abstract fun saveSuccessResolverResponse(
        request: RestaurantHttpRequest,
        responseBody: String?,
        extra: String
    )


    @Synchronized
    private fun getNextRestaurantRequest(): RestaurantHttpRequest? {

        try {
            return restaurantRequestQueue.remove()
        } catch (e: Exception) {
            println("REMOVE EXCEPTION  $e")
        }
        return null;
    }

}