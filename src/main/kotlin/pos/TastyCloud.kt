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

class TastyCloud : HttpPos("tastycloud") {
    private val ahrefsDomains = listOf("clicks.tastycloud.fr")

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex("token", """https?:\/\/clicks\.tastycloud\.fr\/(?<id>[a-f0-9]*)""".toRegex()),
    )

    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)

    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains, false, restaurantIdResolver)

    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)

    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi()
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("site:clicks.tastycloud.fr", false)
    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {

        val restaurantList = mutableListOf<Restaurant>()
        val content = restaurantDb.data

        val json = JSONObject(content)
        var restaurantJson = json.getJSONObject("restaurant")

        restaurantList.add(restaurantJson.toRestaurant(restaurantDb))

        return restaurantList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this
        val social = json.optJSONObject("social_network")

        return Restaurant(
            name = json.optString("restaurant_name"),
            id = json.optString("id"),
            datetime = restaurantDb.datetime,
            contactPhone = json.optString("phone"),
            address1 = json.optString("address_street", ""),
            town = json.optString("address_city", ""),
            postcode = json.optString("address_zip_code", ""),
            contactEmail = json.optString("contact_email"),
            extra1 = json.optString("typology"),
            extra2 = json.optString("delivery_provider"),
            facebook = social.optString("facebook"),
            url = "https://clicks.tastycloud.fr/${restaurantDb.id}",
            urlSource = "https://caching.tastycloud.fr/api_click_and_collect/v7/restaurant/info?restaurant_token=${restaurantDb.id}",
            pos = "TastyCloud"
        )
    }


    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            when (it.type) {
                "token" -> restaurantRequestQueue.add(generateTokenRequest(it.id))
            }
        }
        println("Total id to extract = ${restaurantRequestQueue.size}")
    }


    private fun generateTokenRequest(token: String): RestaurantHttpRequest {
        val request =
            HttpGet("https://caching.tastycloud.fr/api_click_and_collect/v7/restaurant/info?restaurant_token=$token")
        return RestaurantHttpRequest(token, request, "token")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        val json = JSONObject(responseBody)
        if (json.has("restaurant")) {
            return RestaurantStatus.FOUND
        } else {
            return RestaurantStatus.UNAVAILABLE
        }
    }
}