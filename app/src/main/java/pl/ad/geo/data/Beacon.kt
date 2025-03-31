package pl.ad.geo.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Beacon (
    val id: Int,
    val longitude: Double,
    val latitude: Double,
    val numberOnFloor: Int,
    val beaconUid: String?,
    val floorId: Int,
    val buildingShortName: String?,
    val roomPlaced: Boolean,
    val nearFloorChange: Boolean,
    val txPowerToSet: Int
) : Parcelable