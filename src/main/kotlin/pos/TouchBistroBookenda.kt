package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest

class TouchBistroBookenda : HttpPos("touchbistro", useProxy = true) {


    override fun initRequestsQueue() {
        for (id in 0..900) {
            val request =
                HttpPost("https://www.bookenda.com/Services/api/1.1/UserBookingService.svc/webssl/SearchMerchantService")
            request.entity = StringEntity(
                """
               {
    "request": {
        "Header": {
            "PartnerToken": "olHIMEcwrMSBedS6XxISghYjjKAGj0bc",
            "CultureName": "en-US",
            "ClientOffSet":60,
            "Language": 1
        },
        "Body": {
            "SearchInfo": {
                "IdServiceCategory": 4,
                "IncludeUnmapped": true,
                "AvailabilityStatusToReturn": [
                    0
                ],
                "IdMerchants": null,
                "IdDistanceUnitOfMeasure": 1,
                "IdRegion": $id,
                "SortOrder": 1
            },
            "PageSize": 500,
            "PageIndex": 0
        }
    }
}
            """.trimIndent(),
                ContentType.APPLICATION_JSON
            );


            restaurantRequestQueue.add(RestaurantHttpRequest(id.toString(), request, "merchant"))
        }
    }


    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {

        val json = JSONObject(responseBody)
        return if (json.has("SearchMerchantServiceResult")
            && json.getJSONObject("SearchMerchantServiceResult").getJSONObject("Body").getInt("ItemCount") > 0
        ) {
            println(
                "Found " + json.getJSONObject("SearchMerchantServiceResult").getJSONObject("Body")
                    .getInt("ItemCount") + " for region ${request.id}"
            )
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }
    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        return JSONObject(restaurantDb.data).toRestaurants(restaurantDb)

    }

    private fun JSONObject.toRestaurants(restaurantDb: RestaurantDb): List<Restaurant> {
        val restaurantList = mutableListOf<Restaurant>()


        val json = this.getJSONObject("SearchMerchantServiceResult")
            .getJSONObject("Body")

        var jsonArray = json.optJSONArray("Items")
        if (jsonArray != null) {
            for (i in 0 until jsonArray.length()) {
                var restaurantJson = jsonArray.getJSONObject(i)
                restaurantList.add(
                    Restaurant(
                        id = restaurantJson.optString("IdMerchant"),
                        name = restaurantJson.optString("MerchantName"),
                        contactPhone = restaurantJson.optString("MerchantPrimaryPhoneNumber"),
                        contactPhone2 = restaurantJson.optString("MerchantSecondaryPhoneNumber"),
                        url = restaurantJson.optString("MerchantWebSite"),
                        datetime = restaurantDb.datetime,
                        address1 = restaurantJson.optString("MerchantAddress"),
                        latitude = restaurantJson.optString("MerchantLatitude"),
                        longitude = restaurantJson.optString("MerchantLongitude"),
                        urlSource = restaurantDb.source,
                        pos = "TouchBistro"
                    )
                )
            }
        }
        return restaurantList

    }
}