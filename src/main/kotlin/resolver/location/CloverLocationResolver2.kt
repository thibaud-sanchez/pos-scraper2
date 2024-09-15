package resolver.location

import model.Restaurant
import org.apache.http.client.methods.HttpGet
import org.json.JSONArray
import pos.RestaurantDb
import pos.common.RestaurantHttpRequest
import resolver.HttpResolver
import resolver.RestaurantIdentifier

class CloverLocationResolver2() : HttpResolver("clover") {




    override fun initRequestsQueue() {
        val meters = 200_000
        val diameter = 50000
        val points = mutableListOf<LatLng>()
      //  points.addAll(LocationGenerator.generateLocations(LatLng(48.7048, -122.4671, "Bellingham"), meters,diameter))
        points.addAll(LocationGenerator.generateLocations(LatLng(49.2474, -123.0910, "Vancouver"), meters,diameter))
        points.addAll(LocationGenerator.generateLocations(LatLng(51.0480, -114.0592, "Calgary"), meters,diameter))
        points.addAll(LocationGenerator.generateLocations(LatLng(43.655, -79.3827, "Toronto"), meters,diameter))
        points.addAll(LocationGenerator.generateLocations(LatLng(45.2587, -76.0522, "Ottawa"), meters,diameter))
        points.addAll(LocationGenerator.generateLocations(LatLng(45.8721, -73.6006, "Montreal"), meters,diameter))
        points.addAll(LocationGenerator.generateLocations(LatLng(46.7958, -71.2558, "Quebec"), meters,diameter))
        println("total locations = ${points.size}")

        points
            .map { generateCloverResolveIdRequest(it) }
            .map { restaurantRequestQueue.add(it) }
    }

    private fun generateCloverResolveIdRequest(loc: LatLng) :RestaurantHttpRequest{
        val url = "https://api.perka.com/2/cma/merchant/list/nearby?latitude=${loc.lat}&longitude=${loc.long}"
        return RestaurantHttpRequest(loc.toString(), HttpGet(url), "location")

    }


    override fun saveSuccessResolverResponse(request: RestaurantHttpRequest, responseBody: String?, extra: String) {
        try {
            val jsonArray = JSONArray(responseBody)
            println("Found ${jsonArray.length()} restaurants!")

            for (i in 0 until jsonArray.length()) {

                val rest = jsonArray.getJSONObject(i)

                val idName = rest.getString("cloverMerchantId")
                val name = rest.getString("merchantName")
                var restaurantIdentifier =
                    RestaurantIdentifier(idName, request.type, request.request.uri.toString(), rest.toString())
                database.saveResolvedId(request.id, restaurantIdentifier)

                println("Restaurant #$idName saved ! ($name)")

            }


        } catch (e: Exception) {
            println("Unable to check if id is resolved for restaurant ${request.id} : $responseBody")
            e.printStackTrace()
        }
    }

    private fun getNextRestaurantRequest(): RestaurantHttpRequest {
        return restaurantRequestQueue.remove()
    }

    override fun convertRawDataToRestaurant(rawData: RestaurantDb): List<Restaurant> {
        TODO("Not yet implemented")
    }

    fun getResolvedIds(): List<RestaurantIdentifier> {
        return database.getResolvedIds().map { RestaurantIdentifier(it.id, it.type, it.source, it.extra) }
    }
}