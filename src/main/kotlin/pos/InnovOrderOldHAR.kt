package pos

import model.Restaurant
import net.lightbody.bmp.core.har.HarEntry
import org.json.JSONObject
import org.openqa.selenium.logging.LogEntry
import pos.common.ChromeDriverLogType
import pos.common.ChromeDriverPos
import pos.common.RestaurantHttpRequest
import pos.common.ScraperUtils
import resolver.GoogleSearchResolver
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver

class InnovOrderOldHAR : ChromeDriverPos("innovorder", ChromeDriverLogType.HAR) {

    //val basePath = File("data/innovorder")


    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "id",
            """window\.brandHash=\"(?<id>.[a-f0-9]*)\"""".toRegex()
        )
    )

    val regexResolver = RestaurantIdResolver(basePath, database, regex)
    var resolverGoogle = GoogleSearchResolver(basePath, database, regexResolver)


    fun resolveGoogleids() {
        resolverGoogle.parseGoogleResult(""""propulsé par innovorder"""")
    }
//
//    fun buildCSV() {
//
//        val writer = RestaurantWriter(File(basePath, "innovorder.csv"))
//        var count = 0
//        basePath.walk().filter { it.name.endsWith("txt") }.forEach {
//            val content = it.readText()
//            try {
//                val url = "http://${it.nameWithoutExtension}/home/places"
//                val json = JSONObject(content)
//                val restaurants = json.toRestaurants(url)
//                restaurants.forEach {
//                    writer.appendRestaurant(it)
//                    count++
//                }
//
//            } catch (e: Exception) {
//                println("Unable to read restaurant information for ${it.name} : $content")
//            }
//        }
//        writer.close()
//        println("Finish export : $count lines")
//    }

    override fun convertRawDataToRestaurant(rawData: RestaurantDb): List<Restaurant> {
        return JSONObject(rawData.data).toRestaurants(rawData.source, rawData.datetime)
    }


    override fun isValidHarEntry(request: RestaurantHttpRequest, entry: HarEntry): Boolean {
        return entry.request.url.contains("configuration");
    }

    override fun isValidLogEntry(request: RestaurantHttpRequest, entry: LogEntry): Boolean {

        return false
    }

    override fun getPayloadForValidLogEntry(request: RestaurantHttpRequest, entry: LogEntry): String? {
        return null
    }

    override fun getPayloadForValidHarEntry(request: RestaurantHttpRequest, entry: HarEntry): String? {
        val content = entry.response.content.text

        if (content?.trim() != null && content.trim().startsWith('{')) {
            val json = JSONObject(entry.response.content.text)
            val domain = ScraperUtils.extractDomain(entry.request.url)

            println("found restaurant $domain")
            return json.toString()
        }
        return null
    }

    override fun initRequestsQueue() {
        TODO("Not yet implemented")
    }



    private fun JSONObject.toRestaurants(url: String, datetime: String): List<Restaurant> {
        val restaurants = mutableListOf<Restaurant>()
        val restaurantArray = this.getJSONObject("data").getJSONArray("restaurants")

        for (i in 0 until restaurantArray.length()) {

            val rest = restaurantArray.getJSONObject(i)


            val address = rest.getJSONObject("address")
            val address1 = address.optString("streetNumber") + " " + address.optString("route")
            restaurants.add(
                Restaurant(
                    name = rest.optString("name"),
                    id = rest.optString("restaurantId"),
                    datetime = datetime,
                    contactEmail = rest.optString("contactEmail"),
                    contactName = rest.optString("contactName"),
                    contactPhone = rest.optString("contactPhone"),
                    url = url,
                    address1 = address1,
                    town = address.optString("locality"),
                    postcode = address.optString("postalCode"),
                    googleId = address.optString("googlePlaceId"),
                    country = address.optString("country"),
                    latitude = address.optString("lat"),
                    longitude = address.optString("lng"),
                    pos = "Innovorder"
                )
            )
        }
        return restaurants


    }

}