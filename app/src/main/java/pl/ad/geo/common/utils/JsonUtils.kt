package pl.ad.geo.common.utils

import android.content.Context
import android.util.Log
import pl.ad.geo.data.model.ReferenceBeacon
import org.json.JSONObject

object JsonUtils {
    fun loadBeacons(context: Context, fileName: String = "beacons.json"): Map<Int, ReferenceBeacon> {
        return try {
            context.assets.open(fileName).use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                parseBeaconJson(jsonString)
            }
        } catch (e: Exception) {
            Log.e("JsonUtils", "Błąd podczas wczytywania beaconów", e)
            emptyMap()
        }
    }

    private fun parseBeaconJson(jsonString: String): Map<Int, ReferenceBeacon> {
        val json = JSONObject(jsonString)
        val beaconsArray = json.getJSONArray("items")
        return (0 until beaconsArray.length()).associate { i ->
            val beaconJson = beaconsArray.getJSONObject(i)
            ReferenceBeacon(
                id = beaconJson.getInt("id"),
                longitude = beaconJson.getDouble("longitude"),
                latitude = beaconJson.getDouble("latitude"),
                numberOnFloor = beaconJson.getInt("numberOnFloor"),
                beaconUid = beaconJson.optString("beaconUid"),
                floorId = beaconJson.getInt("floorId"),
                buildingShortName = beaconJson.optString("buildingShortName"),
                roomPlaced = beaconJson.getBoolean("roomPlaced"),
                nearFloorChange = beaconJson.getBoolean("nearFloorChange"),
                txPowerToSet = beaconJson.getInt("txPowerToSet")
            ).let { it.id to it }
        }
    }
}
