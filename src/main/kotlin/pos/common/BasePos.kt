package pos.common

import csv.CSV_FORMAT
import csv.RestaurantWriter
import model.Restaurant
import pos.PosDatabase
import pos.RestaurantDb
import java.io.File

abstract class BasePos(val posName: String) {
    val basePath = File("data/$posName")
    private val dbPath = "$basePath/$posName.sqlite"
    var database: PosDatabase
    val retryError = true

    init {
        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            File("default.sqlite").copyTo(dbFile)
        }
        database = PosDatabase(dbPath)

    }


    fun buildCSV(format: CSV_FORMAT = CSV_FORMAT.FULL) {

        val writer = RestaurantWriter(File(basePath, "$posName-${format.name.lowercase()}.csv"), format)

        var count = 0
        var dupe = 0
        var insertedId = mutableSetOf<String>()
        var restaurants = database.getFoundRestaurants().flatMap { rest ->
            try {
                convertRawDataToRestaurant(rest)
            } catch (e: Exception) {
                println("Unable to read transform information for ${rest.id} : ${rest.data}")
                e.printStackTrace()
                emptyList()
            }
        }.distinctBy { it.name.trim().lowercase() + " " + it.postcode }
            .forEach {
                if (!insertedId.contains(it.id)) {
                    insertedId.add(it.id)
                    writer.appendRestaurant(it)
                    count++

                } else {
                    dupe++
                }
            }



    writer.close()
    println("Finish export : $count lines")
}

abstract fun convertRawDataToRestaurant(rawData: RestaurantDb): List<Restaurant>

abstract fun start()


}