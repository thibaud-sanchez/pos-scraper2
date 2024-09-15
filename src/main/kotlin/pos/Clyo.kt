package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest

class Clyo : HttpPos("clyo") {


    override fun initRequestsQueue() {
        val list = mutableListOf<RestaurantHttpRequest>()
        for (id in 2400..2500) {
            list.add(generateClyoRequest(id))
        }
        restaurantRequestQueue.addAll(list.shuffled())

    }

    private fun generateClyoRequest(id: Int): RestaurantHttpRequest {

        val request = HttpGet("http://app.eatself.com/public/index.php/admin/user/shop-info?ShopId=$id")
        val idName = id.toString().padStart(5, '0')

        return RestaurantHttpRequest(idName, request, "id")
    }



    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        val json = JSONObject(responseBody)
        return if (json.optString("status") == "KO") {
            RestaurantStatus.UNAVAILABLE
        } else if (json.optString("status") == "OK") {
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.ERROR
        }

    }

    override fun saveSuccessRestaurantResponse(request: RestaurantHttpRequest, responseBody: String?, extra: String) {
        val json = JSONObject(responseBody)

        val shop = json.getJSONObject("shop")
        saveClyoShop(request, shop)
    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {
        val content = restaurantDb.data

        val json = JSONObject(content)
        return listOf(json.toRestaurant(restaurantDb))
    }

    private fun JSONObject.toRestaurant(restaurantDb:RestaurantDb): Restaurant {
        val json = this.getJSONObject("shop")

        val location = json.optJSONObject("Location")

        val address1 = json.optString("Address1") + " " + json.optString("Address2")
        val id = json.optString("ShopId")
        val url = "https://app.eatself.com/$id/accueil"
        return Restaurant(
            name = json.optString("DefaultName"),
            id = id,
            datetime=restaurantDb.datetime,
            groupId = json.optString("GroupId"),
            contactEmail = json.optString("Email"),
            contactName = json.optString("contactName"),
            contactPhone = json.optString("Tel"),
            contactPhone2 = json.optString("Mobile"),
            url = url,
            address1 = address1,
            town = json.optString("City"),
            postcode = json.optString("Zip"),
            country = json.optString("Country"),
            facebook = json.optString("Facebook"),
            latitude = location?.optString("latitude") ?: "",
            longitude = location?.optString("longitude") ?: "",
            urlSource = restaurantDb.source,
            pos = "Clyo"
        )
    }

    fun saveClyoShop(request: RestaurantHttpRequest, json: JSONObject, sister: Boolean = false) {
        val shop = json.getInt("ShopId")
        val idName = shop.toString().padStart(5, '0')
        // val resultFile = File(basePath, "$idName.txt")
        val statusJson = JSONObject()
        statusJson.put("status", "OK")
        statusJson.put("shop", json)

        database.saveRestaurant(idName, RestaurantStatus.FOUND, request.request.uri.toString(), statusJson.toString())

        println("Restaurant #$shop saved ! (from sister = $sister)")

        val array = json.optJSONArray("sister_shops")
        if (array != null) {
            val tot = array.length()
            for (i in 0 until tot) {
                //Save sister only if it's different from parent
                val sister = array.getJSONObject(i)
                val sisterid = sister.getInt("ShopId")
                if (sisterid != shop) {
                    saveClyoShop(request, sister, true)
                }
            }
        }
    }


}