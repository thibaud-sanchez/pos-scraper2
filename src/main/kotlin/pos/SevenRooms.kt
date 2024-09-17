package pos

import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import pos.RawRestaurant.id
import pos.RawRestaurant.source
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import pos.common.ScraperUtils
import resolver.*

class SevenRooms : HttpPos("sevenrooms") {
    private val ahrefsDomains=listOf("www.sevenrooms.com")

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex("order", """www\.sevenrooms\.com\/reservations\/(?<id>[a-zA-Z0-9\-]*)""".toRegex()),
        RestaurantIdRegex("eu", """eu\.sevenrooms\.com\/reservations\/(?<id>[a-zA-Z0-9\-]*)""".toRegex())
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
      //  resolverGoogle.parseGoogleResult("site:sevenrooms.com",false)
        resolverGoogle.parseGoogleResult("site:eu.sevenrooms.com",false)
    }

    override fun convertRawDataToRestaurant(rawData: RestaurantDb): List<Restaurant> {
        val restaurantList = mutableListOf<Restaurant>()
        val content = rawData.data

        val json = JSONObject(content)
        var restaurantJson = json.getJSONObject("data").getJSONObject("venue_info")

        restaurantList.add(restaurantJson.toRestaurant(rawData))

        return restaurantList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this
        return Restaurant(
            name = json.optString("name"),
            id = json.optString("id"),
            urlSource = restaurantDb.source,
            datetime=restaurantDb.datetime,
            timezone=json.optString("timezone"),
            contactEmail = json.optString("email" ),
            contactPhone = json.optString("phone_number" ),
            address1 = json.optString("address"),
            address2 = json.optString("cross_street"),
            postcode = json.optString("postal_code"),
            town = json.optString("city"),
            state = json.optString("state"),
            latitude = json.optString("latitude"),
            longitude = json.optString("longitude"),
             url = json.optString("website"),
            country = json.optString("country"),
            facebook =  json.optString("facebook_link"),
            extra1 = json.optString("url_key"),
            maps= json.optString("gmaps_link"),
            pos = "SevenRooms"
        )

    }


    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            when (it.type) {

                "order" -> restaurantRequestQueue.add(generateRequest(it.source,"www",it.id))
                "eu" -> restaurantRequestQueue.add(generateRequest(it.source,"eu", it.id))
            }
        }
        println("Total id to extract = ${restaurantRequestQueue.size}")
    }


    private fun generateRequest(source:String, prefix:String, id: String): RestaurantHttpRequest {
        val finalPrefix = if(source.contains("eu.")) "eu" else prefix
        val request = HttpGet("https://$finalPrefix.sevenrooms.com/api-yoa/dining/venue_info?venue_url_key=$id")
        return RestaurantHttpRequest(id, request, "id")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        val json = JSONObject(responseBody)
        val errno = json.optInt("status", -1)
        if (errno == 200) {
            return RestaurantStatus.FOUND
        } else {
            return RestaurantStatus.UNAVAILABLE
        }
    }

}