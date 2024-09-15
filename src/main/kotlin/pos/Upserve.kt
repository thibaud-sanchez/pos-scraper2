package pos

import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.RestaurantIdRegex
import resolver.RestaurantIdResolver

class Upserve : HttpPos("upserve", 5, 10) {


    val regex = listOf<RestaurantIdRegex>(
        RestaurantIdRegex(
            "slug",
            """https?:\/\/app\.upserve\.com\/[bs]\/(?<id>[a-z\-0-9]*)""".toRegex()
        ),
    )

    val ahrefsResolver = RestaurantIdResolver(basePath, database, regex)


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val content = restaurantDb.data

        val json = JSONObject(content)
        return listOf(json.toRestaurant(restaurantDb))
    }


    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {

        val address = this.getJSONObject("address")
        val id = this.optString("ug_merchant_key")
        return Restaurant(
            name = this.optString("location_name"),
            id = id,
            datetime = restaurantDb.datetime,
            contactPhone = this.optString("phone_number"),
            urlSource = this.optString("source_url"),
            url = this.optString("location_url"),
            address1 = address.optString("line1"),
            address2 = address.optString("line2"),
            town = address.optString("city"),
            postcode = address.optString("zip"),
            state = address.optString("state"),
            maps = this.optString("map_url"),
            latitude = this.optString("latitude"),
            longitude = this.optString("longitude"),
            timezone = this.optString("timezone"),
            pos = "Upserve"
        )
    }

    fun resolveAhrefsIds() {
        ahrefsResolver.resolveAllIdsFromPath()
    }

    override fun initRequestsQueue() {
        val restaurantIdentifiers = ahrefsResolver.getResolvedRestaurantId()
        restaurantIdentifiers.forEach {
            restaurantRequestQueue.add(
                generateUpserveRequest(
                    it.id,
                    it.type
                )
            )
        }
        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    private fun generateUpserveRequest(id: String, type: String): RestaurantHttpRequest {

        val request = HttpGet("https://d2evh2mef3r450.cloudfront.net/s/${id}/online_ordering.json")

        return RestaurantHttpRequest(id, request, type)
    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        if (responseBody!!.contains("AccessDenied")) return RestaurantStatus.UNAVAILABLE

        val json = JSONObject(responseBody)
        return if (json.has("permalink")) {
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.ERROR
        }
    }


}