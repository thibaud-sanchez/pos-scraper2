package resolver.location

import kotlin.math.pow
import kotlin.math.roundToInt

class LocationGenerator {
    companion object {
        fun Double.roundTo(numFractionDigits: Int): Double {
            val factor = 10.0.pow(numFractionDigits.toDouble())
            return (this * factor).roundToInt() / factor
        }


        fun generateLocations(
            centerPoint: LatLng,
            surfaceMeters: Int,
            distanceBetweenPointsMeters: Int = 9000
        ): List<LatLng> {

            var coef = 0.0000089;
            var metersIncrement = distanceBetweenPointsMeters * coef;

            var start = LatLng(
                (centerPoint.lat - (surfaceMeters / 2 * coef)).roundTo(5),
                (centerPoint.long - (surfaceMeters / 2 * coef)).roundTo(5),
                centerPoint.name
            )
            var end = LatLng(
                (centerPoint.lat + (surfaceMeters / 2 * coef)).roundTo(5),
                (centerPoint.long + (surfaceMeters / 2 * coef)).roundTo(5),
                centerPoint.name
            )

            val listOfPoint = mutableListOf<LatLng>()
            var currentPoint = start
            do {
                do {
                    val newLong = (currentPoint.long + metersIncrement).roundTo(5)
                    currentPoint = LatLng(currentPoint.lat, newLong, currentPoint.name)
                    listOfPoint.add(currentPoint)

                } while (currentPoint.long <= end.long)

                currentPoint = LatLng((currentPoint.lat + metersIncrement).roundTo(5), start.long, centerPoint.name)
            } while (currentPoint.lat <= end.lat)

            println("total locations = ${listOfPoint.size}")
            return listOfPoint
        }
    }
}