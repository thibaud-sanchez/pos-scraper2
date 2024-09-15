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

class TBDine : HttpPos("tbdine" /*,nbThreads = 1, sleepFromSec = 1, sleepToSec = 5*/) {

    // execute this to get a new token: https://order.tbdine.com/pickup/25673/
    private val token = "d6b1cde562acdfcad4b013fcba21f68ed1b23fed"

    private val ahrefsDomains = listOf("order.tbdine.com")
    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "id",
            """(https?:\/\/)?order\.tbdine\.com\/pickup\/(?<id>[a-zA-Z0-9_\-]*)""".toRegex()
        ),
        RestaurantIdRegex(
            "id",
            """\"venueXRefID\"\s*:\s*\"(?<id>[0-9_\-]*)\"""".toRegex()
        ),
//        RestaurantIdRegex(
//            "str",
//            """(https?:\/\/)?order\.tbdine\.com\/(?!pickup)(?<id>[a-zA-Z0-9_\-]+)((\?src)?|(\/pickup)?)""".toRegex(),
//            preContent = true
//        ),
    )


    val restaurantIdResolver = RestaurantIdResolver(
        basePath,
        database,
        regex,
        ignoreUrlsPattern = listOf("""^(https?:\/\/)?order\.tbdine\.com\/?${'$'}""".toRegex())
    )

    val ahrefsResolver = AhrefsResolver(basePath, database, ahrefsDomains, false, restaurantIdResolver)
    var resolverGoogle = GoogleSearchResolver(basePath, database, restaurantIdResolver)


    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi(false, 10000)
    }

    fun resolveGoogleids() {
        ahrefsDomains.forEach {
            resolverGoogle.parseGoogleResult("site:$it", false)

        }
    }
//
//    override fun initRequestsQueue() {
//        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
//        restaurantIdentifiers.forEach {
//            when (it.type) {
//                "id" -> restaurantRequestQueue.add(generateRequest(it.id))
//            }
//        }
//
//        println("Total id to extract = ${restaurantRequestQueue.size}")
//    }

    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantHttpRequest>()
        for (id in 25000..41000) {
            list.add(generateRequest(id.toString()))
        }
        restaurantRequestQueue.addAll(list.shuffled())

    }
    private fun generateRequest(id: String): RestaurantHttpRequest {
        val request =
            HttpGet("https://order.tbdine.com/_next/data/$token/pickup/$id/menu.json")
        return RestaurantHttpRequest(id, request, "id")
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {

        if (responseBody!!.contains("err_model_not_found") || responseBody.contains("Not Acceptable"))
            return RestaurantStatus.UNAVAILABLE
        else if (responseBody.contains("err_schedule_no_time_slots")) {
            return RestaurantStatus.ERROR
        }
        try {
            JSONObject(responseBody)
            return RestaurantStatus.FOUND
        }catch (e:Exception){
            println("Unable to parse json for http response: $responseBody")
            return RestaurantStatus.ERROR
        }

    }

    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val content = restaurantDb.data

        val json = JSONObject(content)

        return listOf(json.toRestaurant(restaurantDb.source, restaurantDb.datetime))
    }

    private fun JSONObject.toRestaurant(sourceUrl: String, datetime: String): Restaurant {
        val json = this.getJSONObject("pageProps").getJSONObject("venue")
        val address = json.optString("address")
        val splitAddress = address.split(",")
        return Restaurant(
            name = json.optString("name"),
            id = json.getString("venueXRefID"),
            datetime = datetime,
            contactPhone = json.optString("phone") ?: "",
            address1 = splitAddress.getOrElse(0) { "" }.trim(),
            town = splitAddress.getOrElse(1) { "" }.trim(),
            state = splitAddress.getOrElse(2) { "" }.trim(),
            country = json.optString("venueCountry") ?: "",
            urlSource = sourceUrl,
            latitude = json.optString("latitude"),
            longitude = json.optString("longitude"),
            url = json.optString("venueUrl") ?: "",
            pos = "TouchBistro"
        )

    }
}