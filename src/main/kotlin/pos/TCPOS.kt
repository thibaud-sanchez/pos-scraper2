package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import pos.common.ScraperUtils

class TCPOS : HttpPos("tcpos", 5, 20) {


    private fun generateTrivecRequest(id: Int): RestaurantHttpRequest {

        val request = HttpGet("https://mytrivec.com/srv/connect/api/client/$id/default")
        val idName = id.toString().padStart(5, '0')

        return RestaurantHttpRequest(idName, request, "id")
    }


    private var totalcurrent = 0
    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantHttpRequest>()
        for (id in 0..11000) {
//        for (id in 6105..6107) {
            list.add(generateTrivecRequest(id))
        }
        restaurantRequestQueue.addAll(list.shuffled())
        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    private fun <A, B> lazyCartesianProduct(
        listA: Iterable<A>,
        listB: Iterable<B>
    ): Sequence<Pair<A, B>> =
        sequence {
            listA.forEach { a ->
                listB.forEach { b ->
                    yield(a to b)
                }
            }
        }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        if (responseBody != null && responseBody.contains("No business information")) {
            return RestaurantStatus.UNAVAILABLE
        }
        val json = JSONObject(responseBody)
        return if (json.has("companyInfo")) {
            totalcurrent++
            println("1 new restaurant ! // Total : $totalcurrent")
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }

    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {

        val restaurantList = mutableListOf<Restaurant>()
        val content = restaurantDb.data
        val json = JSONObject(content)
        restaurantList.add(json.toRestaurant(restaurantDb))

        return restaurantList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this
        val company = json.optJSONObject("companyInfo")
        val address = company.optJSONObject("address")

        var address1 = address.optString("houseNumber") + ", " + address.optString("street")
        var lat = address.optString("latitude")
        var long = address.optString("longitude")

        var socialMedia = company.optJSONObject("socialMedia")
        val url = "https://pay.mytrivec.com/restaurant/${restaurantDb.id}"
        return Restaurant(
            name = json.optString("name"),
            id = restaurantDb.id,
            urlSource = url,
            datetime = restaurantDb.datetime,
            contactPhone = address.optString("phone"),
            contactPhone2 = address.optString("gsm"),
            contactEmail = address.optString("email"),
            address1 = address1,
            postcode = address.optString("zipCode"),
            town = address.optString("city"),
            latitude = lat,
            longitude = long,
            country = address.optString("country"),
            maps = ScraperUtils.getGoogleMapsUrlByLatitudeLongitude(lat, long),
            url = socialMedia.optString("website"),
            facebook = socialMedia.optString("facebook"),

            pos = "Trivec"
        )

    }
}