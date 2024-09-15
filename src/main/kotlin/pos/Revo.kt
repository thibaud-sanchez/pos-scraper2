package pos

import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver
import java.net.URLEncoder

class Revo : HttpPos("revo") {

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex("base", """\"https?:\/\/.*revointouch\.works\/app/(?<id>.*?)/stores.*(\"|<)""".toRegex()),
        RestaurantIdRegex(
            "delivery",
            """\"https?:\/\/.*revointouch\.works\/app/delivery/login/(?<id>.*?)(=?asDigitalMenu=true)?(\"|<)""".toRegex()
        ),
        RestaurantIdRegex(
            "store",
            """\"https?:\/\/.*revointouch\.works\/app/store/login/(?<id>.*?)(=?asDigitalMenu=true)?(\"|<)""".toRegex()
        ),
        RestaurantIdRegex(
            "pickup",
            """\"https?:\/\/.*revointouch\.works\/app/pickup/login/(?<id>.*?)(=?asDigitalMenu=true)?(\"|<)""".toRegex()
        ),
        RestaurantIdRegex(
            "none",
            """\"https?:\/\/.*revointouch\.works\/app/login/(?<id>.*?)(=?asDigitalMenu=true)?(\"|<)""".toRegex()
        )
    )


    val resolver = RestaurantIdResolver(basePath, database, regex)


    fun resolveAllIds() {
        resolver.resolveAllIdsFromPath();
    }


    override fun convertRawDataToRestaurant(rawData: RestaurantDb): List<Restaurant> {
        return getRestaurantsFromHtml(rawData.id, rawData.data, rawData.datetime)
    }


    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantHttpRequest>()
        for (id in 380..400) {
            val idName = id.toString().padStart(6, '0')

            list.add(generateRevoRequest(id))
        }
        restaurantRequestQueue.addAll(list.shuffled())

    }

    private fun generateRevoRequest(id: Int): RestaurantHttpRequest {

        val request = HttpGet("https://solo.revointouch.works/app/$id/stores/")
        val idName = id.toString().padStart(5, '0')

        return RestaurantHttpRequest(idName, request, "id")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        if (responseBody != null && responseBody.isNotBlank()) {
            if (responseBody.contains("Unauthorized") || responseBody.contains("404 Not Found")) {
                return RestaurantStatus.UNAVAILABLE
            } else {
                return RestaurantStatus.FOUND

            }
        } else {
            return RestaurantStatus.ERROR

        }
    }

    val client = HttpClientBuilder.create()
        .setRedirectStrategy(LaxRedirectStrategy()).build()


    val regexRestaurantContainer =
        """<div class=\"flex flex-col flex-1 pl-4 md:pl-0 justify-start space-y-2 w-1/3\">(?<restaurant>[.|\r\n\s\S]*?)</div>""".toRegex()
    val regexRestaurantName =
        """<span class=\"break-normal font-bold uppercase text-base text-left .*?\">(?<name>.*)</span>""".toRegex()
    val regexRestaurantAddress =
        """<span class=\"break-normal text-xs text-left .*?\">(?<address>.*)</span>""".toRegex()

    fun getRestaurantsFromHtml(id: String, content: String, datetime: String): List<Restaurant> {

        val restaurantsList = mutableListOf<Restaurant>()
        val restaurantsContent = regexRestaurantContainer.findAll(content)
        restaurantsContent.forEach {
            val data = it.value
            val restaurantName = regexRestaurantName.find(data)?.groups?.get("name")?.value ?: ""
            val restaurantAddress = regexRestaurantAddress.find(data)?.groups?.get("address")?.value ?: ""

            val searchUrl = "https://www.google.com/search?q=" + URLEncoder.encode("$restaurantName,$restaurantAddress")
            restaurantsList.add(
                Restaurant(
                    id = id,
                    datetime = datetime,
                    name = restaurantName,
                    address1 = restaurantAddress, urlSource = "https://solo.revointouch.works/app/$id/stores/",
                    url = searchUrl,

                    pos = "Revo"
                )
            )

        }
        return restaurantsList
    }

}