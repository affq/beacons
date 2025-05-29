package pl.ad.geo.data.model

data class PositionSample(
    val timestamp: Long,
    val gpsLat: Double?,
    val gpsLon: Double?,
    val beaconLat: Double?,
    val beaconLon: Double?
)

