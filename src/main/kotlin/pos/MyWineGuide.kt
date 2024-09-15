package pos


import model.Restaurant
import org.apache.http.Header
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import org.json.JSONObject
import pos.common.HttpPos
import pos.common.RestaurantHttpRequest
import pos.common.ScraperUtils
import resolver.location.LatLng
import resolver.location.LocationGenerator

class MyWineGuide : HttpPos("mywineguide", 10, 40) {


    private fun generateRequest(loc: LatLng): RestaurantHttpRequest {

        val request =
            HttpGet("https://platform.mywineguide.com/api/v2/places?auth_token=5a5d74fc-2f7a-47de-8656-c30d6913d6a7&position=${loc.lat},${loc.long}&dist=200.010975020511395&types=Restaurant,Retail,Online&appver=3.1.1.76")

        return RestaurantHttpRequest(loc.toString(), request, "loc",loc.name)
    }


    private var totalcurrent = 0
    override fun initRequestsQueue() {
        val meters = 600_000
        val distanceBetweenPoints = 100_000
        val points = mutableListOf<LatLng>()

        points.addAll(
            LocationGenerator.generateLocations(
                LatLng(40.7246, -74.0019, "New York"),
                meters,
                distanceBetweenPoints
            )
        )
        points.addAll(LocationGenerator.generateLocations(LatLng(33.7490, -84.3880, "Atlanta"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(40.6782, -73.9442, "Brooklyn"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(41.9209, -73.9613, "Hudson Valley"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(34.8526, -82.3940, "Greenville"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(39.9526, -75.1652, "Philadelphia"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(36.1627, -86.7816, "Nashville"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(42.3601, -71.0589, "Boston"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(43.6591, -70.2568, "Portland"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(38.9072, -77.0369, "DC"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(32.7765, -79.9311, "Charleston"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(25.7617, -80.1918, "Miami"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(27.9506, -82.4572, "Tampa"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(28.5384, -81.3789, "Orlando"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(30.3322, -81.6557, "Jacksonville"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(41.8781, -87.6298, "Chicago"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(39.2904, -76.6122, "Baltimore"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(35.7796, -78.6382, "Raleigh"), meters,distanceBetweenPoints))
        points.addAll(LocationGenerator.generateLocations(LatLng(35.2271, -80.8431, "Charlotte"), meters,distanceBetweenPoints))

        points
            .map { generateRequest(it) }
            .map { restaurantRequestQueue.add(it) }

        println("Total id to extract = ${restaurantRequestQueue.size}")
    }

    override fun determineResponseStatus(
        request: RestaurantHttpRequest,
        responseBody: String?,
        httpCode: Int,
        headers: Array<Header>
    ): RestaurantStatus {

        val json = JSONArray(responseBody)
        return if (json.length() > 0) {
            totalcurrent += json.length()
            println("${json.length()} new restaurant ! // Total : $totalcurrent")
            RestaurantStatus.FOUND
        } else {
            RestaurantStatus.UNAVAILABLE
        }

    }


    override fun convertRawDataToRestaurant(restaurantDb: RestaurantDb): List<Restaurant> {

        val restaurantList = mutableListOf<Restaurant>()
        val content = restaurantDb.data
        val jsonArray = JSONArray(content)
        if (jsonArray != null) {
            for (i in 0 until jsonArray.length()) {
                var restaurantJson = jsonArray.getJSONObject(i)
                restaurantList.add(restaurantJson.toRestaurant(restaurantDb));
            }
        }
        return restaurantList
    }

    private fun JSONObject.toRestaurant( restaurantDb: RestaurantDb): Restaurant {
        val json = this
        return Restaurant(
            name = json.optString("name"),
            id = json.optString("id"),
            datetime = restaurantDb.datetime,
            state=restaurantDb.extra,
            address1 = json.optString("google_address"),
            googleId = json.optString("google_place"),
            latitude =json.optString("latitude"),
            longitude = json.optString("longitude"),
            maps = ScraperUtils.getGoogleMapsUrlByPlaceId(json.optString("google_place")),
            extra1 = json.optString("type"),
            extra2 = json.optString("display_note"),
            pos = "MyWineGuide"
        )

    }
}