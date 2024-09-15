package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.AhrefsResolver
import resolver.GoogleSearchResolver
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver

class ClusterPos : HttpPos("clusterpos") {

    private val ahrefsDomains = listOf("clusterpos.com")
    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "id",
            """https?:\/\/(?<id>(?!www|help|cloud|cloud-dev|developers)[a-z\-0-9]*\.clusterpos\.(com))""".toRegex()
        )
    )


    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)

    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains, false, restaurantIdResolver)
    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)


    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi(false, 5000)
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("clusterpos.com",false)
    }

    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            when (it.type) {
                "id" -> restaurantRequestQueue.add(generateRequest(it.id))
            }
        }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    private fun generateRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://onlineordering-backend.clusterpos.com/api/v1/franchises")
        request.addHeader("Origin", "https://$id")
        return RestaurantHttpRequest(id, request, "id")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        if (responseBody!!.contains("UnhandledError")) return RestaurantStatus.UNAVAILABLE
        JSONArray(responseBody)
        return RestaurantStatus.FOUND
    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val restaurantList = mutableListOf<Restaurant>()

        val content = restaurantDb.data

        val jsonArray = JSONArray(content)
        if (jsonArray == null) {
            println("UNABLE TO FIND VENUES")
            return restaurantList
        }
        for (i in 0 until jsonArray.length()) {
            val restaurantJson=jsonArray.getJSONObject(i)
            restaurantList.add(restaurantJson.toRestaurant(restaurantDb.source,restaurantDb.datetime))
        }
        return restaurantList
    }


    private fun JSONObject.toRestaurant(sourceUrl: String, datetime: String): Restaurant {
        val json = this
        return Restaurant(
            name = json.optString("name"),
            id = json.getInt("id").toString(),
            datetime = datetime,
            contactPhone = json.optString("phoneNumber") ?: "",
            address1 = json.optString("address") ?: "",
            town = json.optString("city") ?: "",
            postcode = json.optString("postalCode") ?: "",
            latitude = json.optString("latitude"),
            longitude = json.optString("longitude"),
            url = json.optString("website") ?: "",
            urlSource = sourceUrl,
            pos = "ClusterPos"
        )

    }
}