package pos


import model.Restaurant
import org.apache.commons.lang3.StringUtils
import org.apache.http.Header
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import resolver.location.LatLng
import resolver.location.LocationGenerator

class ToastUK : HttpPos("toastuk") {

    override fun initRequestsQueue() {

        val meters = 20_000
        val points = mutableListOf<LatLng>()

            points.addAll(LocationGenerator.generateLocations(LatLng(53.47912, -2.243246, "Manchester"), meters))
            points.addAll(LocationGenerator.generateLocations(LatLng(53.408862, -2.9907867, "Liverpool"), meters))
        points.addAll(LocationGenerator.generateLocations(LatLng(52.4797967, -1.90490, "Glasgow"), meters))
        points.addAll(LocationGenerator.generateLocations(LatLng(53.7954599, -1.53374, "Leeds"), meters))
        points.addAll(LocationGenerator.generateLocations(LatLng(53.3797775, -1.47038884, "Sheffield"), meters))
        points.addAll(LocationGenerator.generateLocations(LatLng(52.478819, -1.8923731, "Birmingham"), meters))
        points.addAll(LocationGenerator.generateLocations(LatLng(53.93180,-6.3356, "Bristol"), meters))
        points.addAll(LocationGenerator.generateLocations(LatLng(51.4733426,-2.60385, "New Castle"), meters))
   //     points.addAll(LocationGenerator.generateLocations(LatLng(51.49512, -0.12779, "London"), meters))

        points
            .forEach { for (i in 0..100 step 30) {
                println(i)
                restaurantRequestQueue.add(generateToastRequest(it,i))
            } }

    }

    private fun generateToastRequest(it: LatLng, offset:Int): RestaurantHttpRequest {
        val graphQLQuery =
            """
           {"query":"query GetRestaurants(${'$'}cityId: Int, ${'$'}cuisineId: Int, ${'$'}enabledFeatures: [String], ${'$'}prices: [String], ${'$'}minRating: Int, ${'$'}search: String, ${'$'}userLat: Float, ${'$'}userLong: Float, ${'$'}limit: Int, ${'$'}offset: Int) {\n  restaurants(\n    cityId: ${'$'}cityId\n    cuisineId: ${'$'}cuisineId\n    enabledFeatures: ${'$'}enabledFeatures\n    prices: ${'$'}prices\n    minRating: ${'$'}minRating\n    search: ${'$'}search\n    userLat: ${'$'}userLat\n    userLong: ${'$'}userLong\n    limit: ${'$'}limit\n    offset: ${'$'}offset\n  ) {\n    edges {\n      ...rxFields\n      __typename\n    }\n    totalRows\n    __typename\n  }\n}\n\nfragment rxFields on RestaurantType {\n  guid\n  name\n  slug\n  images {\n    main {\n      ...imageFields\n      __typename\n    }\n    bg {\n      ...imageFields\n      __typename\n    }\n    consumer {\n      ...imageFields\n      __typename\n    }\n    banner {\n      ...imageFields\n      __typename\n    }\n    __typename\n  }\n  config {\n    giftCardsEnabled\n    onlineOrderingEnabled\n    optEnabled\n    scanToPayEnabled\n    tdsEnabled\n    __typename\n  }\n  links {\n    onlineOrderingURL\n    rxWebsiteURL\n    __typename\n  }\n  meta\n  popularItems {\n    ...menuItemFields\n    __typename\n  }\n  locations {\n    address\n    city\n    state\n    tlCity {\n      msa {\n        id\n        __typename\n      }\n      __typename\n    }\n    __typename\n  }\n  __typename\n}\n\nfragment imageFields on ImageType {\n  id\n  cdnHost\n  src\n  srcSet\n  alt\n  __typename\n}\n\nfragment menuItemFields on MenuItemType {\n  guid\n  name\n  description\n  image {\n    ...imageFields\n    __typename\n  }\n  basePrice\n  avgRating\n  numRatings\n  isPopular\n  dishes {\n    ...dishFields\n    __typename\n  }\n  __typename\n}\n\nfragment dishFields on DishType {\n  id\n  name\n  singularName\n  slug\n  __typename\n}\n",
           "variables":{"limit":300,"offset":$offset,"userLat":${it.lat},"userLong":${it.long},"enabledFeatures":[]}}
        """

        val request = HttpPost("https://bff-production.nv5.toast.ventures/")
        request.addHeader("Content-Type", "application/json")
        request.entity = StringEntity(
            graphQLQuery,
            ContentType.APPLICATION_JSON
        );
        return RestaurantHttpRequest("$it:$offset", request, "location")
    }


    var totalcurrent = 0
    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        println("RES=" + responseBody)
        val json = JSONObject(responseBody)
        return if (json.has("data")) {
            val count = json.optJSONObject("data").optJSONObject("restaurants").optJSONArray("edges")?.length() ?: 0
            totalcurrent += count
            println("$count new restaurants ! // Total : $totalcurrent")
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }

    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {

        val restaurantList = mutableListOf<Restaurant>()
        val content = restaurantDb.data

        val json = JSONObject(content)
        var restaurantArray = json.getJSONObject("data").optJSONObject("restaurants").getJSONArray("edges")

        if (restaurantArray == null) {
            println("No Locations for this record")
            return restaurantList
        }
        for (i in 0 until restaurantArray.length()) {
            restaurantList.add(restaurantArray.getJSONObject(i).toRestaurant(restaurantDb))
        }
        return restaurantList
    }

    private fun JSONObject.toRestaurant(restaurantDb: RestaurantDb): Restaurant {
        val json = this

        val addresses = json.optJSONArray("locations")

        val address = addresses.optJSONObject(0)
        val slug = json.optString("slug")
        val url = "https://www.toasttab.com/$slug/v3"
        return Restaurant(
            name = json.optString("name"),
            id = json.optString("guid"),
            datetime = restaurantDb.datetime,
            extra1 = slug,
            url = url,
            extra2 = json.optJSONObject("meta")?.optString("description")?: "",
            contactPhone = address.optString("phone"),
            address1 = address?.optString("address") ?: "",
            town = address?.optString("city") ?: "",
            state = address?.optString("state") ?: "",
            latitude = address.optString("latitude"),
            longitude = address.optString("longitude"),
            pos = "Toast"
        )

    }
}