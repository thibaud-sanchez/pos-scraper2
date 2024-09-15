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

class LightspeedL : HttpPos("lightspeed-l") {

    private val ahrefsDomains=listOf("lightspeedordering.com")

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "slug",
            """https?:\/\/(?<id>(?!www|help|developers)[a-z\-0-9]*)\.lightspeedordering\.(com)""".toRegex()
        )
    )

    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)
    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains,false,restaurantIdResolver)

    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)


    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi()
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("""site:"lightspeedordering.com"""")
    }

    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            when (it.type) {
                "slug" -> restaurantRequestQueue.add(generateRequest(it.id))
            }
        }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    private fun generateRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://order-service.lightspeedordering.com/v1/merchants/$id/locations")
        return RestaurantHttpRequest(id, request, "slug")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {

        val json = JSONObject(responseBody)
        return if (json.has("results")) {
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }


    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        return JSONObject(restaurantDb.data).toRestaurants(restaurantDb)

    }

    private fun JSONObject.toRestaurants(restaurantDb: RestaurantDb): List<Restaurant> {
        val restaurantList = mutableListOf<Restaurant>()


        val json = this.getJSONObject("results")
        var name = json.optString("name")
        var groupId = json.optString("id")
        var jsonArray = json.optJSONArray("locations")
        if (jsonArray != null) {
            for (i in 0 until jsonArray.length()) {
                var restaurantJson = jsonArray.getJSONObject(i)
                var restaurantAddress = restaurantJson.getJSONObject("address")
                restaurantList.add(
                    Restaurant(
                        id = restaurantJson.optString("id"),
                        name = restaurantJson.optString("name"),
                        contactPhone = restaurantJson.optString("phone"),
                        datetime = restaurantDb.datetime,
                        groupId = groupId,
                        address1 = restaurantAddress.optString("fullAddress"),
                        latitude = restaurantAddress.optString("latitude"),
                        longitude = restaurantAddress.optString("longitude"),
                        urlSource = restaurantDb.source.replace("GET ","").replace(" HTTP/1.1",""),
                        timezone = restaurantJson.optString("timezone"),
                        pos = "LightspeedL"
                    )
                )
            }
        }
        return restaurantList

    }
}