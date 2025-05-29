package pl.ad.geo.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.arcgismaps.data.ServiceFeatureTable
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.layers.FeatureLayer
import pl.ad.geo.R
import pl.ad.geo.common.receivers.ConnectivityStateReceiver

class MainActivity : AppCompatActivity() {

    private lateinit var connectivityReceiver: ConnectivityStateReceiver

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.any { !it.value }) {
            Toast.makeText(
                this,
                "Nie przyznano wymaganych uprawnień. Aplikacja może działać nieprawidłowo.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            checkHardwareState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeConnectivityReceiver()
        requestRequiredPermissions()

        findViewById<Button>(R.id.btnStartMonitoring).setOnClickListener {
            startMapActivity(MapMode.MONITORING)
        }

        findViewById<Button>(R.id.btnStartComparison).setOnClickListener {
            startMapActivity(MapMode.COMPARISON)
        }
    }

    private fun startMapActivity(mode: MapMode) {
        val intent = Intent(this, MapActivity::class.java).apply {
            putExtra("map_mode", mode.name)
        }
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            connectivityReceiver,
            IntentFilter().apply {
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(connectivityReceiver)
    }

    private fun initializeConnectivityReceiver() {
        connectivityReceiver = ConnectivityStateReceiver(
            this,
            connectionReadyCallback = { showConnectionReadyToast() },
            connectionNotReadyCallback = { showConnectionWarning() }
        )
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()

        if (allPermissionsGranted(permissions)) {
            checkHardwareState()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun allPermissionsGranted(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkHardwareState() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        when {
            !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                showLocationEnableDialog()

            !bluetoothManager.adapter.isEnabled ->
                showBluetoothEnableDialog()

            else -> showConnectionReadyToast()
        }
    }

    private fun showLocationEnableDialog() {
        AlertDialog.Builder(this)
            .setTitle("Włącz lokalizację")
            .setMessage("Aby wykrywać beacony, wymagana jest aktywna usługa lokalizacji.")
            .setPositiveButton("Ustawienia") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Anuluj") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showBluetoothEnableDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Włącz Bluetooth")
            .setMessage("Aby wykrywać beacony, wymagane jest włączone Bluetooth.")
            .setPositiveButton("Włącz") { _, _ ->
                try {
                    startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (e: SecurityException) {
                    Toast.makeText(
                        this,
                        "Brak wymaganych uprawnień Bluetooth.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Anuluj") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showConnectionWarning() {
        Toast.makeText(
            this,
            "Wymagane usługi lokalizacji i Bluetooth!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showConnectionReadyToast() {
        Toast.makeText(this, "Wszystko gotowe! Możesz rozpocząć pracę z mapą.", Toast.LENGTH_SHORT).show()
    }
}
