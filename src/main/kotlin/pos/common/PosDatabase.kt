package pos

import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.Table
import org.ktorm.schema.text
import org.ktorm.schema.varchar
import resolver.AhrefsItem
import resolver.RestaurantIdentifier
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PosDatabase(dbPath: String) {


    private var database = Database.connect("jdbc:sqlite:$dbPath")

    fun getRestaurantStatus(id: String): String? {
        return database
            .from(RawRestaurant)
            .select(RawRestaurant.status)
            .where { (RawRestaurant.id eq id) }
            .map { it[RawRestaurant.status] }.firstOrNull()
    }

    fun restaurantExistIntoDb(id: String): Boolean {
        var status = getRestaurantStatus(id)
        return status != null
    }

    fun restaurantMustBeCreatedOrUpdated(id: String): Boolean {
        try {
            var status = getRestaurantStatus(id)

            return status == null || status == RestaurantStatus.ERROR.name
        } catch (e: Exception) {
            e.printStackTrace()
            return true
        }
    }

    fun saveRestaurant(
        id: String,
        status: RestaurantStatus,
        source: String,
        data: String?,
        extra: String? = null,
        force: Boolean = false
    ) {
        var restaurantStatus = getRestaurantStatus(id)

        if (restaurantStatus != null && (force || restaurantStatus != RestaurantStatus.FOUND.name)) {

            this.database.update(RawRestaurant)
            {
                set(it.date, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                set(it.status, status.name)
                set(it.data, data)
                set(it.extra, extra)
                set(it.source, source)
                where {
                    it.id eq id
                }
            }
        } else if (restaurantStatus == null)
            this.database.insert(RawRestaurant)
            {
                set(it.id, id)
                set(it.date, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                set(it.status, status.name)
                set(it.data, data)
                set(it.extra, extra)
                set(it.source, source)
            }
    }

    fun getFoundRestaurants(): List<RestaurantDb> {
        return this.database.from(RawRestaurant)
            .select()
            .where { RawRestaurant.status eq RestaurantStatus.FOUND.name }
            .map {
                RestaurantDb(
                    it[RawRestaurant.id]!!,
                    it[RawRestaurant.date]!!,
                    it[RawRestaurant.data]!!,
                    it[RawRestaurant.extra] ?: "",
                    it[RawRestaurant.source] ?: ""
                )
            }
    }

    fun idsMustBeResolved(sourceName: String, type: String): Boolean {
        try {
            return database
                .from(ResolvedId)
                .select(ResolvedId.restaurant_id)
                .where { (ResolvedId.source_name eq sourceName) and (ResolvedId.id_type eq type) }
                .totalRecords <= 0
        } catch (e: Exception) {
            e.printStackTrace()
            return true
        }
    }

    fun saveResolvedId(ahRefSource: AhrefsItem, restaurantId: RestaurantIdentifier): Boolean {
        if (!isIdResolved(restaurantId.id, restaurantId.type)) {
            this.database.insert(ResolvedId)
            {
                set(it.source_url, ahRefSource.sourceUrl)
                set(it.source_name, ahRefSource.sourceName)
                set(it.date, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                set(it.pos_url, ahRefSource.posUrl)
                set(it.restaurant_id, restaurantId.id)
                set(it.id_type, restaurantId.type)
                set(it.resolved_from, restaurantId.source)
            }
            return true
        }
        return false
    }

    fun saveResolvedId(sourceName: String, restaurantId: RestaurantIdentifier) {
        if (!isIdResolved(restaurantId.id, restaurantId.type)) {
            this.database.insert(ResolvedId)
            {
                set(it.date, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                set(it.restaurant_id, restaurantId.id)
                set(it.id_type, restaurantId.type)
                set(it.resolved_from, restaurantId.source)
                set(it.extra, restaurantId.extra)
                set(it.source_name, sourceName)

            }
        }
    }

    private fun isIdResolved(id: String, type: String): Boolean {
        return database
            .from(ResolvedId)
            .select(ResolvedId.restaurant_id)
            .where { (ResolvedId.restaurant_id eq id) and (ResolvedId.id_type eq type) }
            .totalRecords > 0
    }

    public fun isSourceAlreadyResolved(source: String): Boolean {
        return database
            .from(ResolvedId)
            .select(ResolvedId.resolved_from)
            .where { (ResolvedId.resolved_from eq source) }
            .totalRecords > 0
    }

    fun getResolvedIds(): List<ResolvedIdDb> {
        return this.database.from(ResolvedId)
            .select()
            .map {
                ResolvedIdDb(
                    it[ResolvedId.restaurant_id]!!,
                    it[ResolvedId.resolved_from] ?: "",
                    it[ResolvedId.id_type] ?: "",
                    it[ResolvedId.source_name] ?: "",
                    it[ResolvedId.extra] ?: ""
                )
            }
    }

    fun getResolvedIdsCount(): Int {
        return this.database.from(ResolvedId)
            .select().totalRecords
    }

    fun deleteResolvedId(id: String): Boolean {
        return this.database.delete(ResolvedId) { it.restaurant_id eq id } > 0
    }

    fun getLatestResolvedDate(): String? {
        return this.database.from(ResolvedId)
            .select()
            .orderBy(ResolvedId.date.desc())
            .map { it[ResolvedId.date] ?: "" }
            .firstOrNull()
    }
}


enum class RestaurantStatus {
    FOUND,
    UNAVAILABLE,
    ERROR,
    RETRY
}

object ResolvedId : Table<Nothing>("resolved_id") {
    val date = varchar("date")
    val source_url = varchar("source_url")
    val source_name = varchar("source_name")
    val pos_url = varchar("pos_url")
    val restaurant_id = varchar("restaurant_id").primaryKey()
    val id_type = varchar("id_type").primaryKey()
    val resolved_from = varchar("resolved_from")
    val extra = varchar("extra")
}


data class ResolvedIdDb(val id: String, val source: String, val type: String, val sourceName: String, val extra: String)

data class RestaurantDb(val id: String, val datetime: String, val data: String, val extra: String, val source: String)

object RawRestaurant : Table<Nothing>("raw_restaurant") {
    val id = varchar("id").primaryKey()
    val date = varchar("date")
    val status = varchar("status")
    val data = text("raw_data")
    val extra = varchar("extra")
    val source = varchar("source")
}

