package pos.common

class ScraperUtils {
    companion object {

        fun extractDomain(url: String): String? {
            val domainRegex =
                "^(?:https?:\\/\\/)?(?:[^@\\/\\n]+@)?(?:www\\.)?(?<domain>[^\\/?\\n]+)".toRegex()
            val domainResult = domainRegex.find(url)
            return domainResult?.groups?.get("domain")?.value
        }
        fun getGoogleMapsUrlByPlaceId(placeId:String):String{
            return "https://www.google.com/maps/search/?api=1&query=Eiffel%20Tower&query_place_id=$placeId"
        }

        fun getGoogleMapsUrlByLatitudeLongitude(latitude:String,longitude:String):String{
            return "https://maps.google.com/?q=$latitude,$longitude"
        }


        fun getGoogleMapsRestaurantUrlByLatitudeLongitude(latitude:String,longitude:String):String{
            return "https://www.google.com/maps/search/Restaurants/@$latitude,$longitude,16z/data=!3m1!4b1?entry=ttu"
        }

    }
}