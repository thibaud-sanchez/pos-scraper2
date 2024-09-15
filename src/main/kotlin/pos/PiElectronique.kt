package pos

import model.Restaurant
import net.lightbody.bmp.core.har.HarEntry
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import org.openqa.selenium.logging.LogEntry
import pos.common.ChromeDriverLogType
import pos.common.ChromeDriverPos
import pos.common.RestaurantHttpRequest
import resolver.*

class PiElectronique : ChromeDriverPos("pi", ChromeDriverLogType.LOG) {

    val regex = """https?:\/\/cw\.mypi\.net\/cw\/(?<id>\d+)""".toRegex()
    val resolver = RestaurantIdResolver(basePath, database, listOf(RestaurantIdRegex("piclick", regex)))

    val ahrefsResolver = AhrefsResolver(basePath, database, listOf("cw.mypi.net"), false, resolver)
    var resolverGoogle = GoogleSearchResolver(basePath, database, resolver)


    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromApi()
    }

    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult("cw.mypi.net")
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

        val location = this.optJSONObject("coordonnees")
        return Restaurant(
            name = this.optString("nom"),
            id = id,
            datetime = datetime,
            contactPhone = this.optString("telephone"),
            address1 = this.optString("adresse1"),
            town = this.optString("ville"),
            latitude = location.optString("latitude") ?: "",
            longitude = location.optString("longitude") ?: "",
            pos = "PiElectronique"
        )
    }

    override fun isValidHarEntry(request: RestaurantHttpRequest, entry: HarEntry): Boolean {
        return false
    }


    override fun isValidLogEntry(request: RestaurantHttpRequest, entry: LogEntry): Boolean {
        val jsonObject = JSONObject(entry.message)
        val messageObject: JSONObject = jsonObject.get("message") as JSONObject
        if (messageObject.getString("method") == "Network.webSocketFrameReceived") {
            val params = messageObject.get("params") as JSONObject
            val response = params.get("response") as JSONObject
            val payload = response.getString("payloadData")
            val start = "42/CW,[\"get-sites\","
            return payload.startsWith(start)
        }
        return false
    }

    override fun getPayloadForValidLogEntry(request: RestaurantHttpRequest, entry: LogEntry): String {
        val jsonObject = JSONObject(entry.message)
        val messageObject: JSONObject = jsonObject.get("message") as JSONObject
        if (messageObject.getString("method") == "Network.webSocketFrameReceived") {
            val params = messageObject.get("params") as JSONObject
            val response = params.get("response") as JSONObject
            val payload = response.getString("payloadData")
            val start = "42/CW,[\"get-sites\","
            if (payload.startsWith(start)) {

                val json = payload.substring(start.length, payload.length - 1)
                val jsonObj = JSONArray(json)

                return jsonObj.toString()
            }
        }
        throw RuntimeException("Invalid log entry")
    }

    override fun getPayloadForValidHarEntry(request: RestaurantHttpRequest, entry: HarEntry): String? {
        return null
    }

    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantHttpRequest>()

//         for (id in 30791..30792) {
        for (id in 30000..31000) {
            list.add(generatePiRequest(id.toString()))

        }
        restaurantRequestQueue.addAll(list.shuffled())

    }
//
//    override fun initRequestsQueue() {
//        val restaurantIdentifiers = resolver.getResolvedRestaurantId()
//        restaurantIdentifiers.forEach {
//            when (it.type) {
//                "piclick" -> restaurantRequestQueue.add(generatePiRequest(it.id))
//            }
//        }
//    }

    private fun generatePiRequest(id: String): RestaurantHttpRequest {

        val request = HttpGet("https://cw.mypi.net/cw/$id")
        val idName = id.padStart(5, '0')

        return RestaurantHttpRequest(idName, request, "id")
    }

}