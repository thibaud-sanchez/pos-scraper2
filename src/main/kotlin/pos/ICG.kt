package pos


import model.Restaurant
import org.json.JSONObject
import pos.common.HttpGlypePos
import pos.common.RestaurantGlypeRequest

class ICG : HttpGlypePos("icg") {
    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantGlypeRequest>()
        for (id in 80000..150000) {
            val idName = id.toString().padStart(6, '0')
            val url = "http://cloudlicense.hiopos.com/eRestPortal/portalerest/portal/getRestaurant?idrest=$id&l=en"
            list.add(RestaurantGlypeRequest(idName, url))
        }
        restaurantRequestQueue.addAll(list.shuffled())

    }


    override fun determineResponseStatus(request: RestaurantGlypeRequest, responseBody: String?): RestaurantStatus {
        val json = JSONObject(responseBody)
        if (json.optString("city").isNullOrBlank()) {
            return RestaurantStatus.UNAVAILABLE

        } else {
            return RestaurantStatus.FOUND
        }
    }

    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val content = restaurantDb.data
        val json = JSONObject(content)
        val status =  json.optString("status") ?: ""
if(status=="0")
    return listOf()

        return listOf(json.toRestaurant(restaurantDb))
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this

        val id = json.optString("restaurantId")
        return Restaurant(
            name = json.optString("name"),
            id = id,
            datetime=restaurantDb.datetime,
            contactEmail = json.optString("orderEMail"),
            address1 = json.optString("address"),
            town = json.optString("city"),
            latitude = json.optString("geoLatitud") ?: "",
            longitude = json.optString("geoLongitud") ?: "",
            urlSource= restaurantDb.source,
            url = "https://www.portalrest.com/index.html?idRest=$id&m=2&ask=0",
            pos = "ICG",
            extra1 =  json.optString("status") ?: "",
            extra2 =  json.optString("eRestServer") ?: "",
        )
    }

}