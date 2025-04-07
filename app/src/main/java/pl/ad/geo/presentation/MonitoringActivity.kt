package pl.ad.geo.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import pl.ad.geo.R
import pl.ad.geo.common.utils.JsonUtils
import pl.ad.geo.common.utils.PositioningUtils
import pl.ad.geo.common.utils.PositioningUtilsImpl
import pl.ad.geo.data.model.ReferenceBeacon
import androidx.preference.PreferenceManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.overlay.Marker
import pl.ad.geo.data.model.UserPosition


class MonitoringActivity : AppCompatActivity() {

    private lateinit var beaconManager: BeaconManager
    private lateinit var region: Region
    private lateinit var positionTextView: TextView

    private val DEFAULT_LONGITUDE = 21.01178
    private val DEFAULT_LATITUDE = 52.22977
    private val DEFAULT_ZOOM = 15.0
    private lateinit var mapView: MapView

    private var userPositionMarker: Marker? = null
    private var followUserMode = true

    private val positioningUtils: PositioningUtils = PositioningUtilsImpl()

    private val referenceBeacons: Map<String, ReferenceBeacon> by lazy {
        JsonUtils.loadBeacons(this).also {
            Log.d("RefBeaconLoad", "Wczytano ${it.size} beaconów referencyjnych")
            if (it.isEmpty()) {
                Log.w("RefBeaconLoad", "Brak beaconów referencyjnych. Pozycjonowanie nie będzie działać.")
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) allGranted = false
            }

            if (allGranted) {
                Log.d("Permissions", "Wszystkie wymagane uprawnienia zostały przyznane.")
                startMonitoringBeacons()
            } else {
                Log.e("Permissions", "Nie przyznano wszystkich wymaganych uprawnień.")
                showPermissionDeniedDialog()
            }
        }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>()
        requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            }
        }


        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("Permissions", "Prośba o uprawnienia: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            Log.d("Permissions", "Wszystkie wymagane uprawnienia już są przyznane.")
            startMonitoringBeacons()
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Brak uprawnień")
            .setMessage("Aplikacja wymaga uprawnień Bluetooth i Lokalizacji do działania. Bez nich pozycjonowanie nie będzie możliwe.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))

        setContentView(R.layout.activity_monitoring)

        mapView = findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)


        val intent = intent
        val startLatitude = intent.getDoubleExtra("EXTRA_START_LATITUDE", DEFAULT_LATITUDE)
        val startLongitude = intent.getDoubleExtra("EXTRA_START_LONGITUDE", DEFAULT_LONGITUDE)
        val startZoom = intent.getDoubleExtra("EXTRA_START_ZOOM", DEFAULT_ZOOM)

        setupInitialMapState(startLatitude, startLongitude, startZoom)

        positionTextView = findViewById(R.id.positionTextView)

        setUpBeaconManager()

        val stopScanningButton = findViewById<Button>(R.id.stop_positioning)
        stopScanningButton.setOnClickListener {
            stopMonitoringBeacons()
        }

        checkAndRequestPermissions()
    }

    private fun setupInitialMapState(lat: Double, lon: Double, zoom: Double) {
        mapView.post {
            try {
                val mapController = mapView.controller
                val startPoint = GeoPoint(lat, lon)

                //mapController.setZoom(zoom)
                //mapController.setCenter(startPoint)

                mapController.setZoom(DEFAULT_ZOOM)
                mapController.setCenter(GeoPoint(DEFAULT_LATITUDE,DEFAULT_LONGITUDE))

                mapView.invalidate()

                Log.d("MonitoringActivity", "Pomyślnie ustawiono stan mapy.")

            } catch (e: Exception) {
                Log.e("MonitoringActivity", "Błąd podczas ustawiania stanu mapy", e)
                val mapController = mapView.controller
                mapController?.setZoom(DEFAULT_ZOOM)
                mapController?.setCenter(GeoPoint(DEFAULT_LATITUDE,DEFAULT_LONGITUDE))
                mapView.invalidate()
            }
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

    }

    private fun startMonitoringBeacons() {
        beaconManager.removeAllRangeNotifiers()

        beaconManager.addRangeNotifier { beacons, _ ->
            Log.d("MonitoringActivity", "Wykryto ${beacons.size} beaconów.")
            calculateAndDisplayPosition(beacons.toList())
        }

        try {
            beaconManager.startRangingBeacons(region)
            Log.d("MonitoringActivity", "Rozpoczęto monitorowanie beaconów.")
            runOnUiThread {
                positionTextView.text = "Rozpoczęto skanowanie..."
            }
        } catch (e: SecurityException) {
            Log.e("MonitoringActivity", "Błąd uprawnień podczas rozpoczynania monitorowania!", e)
            showPermissionDeniedDialog()
        }
        catch (e: Exception) {
            Log.e("MonitoringActivity", "Błąd podczas rozpoczynania monitorowania", e)
            runOnUiThread {
                positionTextView.text = "Błąd startu skanowania"
            }
        }
    }

    private fun calculateAndDisplayPosition(detectedBeacons: List<Beacon>) {
        lifecycleScope.launch {
            Log.d("PositionCalc", "Rozpoczynam obliczanie pozycji dla ${detectedBeacons.size} beaconów.")
            var userPosition: UserPosition? = positioningUtils.determineUserPosition(detectedBeacons, referenceBeacons)

            //userPosition = UserPosition(52.22977, 21.01178)

            if (userPosition != null) {
                val positionText = "Lat: ${"%.6f".format(userPosition.latitude)}\nLon: ${"%.6f".format(userPosition.longitude)}"
                positionTextView.text = positionText
                Log.d("PositionCalc", "Obliczona pozycja: $positionText")

                val userGeoPoint = GeoPoint(userPosition.latitude, userPosition.longitude)

                if (userPositionMarker == null) {
                    userPositionMarker = Marker(mapView)
                    userPositionMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    userPositionMarker?.title = "Twoja pozycja"


                    // userPositionMarker?.icon = ContextCompat.getDrawable(this@MonitoringActivity, R.drawable.ic_my_location_marker)
                    userPositionMarker?.icon = ContextCompat.getDrawable(this@MonitoringActivity, R.drawable.baseline_directions_bus_24)


                    mapView.overlays.add(userPositionMarker)
                    Log.d("MarkerLogic", "Stworzono i dodano marker pozycji użytkownika.")
                }

                userPositionMarker?.position = userGeoPoint
                userPositionMarker?.setVisible(true)

                if (followUserMode) {
                    mapView.controller.animateTo(userGeoPoint)
                    // mapView.controller.setCenter(userGeoPoint)
                }

            } else {
                positionTextView.text = "Nie można obliczyć pozycji"
                Log.d("PositionCalc", "Nie udało się obliczyć pozycji (brak pasujących beaconów?).")

                if (userPositionMarker != null) {
                    mapView.overlays.remove(userPositionMarker)
                    userPositionMarker = null
                    Log.d("MarkerLogic", "Usunięto marker pozycji użytkownika (pozycja nieznana).")
                }
            }

            mapView.invalidate()
        }
    }

    private fun stopMonitoringBeacons() {
        try {
            if (beaconManager.rangedRegions.contains(region)) {
                beaconManager.stopRangingBeacons(region)
                beaconManager.removeAllRangeNotifiers()
                Log.d("MonitoringActivity", "Zatrzymano monitorowanie dla regionu: $region")
                runOnUiThread {
                    positionTextView.text = "Skanowanie zatrzymane."
                }
            } else {
                Log.d("MonitoringActivity", "Monitorowanie dla regionu $region nie było aktywne.")
            }
        } catch (e: Exception) {
            Log.e("MonitoringActivity", "Błąd podczas zatrzymywania monitorowania", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MonitoringActivity", "onDestroy: Zatrzymywanie monitorowania.")
        stopMonitoringBeacons()
    }

    override fun onPause() {
        super.onPause()
        Log.d("MonitoringActivity", "onPause: Zatrzymywanie monitorowania.")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MonitoringActivity", "onResume: Sprawdzanie uprawnień i potencjalne wznowienie monitorowania.")
    }
}