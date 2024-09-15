import pos.common.ScraperUtils
import resolver.location.LatLng
import resolver.location.LocationGenerator
import java.io.File

class BuildMapsList {
    fun start() {
        savepoints(LatLng(41.875562,-87.624421,"Chicago"),30)
        savepoints(LatLng(39.952724,-75.163526,"Philadelphie"),20)
        savepoints(LatLng(40.712728,-74.006015,"New York"),30 )
        savepoints(LatLng( 38.895037,-77.036543,"Washington"),15 )

    }

    private fun savepoints(latlng: LatLng, diamKm:Int) {
        val town = latlng.name
        val distance = 1500
        val points = LocationGenerator.generateLocations(
            latlng,
            diamKm*1000,
            distance
        )

        println("total points: ${points.size}")
        val file = File("./data/maps/$town-$diamKm-$distance.csv")
        if (file.exists()) file.delete()
        file.createNewFile()

        points.forEach {
            val url = ScraperUtils.getGoogleMapsRestaurantUrlByLatitudeLongitude(it.lat.toString(), it.long.toString())
            file.appendText("$town;${it.lat};${it.long};$url\n");
        }
    }
}