package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.message.BasicNameValuePair
import org.apache.http.protocol.HTTP
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.AhrefsResolver
import resolver.GoogleSearchResolver
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver


class Silverware : HttpPos("silverware" ) {

    private val ahrefsDomains = listOf("order2.silverwarepos.com")
    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "id",
            """(https?:\/\/)?order2\.silverwarepos\.com\/app\/(?<id>[a-zA-Z0-9_\-]*)""".toRegex()
        )
    )


    val restaurantIdResolver = RestaurantIdResolver(basePath, database, regex)

    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains, false, restaurantIdResolver)
    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)


    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi(false, 10000)
    }


    fun resolveGoogleids() {
        ahrefsDomains.forEach {
            resolverGoogle.parseGoogleResult("site:$it",false)

        }
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
        val request =
            HttpPost("https://order2.silverwarepos.com/v75/api/Chains/Config")
        val nvps: MutableList<NameValuePair> = ArrayList<NameValuePair>()
        nvps.add(BasicNameValuePair("Alias", id))

        request.entity=UrlEncodedFormEntity(nvps, HTTP.UTF_8)
        return RestaurantHttpRequest(id, request, "id")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {

        if (responseBody!!.contains("Chain not found.")) return RestaurantStatus.UNAVAILABLE
        JSONObject(responseBody)
        return RestaurantStatus.FOUND

    }

    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val restaurantList = mutableListOf<Restaurant>()

        val content = restaurantDb.data

        val json = JSONObject(content).getJSONObject("Config")
        var jsonArray = json.optJSONArray("Stores")
        if (jsonArray == null) {
            println("UNABLE TO FIND STORES: $content")
            return restaurantList
        }
        for (i in 0 until jsonArray.length()) {
            val restaurantJson=jsonArray.getJSONObject(i)
            json.optString("Name")
            restaurantList.add(restaurantJson.toRestaurant(json.optString("Name"),restaurantDb))
        }
        return restaurantList
    }

    private fun JSONObject.toRestaurant(brandName:String,restaurantDb: RestaurantDb): Restaurant {
        val json = this
        val location:JSONObject?= json.optJSONObject("Coordinates")

        return Restaurant(
            name = json.optString("Name"),
            id = json.optString("Alias"),
            datetime = restaurantDb.datetime,
            groupId = brandName,
            contactPhone = json.optString("Phone"),
            address1 = json.optString("Address1"),
            address2 = json.optString("Address2"),
            postcode = json.optString("PostalCode"),
            town = json.optString("City"),
            country = json.optString("countryCode"),
            state = json.optString("Province"),
            latitude = location?.optString("Lat")?:"",
            longitude = location?.optString("Lng")?:"",
            urlSource = restaurantDb.source,
            pos = "Silverware"
        )


    }
}