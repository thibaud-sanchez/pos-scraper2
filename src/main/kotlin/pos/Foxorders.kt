package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest

class Foxorders : HttpPos("foxorders", nbThreads = 13) {


    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantHttpRequest>()
        for (page in 1..1) {
            list.add(generateFoxordersRequest(page))
        }
        restaurantRequestQueue.addAll(list.shuffled())

    }

    private fun generateFoxordersRequest(page: Int): RestaurantHttpRequest {

        val request =
            HttpGet("https://www.foxorders.com/api/foxeat/restaurants?page=$page&app=com.foxorders.foxeat&limit=50")

        return RestaurantHttpRequest(page.toString(), request, "page")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        val json = JSONArray(responseBody)
        return if (json.length() == 0) {
            RestaurantStatus.UNAVAILABLE
        } else if (json.length() > 0) {
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.ERROR
        }

    }

    override fun saveSuccessRestaurantResponse(request: RestaurantHttpRequest, responseBody: String?, extra: String) {
        val jsonArray = JSONArray(responseBody)
        for (i in 0 until jsonArray.length()) {
            val restaurant = jsonArray.getJSONObject(i)
            database.saveRestaurant(
                restaurant.getInt("restaurant_id").toString(),
                RestaurantStatus.FOUND,
                request.request.uri.toString(),
                restaurant.toString()
            )

        }
    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val content = restaurantDb.data

        val json = JSONObject(content)
        return listOf(json.toRestaurant(restaurantDb))
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this
        val address = json.getJSONObject("address")


        return Restaurant(
            name = json.optString("restaurant_name"),
            id = json.optString("restaurant_id"),
            datetime = restaurantDb.datetime,
            contactPhone = address.optString("phone"),
            address1 = address.optString("street"),
            town = address.optString("city"),
            postcode = address.optString("zipcode"),
            url = json.optString("url"),
            contactEmail = json.optString("email"),
            latitude = json.optString("latitude") ?: "",
            longitude = json.optString("longitude") ?: "",
            urlSource = restaurantDb.source,
            pos = "Foxorders"
        )
    }


}