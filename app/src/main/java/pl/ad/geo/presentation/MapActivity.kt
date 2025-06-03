package pl.ad.geo.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arcgismaps.ApiKey
import com.arcgismaps.Color
import com.arcgismaps.geometry.*
import com.arcgismaps.mapping.*
import com.arcgismaps.mapping.symbology.*
import com.arcgismaps.mapping.view.*
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.data.ServiceFeatureTable
import com.arcgismaps.mapping.layers.FeatureLayer
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import pl.ad.geo.R
import pl.ad.geo.common.utils.JsonUtils
import pl.ad.geo.common.utils.PositioningUtils
import pl.ad.geo.common.utils.PositioningUtilsImpl
import pl.ad.geo.data.model.PositionSample
import pl.ad.geo.data.model.ReferenceBeacon
import pl.ad.geo.data.model.UserPosition


class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var positionTextView: TextView

    private val requestCodePermissions = 101
    private var userPositionGraphic: Graphic? = null

    private val API_KEY = ""

    private lateinit var beaconManager: BeaconManager
    private lateinit var region: Region
    private val positioningUtils: PositioningUtils = PositioningUtilsImpl()

    private var currentGpsLat: Double? = null
    private var currentGpsLon: Double? = null
    private var currentBeaconLat: Double? = null
    private var currentBeaconLon: Double? = null

    private val comparisonSamples = mutableListOf<PositionSample>()
    private var isComparisonRunning = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val referenceBeacons: Map<String, ReferenceBeacon> by lazy {
        JsonUtils.loadBeacons(this).also {
            Log.d("RefBeaconLoad", "Wczytano ${it.size} beaconów referencyjnych")
        }
    }

    companion object {
        const val START_LONGITUDE = 21.0098999
        const val START_LATITUDE = 52.220656
        const val START_SCALE = 2000.0
        val defaultSpatialRef = SpatialReference(2180)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        ArcGISEnvironment.apiKey = ApiKey.create(API_KEY)

        mapView = findViewById(R.id.map)
        mapView.map = createMap()
        mapView.onCreate(this)

        positionTextView = findViewById(R.id.positionTextView)

        checkPermissions()

        val modeName = intent.getStringExtra("map_mode") ?: "MONITORING"
        val mode = MapMode.valueOf(modeName)

        when (mode) {
            MapMode.MONITORING -> startMonitoringMode()
            MapMode.COMPARISON -> startComparisonMode()
            MapMode.NONE -> TODO()
        }
    }

    private fun getBaseLayers(): List<FeatureLayer> {
        val poiLayersUrl = "https://arcgis.cenagis.edu.pl/server/rest/services/SION2_Topo_MV/sion_topo_POI_style_EN/FeatureServer/"
        val indoorLayersUrl = "https://arcgis.cenagis.edu.pl/server/rest/services/SION2_Topo_MV/sion2_topo_indoor_all/MapServer/"

        val layerUrls = listOf(
            "${indoorLayersUrl}6",
            "${indoorLayersUrl}5",
            "${indoorLayersUrl}4",
            "${indoorLayersUrl}3",
            "${indoorLayersUrl}2",
            "${indoorLayersUrl}1",
            "${indoorLayersUrl}0",
            "${poiLayersUrl}28",
            "${poiLayersUrl}24"
        )

        return layerUrls.map {
            val featureTable = ServiceFeatureTable(it)
            FeatureLayer.createWithFeatureTable(featureTable)
        }
    }

    private fun createMap(): ArcGISMap {
        val map = ArcGISMap(BasemapStyle.ArcGISNewspaper).apply {
            initialViewpoint = Viewpoint(
                latitude = START_LATITUDE,
                longitude = START_LONGITUDE,
                scale = START_SCALE
            )
        }
        val featureLayers = getBaseLayers()
        map.operationalLayers.addAll(featureLayers)
        return map
    }

    private fun startMonitoringMode() {
        Toast.makeText(this, "Tryb: Monitorowanie beaconów", Toast.LENGTH_SHORT).show()
        positionTextView.text = "Oczekiwanie na dane beaconów..."

        setupBeaconManager()
        startMonitoringBeacons()
    }

    private fun setupBeaconManager() {
        beaconManager = BeaconManager.getInstanceForApplication(this)
        listOf(
            BeaconParser.EDDYSTONE_UID_LAYOUT,
            BeaconParser.EDDYSTONE_TLM_LAYOUT,
            BeaconParser.EDDYSTONE_URL_LAYOUT
        ).forEach {
            beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(it))
        }
        region = Region("all-beacons-region", null, null, null)
    }

    private fun startMonitoringBeacons() {
        beaconManager.removeAllRangeNotifiers()

        beaconManager.addRangeNotifier { beacons, _ ->
            calculateAndDisplayPosition(beacons.toList())
        }

        try {
            beaconManager.startRangingBeacons(region)
            runOnUiThread {
                positionTextView.text = "Rozpoczęto skanowanie..."
            }
        } catch (e: SecurityException) {
            runOnUiThread {
                positionTextView.text = "Brak uprawnień Bluetooth/Lokalizacji"
            }
        } catch (e: Exception) {
            runOnUiThread {
                positionTextView.text = "Błąd podczas uruchamiania monitorowania"
            }
        }
    }

    private fun calculateAndDisplayPosition(beacons: List<Beacon>) {
        lifecycleScope.launch {
            val userPosition: UserPosition? =
                positioningUtils.determineUserPosition(beacons, referenceBeacons)

            if (userPosition != null) {
                currentBeaconLat = userPosition.latitude
                currentBeaconLon = userPosition.longitude

                val text = "Lat: ${"%.6f".format(userPosition.latitude)}, Lon: ${"%.6f".format(userPosition.longitude)}"

                runOnUiThread {
                    positionTextView.text = text
                    updateUserMarker(userPosition.latitude, userPosition.longitude)
                    Log.d("COMPARE", "✅ Zaktualizowano pozycję z beaconów: $text")
                }
            } else {
                runOnUiThread {
                    Log.d("COMPARE", "⚠️ Brak pozycji z beaconów w tej iteracji – używamy poprzednich.")
                }
            }

        }
    }


    private fun updateUserMarker(lat: Double, lon: Double) {
        val overlay = mapView.graphicsOverlays.firstOrNull() ?: GraphicsOverlay().also {
            mapView.graphicsOverlays.add(it)
        }

        val projected = projectPoint(lat, lon) ?: return
        if (userPositionGraphic == null) {
            val symbol = SimpleMarkerSymbol(
                style = SimpleMarkerSymbolStyle.Circle,
                color = Color.red,
                size = 10f
            )
            userPositionGraphic = Graphic(projected, symbol)
            overlay.graphics.add(userPositionGraphic!!)
        } else {
            userPositionGraphic?.geometry = projected
        }

        mapView.setViewpoint(Viewpoint(projected, START_SCALE))
    }

    private fun removeUserMarker() {
        userPositionGraphic?.let {
            mapView.graphicsOverlays.firstOrNull()?.graphics?.remove(it)
            userPositionGraphic = null
        }
    }

    private fun stopMonitoring() {
        try {
            if (::beaconManager.isInitialized && beaconManager.rangedRegions.contains(region)) {
                beaconManager.stopRangingBeacons(region)
                beaconManager.removeAllRangeNotifiers()
                Log.d("Beacon", "Zatrzymano monitorowanie.")
            }
        } catch (e: Exception) {
            Log.e("Beacon", "Błąd podczas zatrzymywania monitorowania", e)
        }
    }

    private fun startComparisonMode() {
        Toast.makeText(this, "Tryb: Porównanie lokalizacji GPS vs Beacon", Toast.LENGTH_SHORT).show()
        positionTextView.text = "Zbieranie danych przez 30 sekund..."

        setupBeaconManager()
        setupGpsListener()

        isComparisonRunning = true
        startMonitoringBeacons()

        lifecycleScope.launch {
            repeat(60) {
                if (!isComparisonRunning) return@launch
                delay(1000)

                comparisonSamples.add(
                    PositionSample(
                        timestamp = System.currentTimeMillis(),
                        gpsLat = currentGpsLat,
                        gpsLon = currentGpsLon,
                        beaconLat = currentBeaconLat,
                        beaconLon = currentBeaconLon
                    )
                )
            }

            stopMonitoring()
            stopGpsListener()
            Log.d("Comparison", "Comparison samples: $comparisonSamples")
            isComparisonRunning = false
            positionTextView.text = "Analiza danych..."

            analyzeComparisonResults()
        }
    }

    private fun setupGpsListener() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation ?: return
                currentGpsLat = loc.latitude
                currentGpsLon = loc.longitude
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    private fun analyzeComparisonResults() {
        val wgs84 = SpatialReference.wgs84()

        val distances = comparisonSamples.mapNotNull {
            val gpsPt = if (it.gpsLat != null && it.gpsLon != null)
                Point(it.gpsLon, it.gpsLat, wgs84) else null
            val beaconPt = if (it.beaconLat != null && it.beaconLon != null)
                Point(it.beaconLon, it.beaconLat, wgs84) else null

            if (gpsPt != null && beaconPt != null) {
                val dist = GeometryEngine.distanceOrNull(gpsPt, beaconPt) ?: return@mapNotNull null
                Triple<Point, Point, Double>(gpsPt, beaconPt, dist)
            } else null
        }

        if (distances.isEmpty()) {
            positionTextView.text = "Nie zebrano żadnych pozycji z beaconów – brak danych do porównania."
            Toast.makeText(this, "Brak danych beaconów – analiza niemożliwa.", Toast.LENGTH_LONG).show()
            return
        }

        val top5 = distances.sortedByDescending { it.third }.take(5)
        //TODO: ogarnąć, żeby te same pary się nie wyświetlały 2 razy (bo wtedy wygląda jakby było mniej punktów)
        Log.d("COMPARE", "Liczba próbek do analizy: ${distances.size}")
        val overlay = GraphicsOverlay()
        mapView.graphicsOverlays.clear()
        mapView.graphicsOverlays.add(overlay)

        for ((gps, beacon, dist) in top5) {
            val gpsSymbol = SimpleMarkerSymbol(SimpleMarkerSymbolStyle.Diamond, Color.cyan, 10f)
            val beaconSymbol = SimpleMarkerSymbol(SimpleMarkerSymbolStyle.Circle, Color.red, 10f)

            val gpsGraphic = Graphic(projectPoint(gps.y, gps.x) ?: gps, gpsSymbol)
            val beaconGraphic = Graphic(projectPoint(beacon.y, beacon.x) ?: beacon, beaconSymbol)

            val line = PolylineBuilder(wgs84).apply {
                addPoint(gps)
                addPoint(beacon)
            }.toGeometry()

            val lineSymbol = SimpleLineSymbol(SimpleLineSymbolStyle.Solid, Color.black, 2f)
            val lineGraphic = Graphic(line, lineSymbol)

            overlay.graphics.addAll(listOf(gpsGraphic, beaconGraphic, lineGraphic))
        }

        positionTextView.text = "Top 5 różnic zaznaczone na mapie."
    }


    private fun stopGpsListener() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun projectPoint(
        latitude: Double,
        longitude: Double,
        sourceRef: SpatialReference = SpatialReference.wgs84(),
        destRef: SpatialReference = defaultSpatialRef
    ): Point? {
        val sourcePoint = Point(longitude, latitude, sourceRef)
        return GeometryEngine.projectOrNull(sourcePoint, destRef)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), requestCodePermissions)
        }
    }

    fun MapView.showMarker(latitude: Double, longitude: Double) {
        this.graphicsOverlays.clear()
        val overlay = GraphicsOverlay()
        this.graphicsOverlays.add(overlay)

        val point = projectPoint(latitude, longitude) ?: return
        val symbol = SimpleMarkerSymbol(
            style = SimpleMarkerSymbolStyle.Circle,
            color = Color.red,
            size = 10.0f
        )
        overlay.graphics.add(Graphic(point, symbol))
        this.setViewpoint(Viewpoint(point, START_SCALE))
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy(this)
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePermissions && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "Brak wymaganych uprawnień!", Toast.LENGTH_SHORT).show()
        }
    }
}
