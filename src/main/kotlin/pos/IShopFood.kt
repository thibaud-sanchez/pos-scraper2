package pos


import com.google.common.base.Strings
import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest


class IShopFood : HttpPos("ishopfood") {

    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantHttpRequest>()
        for (id in 200..300) {
            list.add(generateRequest(id.toString()))
        }
        restaurantRequestQueue.addAll(list.shuffled())
    }

    private fun generateRequest(id: String): RestaurantHttpRequest {
        val request =
            HttpGet("https://api.ishopfood.com/api/branches/$id/companies")
        return RestaurantHttpRequest(id, request, "id")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {

        if (httpCode == 404) return RestaurantStatus.UNAVAILABLE
        JSONArray(responseBody)
        return RestaurantStatus.FOUND

    }

    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val restaurantList = mutableListOf<Restaurant>()

        val content = restaurantDb.data

        var jsonArray = JSONArray(content)
//        if (jsonArray == null) {
//            println("UNABLE TO FIND STORES: $content")
//            return restaurantList
//        }
        for (i in 0 until jsonArray.length()) {
            val restaurantJson = jsonArray.getJSONObject(i)
            var restaurant = restaurantJson.toRestaurant(restaurantDb)
            if (restaurant != null)
                restaurantList.add(restaurant)
        }
        return restaurantList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant? {
        val json = this
        val address: JSONObject? = json.optJSONObject("address")
        val social: JSONObject? = json.optJSONObject("information")?.optJSONObject("social")
        if (Strings.isNullOrEmpty(address?.optString("address"))
            && Strings.isNullOrEmpty(address?.optString("city"))
            && Strings.isNullOrEmpty(address?.optString("zipCode"))
        ) return null;
        else
            return Restaurant(
                name = json.optString("name"),
                id = json.optString("id"),
                datetime = restaurantDb.datetime,
                groupId = json.optString("branch"),
                contactPhone = json.optString("phoneNumber"),
                contactEmail = json.optString("email"),
                address1 = address?.optString("address") ?: "",
                postcode = address?.optString("zipCode") ?: "",
                town = address?.optString("city") ?: "",
                country = address?.optString("country") ?: "",
                state = address?.optString("state") ?: "",
                extra1 = json.optString("nameCanonical"),
                timezone = json.optString("timezone"),
                latitude = address?.optString("latitude") ?: "",
                longitude = address?.optString("longitude") ?: "",
                facebook = social?.optString("facebook") ?: "",
                url = social?.optString("website") ?: "",
                urlSource = restaurantDb.source,
                pos = "iShopFood"
            )


    }
}