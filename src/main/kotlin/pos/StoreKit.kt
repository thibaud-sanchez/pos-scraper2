package pos

import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import pos.common.ScraperUtils
import resolver.*

class StoreKit : HttpPos("storekit") {
    private val ahrefsDomains=listOf("order.storekit.com")

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex("order", """order\.storekit\.com\/(?<id>[a-zA-Z0-9\-]*)""".toRegex())
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
        val restaurantList = mutableListOf<Restaurant>()
        val content = rawData.data

        val json = JSONObject(content)
        var restaurantJson = json.optJSONObject("venue") ?:  json.getJSONObject("brand").optJSONArray("venues").getJSONObject(0)

        restaurantList.add(restaurantJson.toRestaurant(rawData))

        return restaurantList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this
        val address = json.optJSONObject("address")

        return Restaurant(
            name = json.optString("name"),
            id = json.optString("id"),
            urlSource = restaurantDb.source,
            datetime=restaurantDb.datetime,
            timezone=json.optString("timezone"),
            contactEmail = json.optString("email" ),
            contactPhone = json.optString("phoneNumber" ),
            address1 =  address.optString("buildingNumber")+ " "+ address.optString("street"),
            town = address.optString("city"),
            postcode = address.optString("postCode"),
            state = address.optString("state"),
            latitude = address.optString("lat"),
            longitude = address.optString("lng"),
            url = json.optString("url"),
            country = address.optString("country"),
            extra1 = json.optString("slug"),
            extra2 = json.optString("posProvider"),
            maps= json.optString("gmaps_link"),
            pos = "StoreKit"
        )

    }



    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach { restaurantRequestQueue.add(generateOrderRequest(it.id)) }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }


    private fun generateOrderRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://order.storekit.com/api/venues/$id")
        return RestaurantHttpRequest(id, request, "id")
    }




    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        val json = JSONObject(responseBody)
        if (json.optJSONObject("venue") !=null) {
            return RestaurantStatus.FOUND
        } else {
            return RestaurantStatus.UNAVAILABLE
        }
    }
}