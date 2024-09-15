package resolver.location

data class LatLng(val lat: Double, val long: Double, val name:String) {
    override fun toString(): String {
        return "$lat $long $name"
    }
}