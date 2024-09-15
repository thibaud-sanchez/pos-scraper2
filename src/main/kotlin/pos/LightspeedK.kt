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

class LightspeedK : HttpPos("lightspeed-k", nbThreads = 1, sleepToSec = 5, sleepFromSec = 0, useProxy = false) {

    private val ahrefsDomains = listOf("mylightspeed.app")

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "id",
            """https?:\/\/mylightspeed\.app\/(?<id>[A-Z\-0-9]*)""".toRegex()
        )
    )
    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)
    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains, false, restaurantIdResolver)

    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)

    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi()
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("site:mylightspeed.app",false)
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

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        return if (httpCode == 200) RestaurantStatus.FOUND else RestaurantStatus.ERROR
    }

    private fun generateRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://mylightspeed.app/api/oa/initialize-session?merchantCode=$id&location=C-ordering")
        return RestaurantHttpRequest(id, request, "id")
    }

    override fun getLinkedRequest(
        previousRequest: RestaurantHttpRequest,
        previousResponse: String?,
        previousResponseHeaders: Array<Header>
    ): HttpRequestBase? {
var sessionId = JSONObject(previousResponse).optJSONObject("session").optString("sessionId")
        var req = HttpGet("https://mylightspeed.app/api/oa/configuration/v1/session/details")
        req.addHeader("Cookie","sessionId=$sessionId")
        return req
    }

    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
//        val content = restaurantDb.data
//
//        if (content.trim().startsWith('{')) {
//            val json = JSONObject(content)
//          //  val domain = ScraperUtils.extractDomain(entry.request.url)
//
//            println("found restaurant $domain")
//            return json.toString()
//        }
        return listOf(JSONObject(restaurantDb.data).toRestaurant(restaurantDb))

    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val restaurantList = mutableListOf<Restaurant>()


        val json = this
        val jsonContact = json.optJSONObject("merchantContactMethodsList")
        var email = if (jsonContact != null && jsonContact.has("EMAIL")) {
            jsonContact.getJSONObject("EMAIL").optString("value", json.optString("email"))
        } else {
            json.optString("email")
        }

        var phone = if (jsonContact != null && jsonContact.has("PHONE")) {
            jsonContact.getJSONObject("PHONE").optString("value", json.optString("phone"))
        } else {
            json.optString("phone")
        }


        var web = if (jsonContact != null && jsonContact.has("WEB")) {
            jsonContact.getJSONObject("WEB").optString("value")
        } else ""

        var facebook = if (jsonContact != null && jsonContact.has("FACEBOOK")) {
            jsonContact.getJSONObject("FACEBOOK").optString("value")
        } else ""

        return Restaurant(
            id = json.optString("merchantId"),
            extra1 = restaurantDb.id,
            name = json.optString("name"),
            datetime = restaurantDb.datetime,
            contactPhone = phone,
            contactEmail = email,
            facebook = facebook,
            url = web,
            address1 = json.optString("address1"),
            address2 = json.optString("address2"),
            town = json.optString("city"),
            country = json.optString("country"),
            postcode = json.optString("zip"),
            urlSource = restaurantDb.source.replace("GET ", "").replace(" HTTP/1.1", ""),
            timezone = json.optString("timezone"),
            pos = "LightspeedK"

        )

    }
}