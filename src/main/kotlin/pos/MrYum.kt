package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import pos.common.ScraperUtils
import resolver.ResolverInputFormat
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver

class MrYum : HttpPos("mryum") {

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "slug",
            """https?:\/\/(www\.)?mryum\.com\/(?<id>[a-zA-Z\-0-9]*)""".toRegex()
        ),
    )

    val resolver = RestaurantIdResolver(basePath,database, regex)

    fun resolveAllIds() {
        resolver.resolveAllIdsFromPath(format =ResolverInputFormat.BASIC_TEXT, resolveIntoWebsiteContent = false);
    }

    private fun generateMrYumRequest(slug: String): RestaurantHttpRequest {
        val graphQLQuery = """{
                "query": "query venueLanding(${'$'}venueSlug: String!) {\n  guestVenue(slug: ${'$'}venueSlug) {\n    ...VenueLanding\n    __typename\n  }\n\n}\n\nfragment VenueLanding on GuestVenue {\n  id\n  countryCode\n  name\n  slug\n  countryCode\n  timezone\n  location {\n    id: googlePlaceId\n    formattedAddress\n    latitude\n    longitude\n    state\n    postalCode\n    locality\n    __typename\n  }\n \n}\n\n",
                "variables": "{\"venueSlug\":\"$slug\"}"
            }"""


        val request = HttpPost("https://www.mryum.com/guest-gateway/eu1/graphql")
        request.entity = StringEntity(
            graphQLQuery,
            ContentType.APPLICATION_JSON
        );
        return RestaurantHttpRequest(slug, request, "slug")
    }


    private var totalcurrent = 0
    override fun initRequestsQueue() {
        val restaurantIdentifiers = resolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            when (it.type) {
                "slug" -> {
                    restaurantRequestQueue.add(generateMrYumRequest(it.id))

                }
            }
        }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        val json = JSONObject(responseBody)
        return if (json.has("data") && json.getJSONObject("data").has("guestVenue")) {
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
        var restaurantJson = json.getJSONObject("data").getJSONObject("guestVenue")

        restaurantList.add(restaurantJson.toRestaurant(restaurantDb))

        return restaurantList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this
        val address = json.optJSONObject("location")

        val slug = json.optString("slug")
        val url = "https://www.mryum.com/$slug"
        return Restaurant(
            name = json.optString("name"),
            id = json.optString("id"),
            url = url,
            datetime=restaurantDb.datetime,
            urlSource = restaurantDb.id,
            timezone=json.optString("timezone"),
            address1 = address.optString("formattedAddress"),
            postcode = address.optString("postalCode"),
            state = address.optString("state"),
            latitude = address.optString("latitude"),
            longitude = address.optString("longitude"),
            country = json.optString("countryCode"),
            maps= ScraperUtils.getGoogleMapsUrlByPlaceId(address.optString("id")),
            pos = "MrYum"
        )

    }
}