package pl.ad.geo.presentation

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import pl.ad.geo.R
import pl.ad.geo.data.Beacon

class MonitoringActivity : AppCompatActivity() {

    private lateinit var beaconManager: BeaconManager
    private lateinit var region: Region

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitoring)

        val referenceBeacons = intent
            .getParcelableArrayListExtra<Beacon>("REFERENCE_BEACONS")
            ?.associateBy { it.id } ?: emptyMap()

        setUpBeaconManager()

        val stopScanningButton = findViewById<Button>(R.id.stop_scanning_button)
        stopScanningButton.setOnClickListener {
            stopMonitoringBeacons()
            findViewById<TextView>(R.id.monitoring_status).text = getString(R.string.scanning_stopped)
        }
    }

    private fun setUpBeaconManager() {
        beaconManager = BeaconManager.getInstanceForApplication(this)

        listOf(
            BeaconParser.EDDYSTONE_UID_LAYOUT,
            BeaconParser.EDDYSTONE_TLM_LAYOUT,
            BeaconParser.EDDYSTONE_URL_LAYOUT
        ).forEach { layout ->
            beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(layout))
        }

        region = Region("all-beacons-region", null, null, null)

        startMonitoringBeacons()
    }

    private fun startMonitoringBeacons() {
        beaconManager.addRangeNotifier { beacons, _ ->
            val beaconInfoTextView = findViewById<TextView>(R.id.beacon_info)
            if (beacons.isNotEmpty()) {
                val firstBeacon = beacons.first()
                runOnUiThread {
                    beaconInfoTextView.text = getString(R.string.beacon_detected, firstBeacon.id1)
                }
                Log.d("MonitoringActivity", "Wykryto beacon ID1=${firstBeacon.id1}")
            } else {
                runOnUiThread {
                    beaconInfoTextView.text = getString(R.string.no_beacons_detected)
                }
            }
        }

        try {
            beaconManager.startRangingBeacons(region)
            findViewById<TextView>(R.id.monitoring_status).text = getString(R.string.monitoring_started)
        } catch (e: Exception) {
            Log.e("MonitoringActivity", "Błąd podczas rozpoczynania monitorowania", e)
            findViewById<TextView>(R.id.monitoring_status).text = getString(R.string.monitoring_error)
        }
    }

    private fun stopMonitoringBeacons() {
        try {
            beaconManager.stopRangingBeacons(region)
            Log.d("MonitoringActivity", "Zatrzymano monitorowanie.")
        } catch (e: Exception) {
            Log.e("MonitoringActivity", "Błąd podczas zatrzymywania monitorowania", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoringBeacons()
    }
}