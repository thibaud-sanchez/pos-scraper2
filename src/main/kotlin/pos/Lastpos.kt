package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.AhrefsResolver
import resolver.GoogleSearchResolver
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver

class Lastpos : HttpPos("lastpos") {

    private val ahrefsDomains=listOf("last.app","last.shop")

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "shop",
            """https?:\/\/(?<id>(?!www|help|developers)[a-z\-0-9]*\.last\.(shop|app))""".toRegex()
        )
    )

    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)
    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains,false,restaurantIdResolver)

    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)

    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi(false)
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("""site:"last.app"""")
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
        request.addHeader("Origin","https://$id.clusterpos.com")
        return RestaurantHttpRequest(id, request, "id")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {

        val json = JSONObject(responseBody)
        return if (json.has("organizationId")) {
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }


    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val restaurantList = mutableListOf<Restaurant>()

        val content = restaurantDb.data

        val json = JSONObject(content)
        var jsonArray = json.optJSONArray("locations")
        if (jsonArray == null) {
            val jsonGroup = json.getJSONObject("locationGroups")
            jsonArray = jsonGroup.getJSONObject(jsonGroup.keySet().first()).getJSONArray("locationBrands")
        }
        if (jsonArray == null) {
            println("UNABLE TO FIND LOCATIONS")
            return restaurantList
        }
        for (i in 0 until jsonArray.length()) {
            restaurantList.add(jsonArray.getJSONObject(i).toRestaurant(restaurantDb))
        }
        return restaurantList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this
        return Restaurant(
            name = json.optString("name"),
            id = json.optString("id"),
            datetime = restaurantDb.datetime,
            groupId = json.optString("brandName"),
            contactPhone = json.optString("phoneNumber"),
            address1 = json.optString("address"),
            latitude = json.optString("latitude"),
            longitude = json.optString("longitude"),
            contactEmail = json.optString("email"),
            postcode = json.optString("postalCode"),
            country = json.optString("countryCode"),
            urlSource = restaurantDb.source,
            timezone = json.optString("timezone"),
            pos = "Lastpos"
        )


    }
}