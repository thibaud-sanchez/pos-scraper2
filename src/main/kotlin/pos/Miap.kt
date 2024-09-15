package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import pos.common.ScraperUtils
import java.io.File

class Miap : HttpPos("miap", 5, 20) {


    private fun generateMiapRequest(id: Int): RestaurantHttpRequest {

        val request = HttpGet("https://mytrivec.com/srv/connect/api/client/$id/default")
        val idName = id.toString().padStart(5, '0')

        return RestaurantHttpRequest(idName, request, "id")
    }


    private var totalcurrent = 0
    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantHttpRequest>()
        for (id in 0..11000) {
//        for (id in 6105..6107) {
            list.add(generateMiapRequest(id))
        }
        restaurantRequestQueue.addAll(list.shuffled())
        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        if (responseBody != null && responseBody.contains("No business information")) {
            return RestaurantStatus.UNAVAILABLE
        }
        val json = JSONObject(responseBody)
        return if (json.has("companyInfo")) {
            totalcurrent++
            println("1 new restaurant ! // Total : $totalcurrent")
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }

    }

    fun saveAllFromJsonToDb() {
        val jsonTxt = File(basePath, "all.json").readText()
        val jsonArray = JSONArray(jsonTxt)
        for (i in 0 until jsonArray.length()) {
            val restaurantJson = jsonArray.getJSONObject(i)
            val id = restaurantJson.optString("id")
            database.saveRestaurant(
                id,
                RestaurantStatus.FOUND,
                "https://app.miap.co/?pickup=$id",
                restaurantJson.toString()
            )

        }
    }

    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {

        val restaurantList = mutableListOf<Restaurant>()
        val content = restaurantDb.data
        val json = JSONObject(content)
        restaurantList.add(json.toRestaurant(restaurantDb.id, restaurantDb.datetime))

        return restaurantList
    }

    private fun JSONObject.toRestaurant(sourceId: String, datetime: String): Restaurant {
        val json = this
        val address: JSONObject? = json.getJSONArray("address").optJSONObject(0)

        var address1 = address?.optString("street_number") + ", " + address?.optString("street_name")
        var lat = address?.optString("lat")
        var long = address?.optString("lon")

        return Restaurant(
            name = json.optString("name"),
            id = sourceId,
            urlSource = "https://app.miap.co/?pickup=$sourceId",
            datetime = datetime,
            contactPhone = json.optString("phone"),
            contactPhone2 = json.optString("gsm"),
            contactEmail = json.optString("email"),
            address1 = address1,
            postcode = address?.optString("zip_code")?:"",
            town = address?.optString("city")?:"",
            latitude = lat?:"",
            longitude = long?:"",
            maps = if(lat!=null && long!=null) ScraperUtils.getGoogleMapsUrlByLatitudeLongitude(lat, long) else "",
            url = json.optString("url"),
            facebook = json.optString("facebook_url", json.optString("instagram_url")),
            extra1 = if (json.optBoolean("pickup", false)) "Pickup" else "Menu",
            googleId = json.optString("google_my_business_url"),
            pos = json.optString("pos_system", "")
        )

    }
}