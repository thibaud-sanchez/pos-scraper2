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
import java.net.URLEncoder

class Agora : HttpPos("agora") {

    private val ahrefsDomains=listOf("smartmenu.agorapos.com","bookings.agorapos.com")
    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "smartmenu",
            """https?:\/\/smartmenu\.agorapos\.com\/\?id=(?<id>[a-z\-0-9]*)""".toRegex()
        ),
        RestaurantIdRegex(
            "bookings",
            """https?:\/\/bookings\.agorapos\.com\/(?<id>[a-z\-0-9_\/]*)""".toRegex()
        ),
    )


    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)

    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains,false,restaurantIdResolver)
    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)


    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi()
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("smartmenu.agorapos.com")
    }

    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            when (it.type) {
                "smartmenu" -> restaurantRequestQueue.add(generateSmartmenuRequest(it.id))
                "bookings" -> restaurantRequestQueue.add(generateBookingRequest(it.id))
            }
        }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    private fun generateBookingRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://bookings.agorapos.com/$id")
        return RestaurantHttpRequest(id, request, "bookings")
    }

    private fun generateSmartmenuRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://smartmenu.agorapos.com/api/digital-menu/$id")
        return RestaurantHttpRequest(id, request, "smartmenu")
    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        return if (request.type == "smartmenu") {
            if (responseBody!!.contains("404")) return RestaurantStatus.UNAVAILABLE
            val json = JSONObject(responseBody)
            if (json.has("id")) {
                RestaurantStatus.FOUND
            } else {
                RestaurantStatus.UNAVAILABLE
            }
        } else {
            if (!responseBody.isNullOrBlank()) {
                RestaurantStatus.FOUND
            } else {
                RestaurantStatus.ERROR
            }

        }

    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        if (restaurantDb.source.contains("smartmenu")) {
            val content = restaurantDb.data

            val json = JSONObject(content)

            return listOf(json.toRestaurant(restaurantDb.source, restaurantDb.datetime))
        } else {
            return getRestaurantsFromHtml(restaurantDb, restaurantDb.data)
        }
    }

    val regexRestaurantName = """<h3 style="margin-bottom: 0px;">(?<name>[\w\s\-\(\)]*)""".toRegex()
    val regexRestaurantAddress =
        """<h4 style="margin-top: 0px;">(?<address>[\w\s\-\(\)]*)""".toRegex()

    fun getRestaurantsFromHtml(restaurantDb: RestaurantDb, content: String): List<Restaurant> {

        val restaurantsList = mutableListOf<Restaurant>()
        val restaurantName = regexRestaurantName.find(content)?.groups?.get("name")?.value ?: ""
        val restaurantAddress = regexRestaurantAddress.find(content)?.groups?.get("address")?.value ?: ""
        if (restaurantName.isNullOrBlank() && restaurantAddress.isNullOrBlank()) {
            println("Unable to find restaurant info for ${restaurantDb.id}")
            return emptyList()
        }
        val searchUrl =
            "https://www.google.com/search?q=" + URLEncoder.encode("$restaurantName,$restaurantAddress")
        restaurantsList.add(
            Restaurant(
                id = restaurantDb.id,
                datetime = restaurantDb.datetime,
                name = restaurantName,
                address1 = restaurantAddress,
                urlSource = restaurantDb.source,
                url = searchUrl,

                pos = "Agora"
            )
        )
        return restaurantsList
    }

    private fun JSONObject.toRestaurant(sourceUrl: String, datetime: String): Restaurant {
        val json = this
        val company = json.optJSONObject("sender")?.optJSONObject("company")
        val address = company?.optJSONObject("address")
        return Restaurant(
            name = json.optString("name"),
            id = json.getString("id"),
            datetime = datetime,
            contactPhone = company?.optString("phone") ?: "",
            address1 = address?.optString("street") ?: "",
            town = address?.optString("city") ?: "",
            postcode = address?.optString("zipCode") ?: "",
            state = address?.optString("region") ?: "",
            contactEmail = company?.optString("email") ?: "",
            urlSource = sourceUrl,
            timezone = json.optString("timezone"),
            pos = "Agora"
        )

    }
}