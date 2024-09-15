package csv

import model.Restaurant
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.nio.file.Files

enum class CSV_FORMAT {
    FULL,
    SALESFORCE
}

class RestaurantWriter(private val csvFile: File, private val format: CSV_FORMAT) {

    val writer = Files.newBufferedWriter(csvFile.toPath())

    private val csvPrinter = if (format == CSV_FORMAT.FULL) {
        CSVPrinter(
            writer, CSVFormat.DEFAULT
                .withHeader(
                    "name",
                    "id",
                    "datetime",
                    "groupId",
                    "contactEmail",
                    "contactName",
                    "contactPhone",
                    "contactPhone2",
                    "address1",
                    "address2",
                    "address3",
                    "postcode",
                    "town",
                    "state",
                    "country",
                    "latitude",
                    "longitude",
                    "googleId",
                    "maps",
                    "timezone",
                    "facebook",
                    "url",
                    "urlSource",
                    "pos",
                    "extra1",
                    "extra2"
                )
        )
    } else {
        CSVPrinter(
            writer, CSVFormat.EXCEL
                .withDelimiter(';')
                .withHeader(
                    "Company / Account",
                    "Street",
                    "City",
                    "State/Province",
                    "Zip/Postal Code",
                    "Phone",
                    "POS",
                    "Lead ID",
                    "Date",
                )
        )
    }

    fun flush() = csvPrinter.flush()
    fun close() = csvPrinter.close()
    fun appendRestaurant(restaurant: Restaurant) {
        if (format == CSV_FORMAT.FULL) {
            csvPrinter.printRecord(
                restaurant.name,
                restaurant.id,
                restaurant.datetime,
                restaurant.groupId,
                restaurant.contactEmail,
                restaurant.contactName,
                restaurant.contactPhone,
                restaurant.contactPhone2,
                restaurant.address1,
                restaurant.address2,
                restaurant.address3,
                restaurant.postcode,
                restaurant.town,
                restaurant.state,
                restaurant.country,
                restaurant.latitude,
                restaurant.longitude,
                restaurant.googleId,
                restaurant.maps,
                restaurant.timezone,
                restaurant.facebook,
                restaurant.url,
                restaurant.urlSource,
                restaurant.pos,
                restaurant.extra1,
                restaurant.extra2
            )
        } else {
            csvPrinter.printRecord(
                restaurant.name,
                ( restaurant.address1 + " " + restaurant.address2+ " " + restaurant.address3).trim(),
                restaurant.town,
                restaurant.state,
                restaurant.postcode,
                restaurant.contactPhone,
                restaurant.pos,
                "",
                restaurant.datetime
            )
        }

    }
}