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

class Obypay : HttpPos("obypay", nbThreads = 5, sleepFromSec = 5, sleepToSec = 15) {

    private val ahrefsDomains = listOf("obypay.com")
    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "id",
            """(https?:\/\/)?.*\.c\.obypay\.com\/(?<id>[a-zA-Z0-9_\-]*)""".toRegex()
        ),
        RestaurantIdRegex(
            "id",
            """manifest\.(?<id>[a-zA-Z0-9_\-]*)\.json""".toRegex()
        ),
        RestaurantIdRegex(
            "id",
            """\?cid=(?<id>[a-zA-Z0-9_\-]*)""".toRegex()
        ),
        RestaurantIdRegex(
            "short",
            """(https?:\/\/)?go\.obypay\.com\/api\/cashless\/hws\/(?<id>[a-zA-Z0-9_\-]*)""".toRegex(),
            true
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
            resolverGoogle.parseGoogleResult("site:$it", false)
        }
    }

    fun combinations(n: Int, arr: CharArray, list: MutableList<CharArray>) {
        // Calculate the number of arrays we should create
        val numArrays = Math.pow(arr.size.toDouble(), n.toDouble()).toInt()
        // Create each array
        for (i in 0 until numArrays) {
            list.add(CharArray(n))
        }
        // Fill up the arrays
        for (j in 0 until n) {
            // This is the period with which this position changes, i.e.
            // a period of 5 means the value changes every 5th array
            val period = Math.pow(arr.size.toDouble(), (n - j - 1).toDouble()).toInt()
            for (i in 0 until numArrays) {
                val current = list[i]
                // Get the correct item and set it
                val index = i / period % arr.size
                current[j] = arr[index]
            }
        }
    }

    fun resolveAllPossibleUrlIds() {

        val chars = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray()
        val list = mutableListOf<CharArray>()
        combinations(2, chars, list)

//        for (iter in 0..3) {
//            var prefix = ""
//            chars.map {
//                val prefix = it.toString()
//                list.add(prefix)
//
//                list.addAll(generateCombination(chars, prefix))
//
//            }
//        }
        val idList = list
            .map { String(it) }
            .map { "https://go.obypay.com/api/cashless/hws/$it" }
            .shuffled()

        println(list.size)
        restaurantIdResolver.resolveAllIdsFromUrlList(idList)
    }

    fun generateCombination(chars: CharArray, prefix: String): List<String> {
        return chars.map {
            prefix + it.toString()
        }
    }

    override fun initRequestsQueue() {
        val restaurantIdentifiers = restaurantIdResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            restaurantRequestQueue.add(generateRequest(it.id))
        }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    private fun generateRequest(id: String): RestaurantHttpRequest {
        val request = HttpGet("https://api2-new.nadvice-app.com/api/cashless/outlets/$id")
        return RestaurantHttpRequest(id, request, "id")
    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        if (httpCode == 404) return RestaurantStatus.UNAVAILABLE

        val json = JSONObject(responseBody)

        return if (json.has("data")) {
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }
    }

    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val content = restaurantDb.data
        val restaurantList = mutableListOf<Restaurant>()

        val json = JSONObject(content)

        val meta = json.getJSONObject("meta")
        if (!meta.optBoolean("success")){
            println("Ignore export: " + meta.optString("message"))
        }
        val data = json.getJSONObject("data")
        try {
            var outlets = data.optJSONArray("outlets")
            if (outlets != null && outlets.length() > 0) {
                for (i in 0 until outlets.length()) {
                    var restaurantJson = outlets.getJSONObject(i)
                    restaurantList.add(restaurantJson.toRestaurant(restaurantDb));
                }
            } else
                restaurantList.add(data.toRestaurant(restaurantDb))
        } catch (e: Exception) {
            println(e)
        }
        return restaurantList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this
        val club = json.getJSONObject("club")
        val geo = club.getJSONObject("geo")
        val provider = club.optJSONObject("provider")?.optString("slug")
        return Restaurant(
            name = club.optString("name", null) ?: json.optString("name"),
            url = json.optString("url"),
            id = json.getString("id"),
            datetime = restaurantDb.datetime,
            address1 = club.optString("address") ?: json.optString("address"),
            postcode = club.optString("zipcode") ?: json.optString("zipcode"),
            town = club.optString("city") ?: json.optString("city"),
            urlSource = restaurantDb.source,
            latitude = geo?.optString("lat") ?: "",
            longitude = geo?.optString("lng") ?: "",
            extra1 = club.optString("description"),
            pos = provider ?: "obypay"
        )

    }
}