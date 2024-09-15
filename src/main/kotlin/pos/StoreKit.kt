package pos

import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.*

class StoreKit : HttpPos("storekit") {
    private val ahrefsDomains=listOf("order.storekit.com")

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex("order", """order\.storekit\.com\/(?<id>.*)(\/)""".toRegex())
    )

    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)

    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains,false,restaurantIdResolver)

    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)

    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi()
    }
    fun resolveAllIds() {
        restaurantIdResolver.resolveAllIdsFromPath(format = ResolverInputFormat.BASIC_TEXT, false);
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("site:order.storekit.com",false)
    }

    override fun convertRawDataToRestaurant(rawData: RestaurantDb): List<Restaurant> {
        return getRestaurantFromJson(JSONObject(rawData.data), rawData.datetime)
    }

    private fun JSONObject.toRestaurant(datetime: String): Restaurant {
        val json = this
        val location = json.optJSONObject("loc")

        return Restaurant(
            name = json.optString("tr_name"),
            id = json.optString("id"),
            datetime = datetime,
            contactPhone = json.optString("phone"),
            address1 = json.optString("address")?.replace("\r\n", " ") ?: "",
            timezone = json.optString("timezone"),
            latitude = location?.optString("lat") ?: "",
            longitude = location?.optString("lng") ?: "",
            country = json.optString("country_code"),
            pos = "Zelty"
        )
    }


    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            when (it.type) {

                "order" -> restaurantRequestQueue.add(generateZeltyOrderRequest(it.id))
                "booking" -> restaurantRequestQueue.add(generateZeltyBookingRequest(it.id))
            }
        }
        println("Total id to extract = ${restaurantRequestQueue.size}")
    }


    private fun generateZeltyOrderRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://bo.zelty.fr/apis/order/1.0/getdb?zkey=$id")
        return RestaurantHttpRequest(id, request, "id")
    }

    private fun generateZeltyBookingRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://bo.zelty.fr/apis/booking/1.0/getdb")
        request.addHeader("Authorization", "Basic $id")
        return RestaurantHttpRequest(id, request, "id")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        val json = JSONObject(responseBody)
        val errno = json.optInt("errno", -1)
        if (errno == 0) {
            return RestaurantStatus.FOUND
        } else {
            return RestaurantStatus.UNAVAILABLE
        }
    }


    fun getRestaurantFromJson(json: JSONObject, datetime: String): List<Restaurant> {

        val restaurantsList = mutableListOf<Restaurant>()
        val urlSource = json.optString("source_url")
        val url = json.optString("json_url")
        var array: JSONArray? = json.optJSONArray("restaurants")
        if (array == null && json.has("restaurant")) {
            array = JSONArray().put(json.getJSONObject("restaurant"))
        } else if( array==null) {
            println("Unable to find restaurant for current json $url")
            return emptyList()
        }


        val count = array!!.length()
        for (i in 0 until count) {
            try {
                val json = array.getJSONObject(i)
                json.put("source_url", urlSource)
                json.put("json_url", url)
                val restaurant = json.toRestaurant(datetime)
                restaurantsList.add(restaurant)
            } catch (e: Exception) {
                println("Invalid restaurant :  ${json.toString()}")
            }
        }
        return restaurantsList
    }
}