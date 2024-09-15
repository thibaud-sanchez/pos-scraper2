package pos


import model.Restaurant
import org.json.JSONObject
import pos.common.HttpGlypePos
import pos.common.RestaurantGlypeRequest
import resolver.ResolverInputFormat
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver
import resolver.location.CloverLocationResolver2

class Clover : HttpGlypePos("clover") {


    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "slug",
            """https?:\/\/www\.clover\.com\/online-ordering\/(?<id>[a-zA-Z\-0-9]*?)(\/|${'$'}|\?)""".toRegex()
        ),
    )

    val ahrefsResolver = RestaurantIdResolver(basePath, database, regex)
    val locationResolver = CloverLocationResolver2()

    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromPath(ResolverInputFormat.FORMAT_EXPORT_V2)
    }

    fun clearSlugIdAlreadyResolved() {
        var totalRemoved = 0
        var totalKeeped = 0
        database.getFoundRestaurants().forEach { rest ->
            try {
                val content = rest.data
                val json = JSONObject(content)
                val slug = json.optString("slug")
                if (slug.isNotBlank()) {
                    if (ahrefsResolver.removeResolvedId(slug)) {
                        println("Ignore resolved slug \"$slug\" because already resoled wit id ${rest.id}")
                        totalRemoved++
                    } else {
                        println("Keeping slug $slug for extraction (not already exist)")
                        totalKeeped++
                    }
                }
            } catch (e: Exception) {
                println("Unable to check if slug is resolved for restaurant ${rest.id} : ${rest.data}")
                e.printStackTrace()
            }

        }
    }

    fun resolveLocationIds() {
        locationResolver.start()
    }

    override fun initRequestsQueue() {
        val restaurantIdentifiers = locationResolver.getResolvedIds()
        restaurantIdentifiers.forEach {
            when (it.type) {

                "merchantId" -> {
                    var extraJson = JSONObject(it.extra)
                    var oloEnabled = extraJson.optBoolean("coloEnabled", false)
                    if (oloEnabled) {
                        restaurantRequestQueue.add(
                            RestaurantGlypeRequest(
                                it.id,
                                "https://www.clover.com/oloservice/v1/merchants/${it.id}",
                                it.type
                            )
                        )
                    } else {
                        database.saveRestaurant(it.id, RestaurantStatus.FOUND, it.source, it.extra)
                    }
                }
                "slug" -> {
                    restaurantRequestQueue.add(
                        RestaurantGlypeRequest(
                            it.id,
                            "https://www.clover.com/oloservice/v1/merchants/${it.id}?slug=true",
                            it.type
                        )
                    )
                }
            }
        }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    override fun determineResponseStatus(request: RestaurantGlypeRequest, responseBody: String?): RestaurantStatus {
        val json = JSONObject(responseBody)
        return if (json.has("merchantUuid")) {
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }

    }

    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val content = restaurantDb.data

        val json = JSONObject(content)
        return listOf(json.toRestaurant(restaurantDb))
    }

    private fun JSONObject.toRestaurant(restaurantDb:RestaurantDb): Restaurant {
        val json = this
        if (restaurantDb.source.contains("oloservice")) {
            val address = json.optJSONObject("address")

            val address1 = address.optString("address1") + " " + json.optString("address2")
            val id = json.optString("merchantUuid")
            val slug = json.optString("slug")
            val url = "https://www.clover.com/online-ordering/$slug"
            return Restaurant(
                name = json.optString("name"),
                id = id,
                datetime=restaurantDb.datetime,
                url = url,
                contactPhone = json.optString("phone"),
                address1 = address1,
                town = address.optString("city"),
                postcode = address.optString("zip"),
                state = address.optString("state"),
                urlSource = restaurantDb.source,
                timezone = json.optString("timezone"),
                pos = "Clover"
            )
        } else {

            val addressFull = json.optString("addressString")
            val addressSplit = addressFull.split(", ")
            var address = addressFull
            var state = ""
            var city = ""
            if (addressSplit.size > 2) {
                state = addressSplit[addressSplit.size - 1]
                city = addressSplit[addressSplit.size - 2]
                val addressItems = addressSplit.slice(0..addressSplit.size - 3)
                address = addressItems.joinToString(", ")
            }

            val id = json.optString("cloverMerchantId")
            return Restaurant(
                name = json.optString("merchantName"),
                id = id,
                datetime=restaurantDb.datetime,
                address1 = address,
                state = state,
                town = city,
                urlSource = restaurantDb.source,
                latitude = json.optDouble("latitude").toString(),
                longitude = json.optDouble("longitude").toString(),
                pos = "Clover"
            )
        }
    }
}