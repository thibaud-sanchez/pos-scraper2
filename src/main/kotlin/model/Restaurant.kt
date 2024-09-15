package model

import pos.common.ScraperUtils

data class Restaurant(
    val name: String,
    val id: String,
    val datetime: String,
    val groupId: String = "",
    val contactEmail: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val contactPhone2: String = "",
    val country: String = "",
    val address1: String = "",
    val address2: String = "",
    val address3: String = "",
    val town: String = "",
    val postcode: String = "",
    val state: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val googleId: String = "",
    var maps: String = "",
    val urlSource: String = "",
    val url: String = "",
    val pos: String = "",
    val facebook: String = "",
    val timezone: String = "",
    val extra1: String = "",
    val extra2: String = "",
) {
    init {
        if (!latitude.isNullOrBlank()
            && !longitude.isNullOrBlank()
            && maps.isNullOrBlank()){
            maps= ScraperUtils.getGoogleMapsUrlByLatitudeLongitude(latitude,longitude)
        }

    }
}