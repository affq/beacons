package pl.ad.geo.presentation

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import pl.ad.geo.R

import org.osmdroid.views.MapView

class MapActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private lateinit var map : MapView

    companion object {
        const val START_LONGITUDE = 21.01178
        const val START_LATITUDE = 52.22977
        const val START_ZOOM = 15
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_map)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        map.controller.setZoom(START_ZOOM)
        val startPoint = org.osmdroid.util.GeoPoint(START_LATITUDE, START_LONGITUDE)
        map.controller.setCenter(startPoint)
        map.setMultiTouchControls(true)

        val startPositioningButton = findViewById<Button>(R.id.start_positioning)

        val intent = Intent(this, MonitoringActivity::class.java)
        val currentCenter = map.getMapCenter()
        val currentZoomLevel = map.getZoomLevel()

        intent.putExtra("EXTRA_START_LATITUDE", currentCenter.latitude)
        intent.putExtra("EXTRA_START_LONGITUDE", currentCenter.longitude)
        intent.putExtra("EXTRA_START_ZOOM", currentZoomLevel)

        Log.d("MapActivity_Debug", "WYSYŁANE -> Lat: ${currentCenter.latitude}, Lon: ${currentCenter.longitude}, Zoom: $currentZoomLevel") // <--- WAŻNY LOG

        startPositioningButton.setOnClickListener {
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
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