package pl.ad.geo.common.utils

import android.util.Log
import org.altbeacon.beacon.Beacon
import pl.ad.geo.data.model.ReferenceBeacon
import pl.ad.geo.data.model.UserPosition

interface PositioningUtils {
    suspend fun determineUserPosition(
        detectedBeacons: List<Beacon>,
        referenceBeacons: Map<String, ReferenceBeacon>
    ): UserPosition?
}

class PositioningUtilsImpl : PositioningUtils {
    companion object {
        private const val NUM_OF_NEAREST_BEACONS = 5
    }

    override suspend fun determineUserPosition(
        detectedBeacons: List<Beacon>,
        referenceBeacons: Map<String, ReferenceBeacon>
    ): UserPosition? {


        for (beacon in detectedBeacons) {
            Log.d("DETERMINEUSERPOS", "Beacon ID: ${beacon.bluetoothAddress}")
        }

        Log.d("DETERMINEUSERPOS", "Reference beacon keys: ${referenceBeacons.keys}")

        val matchingBeacons: List<Pair<ReferenceBeacon, Beacon>> = detectedBeacons.mapNotNull { detectedBeacon ->
            referenceBeacons[detectedBeacon.bluetoothAddress]?.let { referenceBeacon ->
                referenceBeacon to detectedBeacon
            }
        }

        if (matchingBeacons.isEmpty()) {
            return null
        }

        val nearestBeacons = matchingBeacons
            .sortedBy { it.second.distance }
            .take(NUM_OF_NEAREST_BEACONS)

        var sumLat = 0.0
        var sumLon = 0.0
        var sumWeights = 0.0

        for ((referenceBeacon, detectedBeacon) in nearestBeacons) {
            val weight = 1.0 / maxOf(detectedBeacon.distance, 0.1)

            sumLat += referenceBeacon.latitude * weight
            sumLon += referenceBeacon.longitude * weight
            sumWeights += weight
        }

        if (sumWeights == 0.0) {
            return null
        }

        val userLatitude = sumLat / sumWeights
        val userLongitude = sumLon / sumWeights

        return UserPosition(
            latitude = userLatitude,
            longitude = userLongitude
        )
    }
}