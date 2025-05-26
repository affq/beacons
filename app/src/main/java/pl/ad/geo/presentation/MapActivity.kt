package pl.ad.geo.presentation

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.geometry.Point
import com.arcgismaps.geometry.SpatialReference
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.BasemapStyle
import com.arcgismaps.mapping.Viewpoint
import com.arcgismaps.mapping.symbology.SimpleMarkerSceneSymbol
import com.arcgismaps.mapping.symbology.SimpleMarkerSymbol
import com.arcgismaps.mapping.symbology.SimpleMarkerSymbolStyle
import com.arcgismaps.mapping.view.GraphicsOverlay
import com.arcgismaps.mapping.view.MapView
import com.arcgismaps.mapping.view.Graphic
import pl.ad.geo.R

class MapActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private lateinit var mapView : MapView
    private val defaultSpatialRef = SpatialReference(2180)

    companion object {
        const val START_LONGITUDE = 21.01178
        const val START_LATITUDE = 52.22977
        const val START_SCALE = 1000.00
    }

    private fun createMap(): ArcGISMap {
        return ArcGISMap(BasemapStyle.ArcGISTopographic).apply {
            initialViewpoint = Viewpoint(
                latitude = START_LATITUDE,
                longitude = START_LONGITUDE,
                scale = START_SCALE
            )
        }
    }

    private fun projectPoint(
        latitude: Double,
        longitude: Double,
        sourceRef: SpatialReference = SpatialReference.wgs84(),
        destRef: SpatialReference = defaultSpatialRef): Point?
    {
        val sourcePoint = Point(longitude, latitude, sourceRef)
        return GeometryEngine.projectOrNull(sourcePoint, destRef)
    }

    fun MapView.showMarker(
        latitude: Double,
        longitude: Double
    ) {
        this.graphicsOverlays.clear()
        val overlay = GraphicsOverlay()
        this.graphicsOverlays.add(overlay)
        val point = projectPoint(latitude, longitude) ?: return

        val symbol = SimpleMarkerSymbol(
            style = SimpleMarkerSymbolStyle.Circle,
            color = com.arcgismaps.Color.red,
            size = 10.0f
        )

        val graphic = Graphic(point, symbol)
        overlay.graphics.add(graphic)
        this.setViewpoint(Viewpoint(point, START_SCALE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.map)
        mapView.map = createMap()

        val startPositioningButton = findViewById<Button>(R.id.start_positioning)

        //val intent = Intent(this, MonitoringActivity::class.java)
       // val currentCenter = map.getMapCenter()
       // val currentZoomLevel = map.getZoomLevel()

            /*
        intent.putExtra("EXTRA_START_LATITUDE", currentCenter.latitude)
        intent.putExtra("EXTRA_START_LONGITUDE", currentCenter.longitude)
        intent.putExtra("EXTRA_START_ZOOM", currentZoomLevel)

        Log.d("MapActivity_Debug", "WYSYŁANE -> Lat: ${currentCenter.latitude}, Lon: ${currentCenter.longitude}, Zoom: $currentZoomLevel")

             */
        startPositioningButton.setOnClickListener {
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        //mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        //mapView.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsToRequest = ArrayList<String>()
        var i = 0
        while (i < grantResults.size) {
            permissionsToRequest.add(permissions[i])
            i++
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }
}