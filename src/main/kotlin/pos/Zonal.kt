package pos

import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.json.JSONArray
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.*

class Zonal : HttpPos("zonal", nbThreads = 5, sleepFromSec = 5, sleepToSec = 15) {
    private val ahrefsDomains = listOf("zonalconnect.com")

    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex("order", """https?:\/\/(?<id>[a-z\-0-9]*)\.wlwo-iorder\.zonalconnect\.com""".toRegex())
    )

    //https://order.zelty.fr/index.html#zkey=1af0e784ebc9fff
    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)

    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains, false, restaurantIdResolver)

    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)

    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi()
    }

    fun resolveAllIds() {
        restaurantIdResolver.resolveAllIdsFromPath(format = ResolverInputFormat.BASIC_TEXT, false);
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("site:zonalconnect.com", false)
    }

    override fun convertRawDataToRestaurant(rawData: RestaurantDb): List<Restaurant> {
        return getRestaurantFromJson(rawData.id,rawData.source,JSONObject(rawData.data), rawData.datetime)
    }

    private fun JSONObject.toRestaurant(id: String,source:String,datetime: String): Restaurant {
        val json = this
        val social = json.optJSONObject("social")
        val contact = json.optJSONObject("contactDetails")
        val address = json.optJSONObject("address")
        val country = address.optJSONObject("country")
        val location = address.optJSONObject("location")

        return Restaurant(
            name = json.optString("name"),
            id = "$id-${json.optString("venueRef")}",
            urlSource = source,
            datetime = datetime,
            contactPhone = contact.optString("telephone"),
            contactEmail = contact.optString("email"),
            contactName =  json.optString("manager"),
            address1 = address.optString("line1"),
            town = address.optString("town"),
            postcode = address.optString("postcode"),
            state = address.optString("county"),
            timezone = json.optString("timezone"),
            latitude = location?.optString("latitude") ?: "",
            longitude = location?.optString("longitude") ?: "",
            country = country.optString("code"),
            facebook = social.optString("facebook"),
            googleId = social.optString("googleplus"),
groupId = id,
            pos = "Zonal"
        )
    }


    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            restaurantRequestQueue.add(generateZonalRequest(it.id))
        }
        println("Total id to extract = ${restaurantRequestQueue.size}")
    }


    private fun generateZonalRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://$id.wlwo-iorder.zonalconnect.com/env-config.js")
        return RestaurantHttpRequest(id, request, "id")
    }


    override fun getLinkedRequest(
        previousRequest: RestaurantHttpRequest,
        previousResponse: String?,
        previousResponseHeaders: Array<Header>
    ): HttpRequestBase? {

        val tokenRegex = """REACT_APP_BRAND_TOKEN\: \'(?<id>[A-Za-z0-9=]*)\',""".toRegex()
        val token = tokenRegex.find(previousResponse?:"")
        val tokenValue = token?.groups?.get("id")?.value
        if(tokenValue!=null) {
            println("Found token for ${previousRequest.id} : $tokenValue")


            var req = HttpPost("https://iopapi.zonalconnect.com/")
            req.addHeader(
                "X-Auth-BrandToken",tokenValue
            )
            val query = "request=%7B%22request%22%3A%7B%22method%22%3A%22venues%22%2C%22platform%22%3A%22web%22%2C%22userDeviceIdentifier%22%3A%22web%22%7D%7D"
            req.entity= StringEntity(
                query,
                ContentType.APPLICATION_FORM_URLENCODED
            );
            return req
        }
        else{
            println("Unable to find token for ${previousRequest.id} : $previousResponse")

            return null
        }
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        return if (httpCode == 200) RestaurantStatus.FOUND else RestaurantStatus.ERROR

    }


    fun getRestaurantFromJson(id: String,source:String,json: JSONObject, datetime: String): List<Restaurant> {

        val restaurantsList = mutableListOf<Restaurant>()
        var array: JSONArray? = json.optJSONArray("venues")
       if (array == null) {
            println("Unable to find restaurant for current json $id")
            return emptyList()
        }


        val count = array!!.length()
        for (i in 0 until count) {
            try {
                val json = array.getJSONObject(i)
                val restaurant = json.toRestaurant(id,source,datetime)
                restaurantsList.add(restaurant)
            } catch (e: Exception) {
                println("Invalid restaurant :  ${json.toString()}")
            }
        }
        return restaurantsList
    }
}