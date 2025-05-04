package pl.ad.geo.data.model

data class LocationComparisonPoint(
    val timestamp: Long,
    val beaconPosition: UserPosition?,
    val gpsPosition: UserPosition?,
    var differenceMeters: Double? = null
) {
    override fun toString(): String {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
        val beaconStr =
            beaconPosition?.let { "Beacon(lat=%.5f, lon=%.5f)".format(it.latitude, it.longitude) }
                ?: "Beacon (brak)"
        val gpsStr =
            gpsPosition?.let { "GPS(lat=%.5f, lon=%.5f)".format(it.latitude, it.longitude) }
                ?: "GPS (brak)"
        val diffStr = differenceMeters?.let { "Różnica: %.1f m".format(it) } ?: "Różnica: -"
        return "$timeStr: $diffStr\n $beaconStr\n $gpsStr"
    }
}