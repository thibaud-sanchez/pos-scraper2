package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import pos.common.ScraperUtils

class Pulp : HttpPos("pulp", 1, 5) {

    private fun generateRequest(): RestaurantHttpRequest {
        val request = HttpPost("https://prod.api.blacksheep.app/v1")
        request.addHeader("App-Key", "Dq17iaiwWBHbBAWwbvqQwpSyjoVAUqe6")
        request.entity = StringEntity(
            """
                {
                    "operationName": null,
                    "variables": {
                        "offset": 0,
                        "limit": 1000
                    },
                    "query": "query (${'$'}suggested: Boolean, ${'$'}offset: Int, ${'$'}limit: Int, ${'$'}position: JSON) {\n  shops(suggested: ${'$'}suggested, offset: ${'$'}offset, limit: ${'$'}limit, position: ${'$'}position) {\n    total\n    list {\n      id\n      geoloc\n       slug\n       status\n       address\n       phone\n       contactName\n      name\n      brand {\n        name\n        __typename\n      }\n      __typename\n    }\n    __typename\n  }\n}\n"
                }
            """.trimIndent(),
            ContentType.APPLICATION_JSON
        );
        return RestaurantHttpRequest("all", request, "ids")
    }

    override fun initRequestsQueue() {
        restaurantRequestQueue.add(generateRequest())
    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        val json = JSONObject(responseBody)
        return if (json.has("data") && json.getJSONObject("data").has("shops")) {
            val total = json.getJSONObject("data").getJSONObject("shops").getInt("total")
            println("$total new restaurants ! // Total : $total")
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }

    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val restaurantsList = mutableListOf<Restaurant>()
        val content = restaurantDb.data

        val fulljson = JSONObject(content)
        var restaurantArray = fulljson.getJSONObject("data").getJSONObject("shops").getJSONArray("list")


        val count = restaurantArray!!.length()
        for (i in 0 until count) {
            val json = restaurantArray.getJSONObject(i)
            val status = json.optString("status")
            if (status == "ACTIVE") {
                val restaurant = json.toRestaurant(restaurantDb)
                restaurantsList.add(restaurant)
            } else {
                println("Ignore restaurant status $status:  ${json.toString()}")
            }
        }
        return restaurantsList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this
        val geoloc = json.optJSONObject("geoloc")
        val id = json.optString("id")
        val slug = json.optString("slug")
        val address = json.optString("address")?.split(",")
        println(address)
        val zipCity = if (address != null && address.size > 1) address.get(1).trim() else "";
        val city =if(zipCity.split(" ").size>1) zipCity.split(" ")[1] else zipCity
        val zip = if(zipCity.split(" ").size>1) zipCity.split(" ")[0] else ""
        val country = if (address != null && address.size > 2) address[2] else ""
        val brandName = json.optJSONObject("brand")?.optString("name", "") ?: ""
        val name = brandName + " " + json.optString("name", "")
        val url = "https://app.pulp.eu/shop/$id"
        return Restaurant(
            name = name,
            groupId = brandName,
            contactPhone = json.optString("phone"),
            contactName = json.optString("contactName"),
            id = id,
            url = url,
            datetime = restaurantDb.datetime,
            urlSource = restaurantDb.id,
            address1 = address?.get(0) ?: "",
            country = country,
            postcode = zip,
            town = city,
            latitude = geoloc.optString("lat"),
            longitude = geoloc.optString("long"),
            maps = ScraperUtils.getGoogleMapsUrlByLatitudeLongitude(geoloc.optString("lat"), geoloc.optString("long")),
            pos = "Pulp"
        )

    }
}