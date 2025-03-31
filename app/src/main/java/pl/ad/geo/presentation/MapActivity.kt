package pl.ad.geo.presentation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import pl.ad.geo.R

import org.osmdroid.views.MapView

class MapActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private lateinit var map : MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_map)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.WIKIMEDIA)
    }

    override fun onResume() {
        super.onResume()
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume() //needed for compass, my location overlays, v6.0.0 and up
    }

    override fun onPause() {
        super.onPause()
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause()  //needed for compass, my location overlays, v6.0.0 and up
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

    /*private fun requestPermissionsIfNecessary(String[] permissions) {
    val permissionsToRequest = ArrayList<String>();
    permissions.forEach { permission ->
    if (ContextCompat.checkSelfPermission(this, permission)
            != PackageManager.PERMISSION_GRANTED) {
        // Permission is not granted
        permissionsToRequest.add(permission);
    }
}
    if (permissionsToRequest.size() > 0) {
        ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toArray(new String[0]),
                REQUEST_PERMISSIONS_REQUEST_CODE);
    }
}*/
}