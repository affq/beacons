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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.os.Looper
import android.location.Location
import java.util.concurrent.TimeUnit
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import pl.ad.geo.data.model.LocationComparisonPoint


class MonitoringActivity : AppCompatActivity() {

    private lateinit var beaconManager: BeaconManager
    private lateinit var region: Region
    private lateinit var positionTextView: TextView

    private val DEFAULT_LONGITUDE = 21.01178
    private val DEFAULT_LATITUDE = 52.22977
    private val DEFAULT_ZOOM = 15.0
    private lateinit var mapView: MapView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var isTracking = false
    private var lastGpsPosition: UserPosition? = null
    private val locationData = mutableListOf<LocationComparisonPoint>()

    private lateinit var resultsTextView: TextView
    // private lateinit var resultsRecyclerView: RecyclerView

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
                if ((it.key == Manifest.permission.ACCESS_FINE_LOCATION ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.key == Manifest.permission.BLUETOOTH_SCAN))
                    && !it.value) {
                    allGranted = false
                }
            }

            if (allGranted) {
                Log.d("Permissions", "Wszystkie wymagane uprawnienia zostały przyznane.")
                resultsTextView.text = "Uprawnienia przyznane. Naciśnij Start."
            } else {
                Log.e("Permissions", "Nie przyznano wszystkich wymaganych uprawnień.")
                showPermissionDeniedDialog()
            }
        }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>()
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("Permissions", "Prośba o uprawnienia: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            Log.d("Permissions", "Wszystkie wymagane uprawnienia już są przyznane.")
            resultsTextView.text = "Uprawnienia przyznane. Naciśnij start."
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationRequest()
        setupLocationCallback()

        val toggleTrackingButton = findViewById<Button>(R.id.stop_positioning)//TODO: zmień
        toggleTrackingButton.text = "Start śledzenia"
        toggleTrackingButton.setOnClickListener {
            if (isTracking){
                stopTracking()
            } else {
                startTracking()
            }
        }

        resultsTextView = findViewById(R.id.positionTextView)
        resultsTextView.text = "Naciśnij Start, by rozpocząć śledzenie."

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
            val beaconPosition: UserPosition? = positioningUtils.determineUserPosition(detectedBeacons, referenceBeacons)

            updateBeaconMarkerAndText(beaconPosition)

            if (isTracking && beaconPosition != null) {
                val currentGpsPosition = lastGpsPosition
                val timestamp = System.currentTimeMillis()
                val comparisonPoint = LocationComparisonPoint(
                    timestamp = timestamp,
                    beaconPosition = beaconPosition,
                    gpsPosition = currentGpsPosition
                )
                locationData.add(comparisonPoint)
                Log.d("DataCapture", "Zapisano punkt: Beacon (${"%.5f".format(beaconPosition.latitude)}, ${"%.5f".format(beaconPosition.longitude)}), GPS (${currentGpsPosition?.latitude?.let { "%.5f".format(it) } ?: "null"}, ${currentGpsPosition?.longitude?.let { "%.5f".format(it) } ?: "null"})")

                resultsTextView.text = "Śledzenie aktywne. Zebrano ${locationData.size} punktów."
            } else if (isTracking && beaconPosition == null) {
                Log.d("DataCapture", "Nie zapisano punktu - brak pozycji z beaconów.")
            }
        }
    }

    private fun updateBeaconMarkerAndText(beaconPosition: UserPosition?) {
        runOnUiThread {
            if (beaconPosition != null) {
                val positionText = "Beacon: Lat: ${"%.6f".format(beaconPosition.latitude)}\nLon: ${"%.6f".format(beaconPosition.longitude)}"
                // positionTextView.text = positionText
                Log.d("PositionCalc", "Obliczona pozycja beacon: $positionText")

                val userGeoPoint = GeoPoint(beaconPosition.latitude, beaconPosition.longitude)

                if (userPositionMarker == null) {
                    userPositionMarker = Marker(mapView)
                    userPositionMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    userPositionMarker?.title = "Twoja pozycja (Beacon)"
                    userPositionMarker?.icon = ContextCompat.getDrawable(this@MonitoringActivity, R.drawable.baseline_directions_bus_24)
                    mapView.overlays.add(userPositionMarker)
                    Log.d("MarkerLogic", "Stworzono i dodano marker pozycji użytkownika (Beacon).")
                }

                userPositionMarker?.position = userGeoPoint
                userPositionMarker?.setVisible(true)

                if (followUserMode) {
                    mapView.controller.animateTo(userGeoPoint)
                }
            } else {
                // positionTextView.text = "Nie można obliczyć pozycji z beaconów"
                Log.d("PositionCalc", "Nie udało się obliczyć pozycji z beaconów.")
                userPositionMarker?.setVisible(false)
                // if (userPositionMarker != null) {
                //     mapView.overlays.remove(userPositionMarker)
                //     userPositionMarker = null
                //     Log.d("MarkerLogic", "Usunięto marker pozycji użytkownika (Beacon) (pozycja nieznana).")
                // }
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

    private fun setupLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(5)) //TODO: bez hardkodu
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(2))
            .setMaxUpdateDelayMillis(TimeUnit.SECONDS.toMillis(10))
            .build()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {location ->
                    Log.d("GPS_UPDATE", "Otrzymano lokalizację GPS: Lat: ${location.latitude}, Lon: ${location.longitude}")
                    lastGpsPosition = UserPosition(location.latitude, location.longitude)

                    updateGpsMarker(lastGpsPosition)

                     if (isTracking) {
                         locationData.add(LocationComparisonPoint(System.currentTimeMillis(), null, lastGpsPosition))
                     }
                } ?: run {
                    Log.w("GPS_Update", "Otrzymano pustą lokalizację GPS.")
                }
            }
        }
    }

    private fun startGpsUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                Log.d("GPS_Update", "Rozpoczęto nasłuchiwanie lokalizacji GPS.")
                resultsTextView.text = "Rozpoczęto śledzenie...\nZbieranie danych z Beacon i GPS."
            } catch (e: SecurityException) {
                Log.e("GPS_Update", "Błąd uprawnień przy starcie GPS!", e)
                resultsTextView.text = "Błąd uprawnień GPS!"
            }
        } else {
            Log.e("GPS_Update", "Próba startu GPS bez uprawnień!")
            resultsTextView.text = "Brak uprawnień GPS!"
        }
    }

    private fun stopGpsUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("GPS_Update", "Zatrzymano nasłuchiwanie lokalizacji GPS.")
        lastGpsPosition = null
        removeGpsMarker()
    }

    private var gpsPositionMarker: Marker? = null
    private fun updateGpsMarker(position: UserPosition?) {
        position ?: return
        val gpsGeoPoint = GeoPoint(position.latitude, position.longitude)

        if (gpsPositionMarker == null) {
            gpsPositionMarker = Marker(mapView)
            gpsPositionMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            gpsPositionMarker?.title = "Pozycja GPS"
            gpsPositionMarker?.icon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_mylocation)
            mapView.overlays.add(gpsPositionMarker)
            Log.d("MarkerLogic", "Stworzono i dodano marker pozycji GPS.")
        }
        gpsPositionMarker?.position = gpsGeoPoint
        gpsPositionMarker?.setVisible(true)

        if (followUserMode) {
            mapView.controller.animateTo(gpsGeoPoint)
        }

        mapView.invalidate()
    }

    private fun removeGpsMarker() {
        gpsPositionMarker?.let {
            mapView.overlays.remove(it)
            gpsPositionMarker = null
            mapView.invalidate()
            Log.d("MarkerLogic", "Usunięto marker pozycji GPS.")
        }
    }

    private fun startTracking() {
        if (!allPermissionsGranted()) {
            Log.w("Tracking", "Próba startu bez uprawnień!")
            resultsTextView.text = "Brak potrzebnych uprawnień!"
            checkAndRequestPermissions()
            return
        }

        Log.d("Tracking", "Rozpoczynanie śledzenia...")
        isTracking = true
        locationData.clear()
        lastGpsPosition = null
        findViewById<Button>(R.id.stop_positioning).text = "Stop śledzenia"

        resultsTextView.text = "Rozpoczęto śledzenie...\n Zbieranie danych z Beacon i GPS."
        removeGpsMarker()
        userPositionMarker?.let { mapView.overlays.remove(it) }
        userPositionMarker = null

        startMonitoringBeacons()
        startGpsUpdates()

        mapView.invalidate()
    }

    private fun stopTracking() {
        Log.d("Tracking", "Zatrzymywanie śledzenia...")
        isTracking = false
        findViewById<Button>(R.id.stop_positioning).text = "Start śledzenia"

        stopMonitoringBeacons()
        stopGpsUpdates()

        processAndDisplayResults()
    }

    private fun processAndDisplayResults() {
        Log.d("Results", "Rozpoczynam przetwarzanie ${locationData.size} punktów.")
        if (locationData.isEmpty()) {
            resultsTextView.text = "Zakończono śledzenie. Nie zebrano żadnych danych."
            return
        }

        locationData.forEach { point ->
            if (point.beaconPosition != null && point.gpsPosition != null) {
                point.differenceMeters = calculateDistance(point.beaconPosition, point.gpsPosition)
            } else {
                point.differenceMeters = null
            }
        }

        val validPoints = locationData.filter { it.differenceMeters != null }
        val sortedPoints = validPoints.sortedByDescending { it.differenceMeters }

        Log.d("Results", "Posortowano ${sortedPoints.size} ważnych punktów.")

        if (sortedPoints.isEmpty()) {
            resultsTextView.text = "Zakończono śledzenie.\nZebrano ${locationData.size} punktów, ale w żadnym nie było jednocześnie pozycji Beacon i GPS."
        } else {
            val topN = 10 // TODO: hardkod
            val resultText = StringBuilder("Zakończono śledzenie. Największe różnice:\n\n")
            sortedPoints.take(topN).forEachIndexed { index, point ->
                resultText.append("${index + 1}. ${point.toString()}\n\n")
            }
            if (sortedPoints.size > topN) {
                resultText.append("... (więcej wyników w logach)\n")
            }
            resultsTextView.text = resultText.toString()

            Log.d("Results", "--- Pełna lista posortowanych różnic ---")
            sortedPoints.forEach { Log.d("Results", it.toString()) }
            Log.d("Results", "--- Koniec listy ---")
        }

        // removeGpsMarker()
        // userPositionMarker?.setVisible(false)
        mapView.invalidate()
    }

    private fun calculateDistance(pos1: UserPosition, pos2: UserPosition): Double {
        val location1 = Location("beacon")
        location1.latitude = pos1.latitude
        location1.longitude = pos1.longitude

        val location2 = Location("gps")
        location2.latitude = pos2.latitude
        location2.longitude = pos2.longitude

        return location1.distanceTo(location2).toDouble()
    }

    private fun allPermissionsGranted() : Boolean {
        val requiredPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MonitoringActivity", "onDestroy: Zatrzymywanie wszystkiego.")
        if (isTracking) {
            stopTracking()
        } else {
            stopMonitoringBeacons()
            stopGpsUpdates()
        }
        mapView.onDetach()
    }

    override fun onPause() {
        super.onPause()
        Log.d("MonitoringActivity", "onPause: Zatrzymywanie GPS i potencjalnie Beaconów.")
        stopGpsUpdates()
        if (isTracking) {
            Log.w("MonitoringActivity", "Aplikacja pauzowana podczas śledzenia. Zatrzymuję Beacony.")
            stopMonitoringBeacons()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MonitoringActivity", "onResume: Sprawdzanie uprawnień.")
        if (isTracking && !allPermissionsGranted()) {
            Log.e("MonitoringActivity", "Wznowiono bez uprawnień! Zatrzymuję śledzenie.")
            stopTracking()
            resultsTextView.text = "Brak uprawnień po wznowieniu. Śledzenie zatrzymane."
        } else if (isTracking) {
            Log.d("MonitoringActivity", "Wznawianie nasłuchiwania GPS i Beacon...")
            startGpsUpdates()
            startMonitoringBeacons()
            resultsTextView.text = "Wznowiono śledzenie..."
        } else {
            resultsTextView.text = "Gotowy do startu. Naciśnij przycisk."
        }
        mapView.onResume()
    }
}