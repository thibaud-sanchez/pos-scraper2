package pos

import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.GoogleSearchResolver
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver

class InnovOrder : HttpPos("innovorder") {

    //val basePath = File("data/innovorder")


    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "id",
            """window\.brandHash=\"(?<id>.[a-f0-9]*)\"""".toRegex()
        )
    )

    val regexResolver = RestaurantIdResolver(basePath, database, regex)
    var resolverGoogle = GoogleSearchResolver(basePath, database, regexResolver)


    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult(""""propulsé par innovorder"""")
    }
//
//    fun buildCSV() {
//
//        val writer = RestaurantWriter(File(basePath, "innovorder.csv"))
//        var count = 0
//        basePath.walk().filter { it.name.endsWith("txt") }.forEach {
//            val content = it.readText()
//            try {
//                val url = "http://${it.nameWithoutExtension}/home/places"
//                val json = JSONObject(content)
//                val restaurants = json.toRestaurants(url)
//                restaurants.forEach {
//                    writer.appendRestaurant(it)
//                    count++
//                }
//
//            } catch (e: Exception) {
//                println("Unable to read restaurant information for ${it.name} : $content")
//            }
//        }
//        writer.close()
//        println("Finish export : $count lines")
//    }

    override fun convertRawDataToRestaurant(rawData: RestaurantDb): List<Restaurant> {
        return JSONObject(rawData.data).toRestaurants(rawData.source, rawData.datetime)
    }


    private fun generateInnovOrderRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://api.innovorder.fr/brands/webordering-configuration/$id")
        return RestaurantHttpRequest(id, request, "id")

    }


    private var totalcurrent = 0
    override fun initRequestsQueue() {
        val restaurantIdentifiers = regexResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            when (it.type) {
                "id" -> {
                    restaurantRequestQueue.add(generateInnovOrderRequest(it.id))

                }
            }
        }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        val json = JSONObject(responseBody)
        return if (json.optJSONObject("data") !=null) {
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }
    }


    private fun JSONObject.toRestaurants(url: String, datetime: String): List<Restaurant> {
        val restaurants = mutableListOf<Restaurant>()
        val restaurantArray = this.getJSONObject("data").getJSONArray("restaurants")

        for (i in 0 until restaurantArray.length()) {

            val rest = restaurantArray.getJSONObject(i)


            val address = rest.getJSONObject("address")
            val address1 = address.optString("streetNumber") + " " + address.optString("route")
            restaurants.add(
                Restaurant(
                    name = rest.optString("name"),
                    id = rest.optString("restaurantId"),
                    datetime = datetime,
                    contactEmail = rest.optString("contactEmail"),
                    contactName = rest.optString("contactName"),
                    contactPhone = rest.optString("contactPhone"),
                    url = url,
                    address1 = address1,
                    town = address.optString("locality"),
                    postcode = address.optString("postalCode"),
                    googleId = address.optString("googlePlaceId"),
                    country = address.optString("country"),
                    latitude = address.optString("lat"),
                    longitude = address.optString("lng"),
                    pos = "Innovorder"
                )
            )
        }
        return restaurants


    }

}