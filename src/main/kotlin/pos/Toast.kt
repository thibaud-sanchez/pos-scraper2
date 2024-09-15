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

class Toast : HttpPos("toast",useProxy = false) {

    override fun initRequestsQueue() {

        val meters = 20_000
        val points = mutableListOf<LatLng>()

    //    points.addAll(LocationGenerator.generateLocations(LatLng(40.2824, -80.0413, "Large shot"), meters,50000))
//        points.addAll(LocationGenerator.generateLocations(LatLng(40.7246, -74.0019, "New York"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(33.7490, -84.3880, "Atlanta"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(40.6782, -73.9442, "Brooklyn"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(41.9209, -73.9613, "Hudson Valley"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(34.8526, -82.3940, "Greenville"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(39.9526, -75.1652, "Philadelphia"), meters))
        points.addAll(LocationGenerator.generateLocations(LatLng(51.49512, -0.12779, "London"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(42.3601, -71.0589, "Boston"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(43.6591, -70.2568, "Portland"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(38.9072, -77.0369, "DC"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(32.7765, -79.9311, "Charleston"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(25.7617, -80.1918, "Miami"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(27.9506, -82.4572, "Tampa"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(28.5384, -81.3789, "Orlando"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(30.3322, -81.6557, "Jacksonville"), meters))
      //  points.addAll(LocationGenerator.generateLocations(LatLng(41.8781, -87.6298, "Chicago"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(39.2904, -76.6122, "Baltimore"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(35.7796, -78.6382, "Raleigh"), meters))
//        points.addAll(LocationGenerator.generateLocations(LatLng(35.2271, -80.8431, "Charlotte"), meters))

        points
            .map { generateToastRequest(it) }
            .map { restaurantRequestQueue.add(it) }

    }

    private fun generateToastRequest(it: LatLng): RestaurantHttpRequest {
        val graphQLQuery = """ {
              "query" : "query getNearbyRestaurants(${'$'}input: NearbyRestaurantsInput!) {\n  nearbyRestaurants(input: ${'$'}input) {\n    __typename\n    ...NearbyRestaurantDetail\n  }\n}\nfragment NearbyRestaurantDetail on Restaurant {\n  __typename\n  guid\n  name\n  imageUrl\n  description\n  cuisineType\n  shortUrl\n  location {\n    __typename\n    address1\n    address2\n    city\n    state\n    zip\n    phone\n    latitude\n    longitude\n  }\n}",
              "variables" : {
                "input" : {
                  "radius" : 2,
                  "fulfillmentDateTime" : "2023-06-19T21:25:17-05:00",
             "longitude" :${it.long},
                  "latitude" :${it.lat},
                  "diningOption" : "TAKE_OUT"
                }
              },
              "operationName" : "getNearbyRestaurants"
            }
        """

        val request = HttpPost("https://ws-api.toasttab.com/consumer-app-bff/v1/graphql")
        request.addHeader("apollographql-client-name", " com.toasttab.consumer-apollo-ios")
        request.addHeader("User-Agent", "Toast%20TakeOut/281 CFNetwork/1312 Darwin/21 .0.0\"")
        request.addHeader("X-APOLLO-OPERATION-TYPE", "query")
        request.addHeader("X-APOLLO-OPERATION-NAME", "getNearbyRestaurants")
        request.addHeader("apollographql-client-version", "1.59-281")
        request.addHeader("Content-Type", "application/json")
        request.entity = StringEntity(
            graphQLQuery,
            ContentType.APPLICATION_JSON
        );
        return RestaurantHttpRequest(it.toString(), request, "location")
    }


    var totalcurrent = 0
    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {
        println("RES="+responseBody)
        val json = JSONObject(responseBody)
        return if (json.has("data")) {
            val count = json.optJSONObject("data").optJSONArray("nearbyRestaurants")?.length() ?: 0
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
        var restaurantArray = json.getJSONObject("data").getJSONArray("nearbyRestaurants")

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

        val address = json.optJSONObject("location")

        val address1 = address.optString("address1") + " " + json.optString("address2")
        val slug = json.optString("shortUrl")
        val url = "https://www.toasttab.com/$slug"
        return Restaurant(
            name = json.optString("name"),
            id = json.optString("guid"),
            datetime = restaurantDb.datetime,
            url = url,
            contactPhone = address.optString("phone"),
            address1 = address1,
            town = address.optString("city"),
            postcode = address.optString("zip"),
            state = address.optString("state"),
            latitude = address.optString("latitude"),
            longitude = address.optString("longitude"),
            extra1 = StringUtils.abbreviate(json.optString("description"), 128),
            pos = "Toast"
        )

    }
}