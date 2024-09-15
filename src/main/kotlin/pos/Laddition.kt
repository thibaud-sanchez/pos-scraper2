package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpRequestBase
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.AhrefsResolver
import resolver.GoogleSearchResolver
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver

class Laddition : HttpPos("laddition", useProxy = false) {

    private val ahrefsDomains = listOf("reservation.laddition.com", "commande-en-ligne.laddition.com")
    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "booking",
            """(https?:\/\/)?reservation\.laddition\.com\/booking\/(?<id>[a-zA-Z0-9_\-]*)""".toRegex()
        ),
        RestaurantIdRegex(
            "onlineorder",
            """(https?:\/\/)?commande-en-ligne\.laddition\.com\/commande-en-ligne\/(?<id>[a-zA-Z0-9_\-]*)""".toRegex()
        ),
    )


    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)

    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains, false, restaurantIdResolver)
    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)


    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi()
    }

    fun resolveGoogleids() {
        ahrefsDomains.forEach {
            resolverGoogle.parseGoogleResult("site:$it")

        }
    }

    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            when (it.type) {
                "booking" -> restaurantRequestQueue.add(generateBookingRequest(it.id))
                "onlineorder" -> restaurantRequestQueue.add(generateOnlineOrderRequest(it.id))
            }
        }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    private fun generateBookingRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://reservation.laddition.com/api/restaurants/get-id/$id")
        return RestaurantHttpRequest(id, request, "booking")
    }

    private fun generateOnlineOrderRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://commande-en-ligne.laddition.com/commande-en-ligne/$id")
        return RestaurantHttpRequest(id, request, "onlineorder")
    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        return if (request.type == "booking") {
            if (responseBody!!.contains("404")) return RestaurantStatus.UNAVAILABLE
            val json = JSONObject(responseBody)
            if (json.has("restaurantId")) {
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

    override fun getLinkedRequest(
        previousRequest: RestaurantHttpRequest,
        previousResponse: String?,
        previousResponseHeaders: Array<Header>
    ): HttpRequestBase? {
        return if (previousRequest.type == "booking") {
            val json = JSONObject(previousResponse)
            if (json.has("restaurantId")) {
                var req = HttpGet("https://reservation.laddition.com/api/restaurants/" + json.getString("restaurantId"))
                req.addHeader("authorization", "undefined")
                req
            } else null
        } else null
    }

    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        if (restaurantDb.source.contains("reservation")) {
            val content = restaurantDb.data

            val json = JSONObject(content)

            return listOf(json.toRestaurant(restaurantDb.source, restaurantDb.datetime))
        } else if (restaurantDb.source.contains("commande")) {
            val content = restaurantDb.data

            val regexJson = """<script id="__NEXT_DATA__" type="application\/json">(?<json>.*)<\/script>""".toRegex()
            val regexResult = regexJson.findAll(content)
            regexResult.forEach { result ->
                val res = result.groups["json"]?.value
                if (res != null && res.trim().isNotBlank()) {
                    val json = JSONObject(res)
                    return listOf(json.toOrderingRestaurant(restaurantDb.source, restaurantDb.datetime))
                }
            }
        }
        return listOf()
    }

    private fun JSONObject.toOrderingRestaurant(sourceUrl: String, datetime: String): Restaurant {
        val json = this.getJSONObject("props")
            .getJSONObject("initialState")
            .getJSONObject("restaurant")
            .getJSONObject("infoRestaurant")
        val address = json?.optJSONObject("address")
        return Restaurant(
            name = json.optString("name"),
            id = json.getString("id"),
            contactEmail = json.optString("email_contact") ?: json.optString("email_laddition") ?: "",
            datetime = datetime,
            contactPhone = json.optString("phone") ?: "",
            address1 = address?.optString("street_number") + " " + address?.optString("street"),
            address2 = address?.optString("additional_address") ?: "",
            town = address?.optString("city") ?: "",
            postcode = address?.optString("zip_code") ?: "",
            country = address?.optString("country") ?: "",
            latitude = address?.optString("lat") ?: "",
            longitude = address?.optString("long") ?: "",
            urlSource = sourceUrl,
            timezone = json.optString("timezone"),
            extra1 = json.optString("nbrGuests"),
            facebook = json.optString("pageFacebookId") ?: json.optString("userFacebookId") ?: "",
            pos = "L'Addition"
        )
    }

    private fun JSONObject.toRestaurant(sourceUrl: String, datetime: String): Restaurant {
        val json = this
        val address = json?.optJSONObject("address")
        return Restaurant(
            name = json.optString("name"),
            id = json.getString("id"),
            contactEmail = json.optString("emailContact") ?: json.optString("emailLaddition") ?: "",
            datetime = datetime,
            contactPhone = json.optString("phone") ?: "",
            address1 = address?.optString("streetNumber") + " " + address?.optString("street"),
            address2 = address?.optString("additionalAddress") ?: "",
            town = address?.optString("city") ?: "",
            postcode = address?.optString("zipcode") ?: "",
            country = address?.optString("country") ?: "",
            urlSource = sourceUrl,
            timezone = json.optString("timezone"),
            extra1 = json.optString("nbrGuests"),
            facebook = json.optString("pageFacebookId") ?: json.optString("userFacebookId") ?: "",
            pos = "L'Addition"
        )

    }
}