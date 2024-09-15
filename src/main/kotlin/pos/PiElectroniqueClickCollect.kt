package pos

import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.*

class PiElectroniqueClickCollect : HttpPos("piclick",sleepToSec = 5,sleepFromSec = 1) {

    val regex = """https?:\/\/piclick\.mypi\.net\/(?<id>\d+)""".toRegex()
    val resolver = RestaurantIdResolver(basePath, database, listOf(RestaurantIdRegex("piclick", regex)))

    val ahrefsResolver = AhrefsResolver(basePath, database, listOf("piclick.mypi.net"), false, resolver)
    var resolverGoogle = GoogleSearchResolver(basePath, database, resolver)


    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi()
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("piclick.mypi.net")
    }

    fun resolveAllIds() {
        resolver.resolveAllIdsFromPath(
            format = ResolverInputFormat.FORMAT_EXPORT_V1,
            resolveIntoWebsiteContent = false
        );
    }


    override fun convertRawDataToRestaurant(rawData: RestaurantDb): List<Restaurant> {
        val restaurantList = mutableListOf<Restaurant>()

        val content = rawData.data
        try {
            val jsons = JSONArray(content)
            for (i in 0 until jsons.length()) {
                val json = jsons.getJSONObject(i)
                restaurantList.add(json.toRestaurant(rawData.id, rawData.datetime))
            }

        } catch (e: Exception) {
            println("Unable to read restaurant information for ${rawData.id} : $content")
        }
        return restaurantList

    }


    private fun JSONObject.toRestaurant(id: String, datetime: String): Restaurant {

        val address =this.optJSONObject("address")
        return Restaurant(
            name = this.optString("name"),
            id = this.optString("siteId"),
            groupId=this.optString("brandId"),
            datetime = datetime,
            contactPhone = this.optString("phoneNumber"),
            address1 = address.optString("mainAddress"),
            town = address.optString("city"),
            postcode = address.optString("zipCode"),
            country = address.optString("country"),
            timezone =this.optString("timezone"),
            url = this.optString("redirectionUrl"),
            pos = "PiElectronique"
        )
    }


    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantHttpRequest>()

        for (id in 1000..2000) {
   //     for (id in 15..16) {
            list.add(generatePiRequest(id.toString()))
        }
        restaurantRequestQueue.addAll(list.shuffled())

    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {

        val json = JSONArray(responseBody)
        return if (json.length() > 0) {
            println("Found ${json.length()} restaurants" )
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }

    }

    private fun generatePiRequest(id: String): RestaurantHttpRequest {

        val request = HttpGet("https://deliveries.gtwy.aphilia.io/parameters/public/$id")
        val idName = id.padStart(5, '0')

        return RestaurantHttpRequest(idName, request, "id")
    }

}