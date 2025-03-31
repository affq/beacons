package pl.ad.geo.common.receivers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager

class ConnectivityStateReceiver(
    private val context: Context,
    private val connectionReadyCallback: () -> Unit,
    private val connectionNotReadyCallback: () -> Unit
): BroadcastReceiver() {
    private var gpsOn = false
    private var bluetoothOn = false

    init {
        initializeConnectionState()
    }

    private fun initializeConnectionState() {
        gpsOn = (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
            ?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false

        bluetoothOn = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter?.isEnabled ?: false
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.action?.let { action ->
            when (action) {
                LocationManager.PROVIDERS_CHANGED_ACTION -> {
                    gpsOn = (context?.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
                        ?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    bluetoothOn = when (intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )) {
                        BluetoothAdapter.STATE_ON -> true
                        else -> false
                    }
                }
            }

            if (gpsOn && bluetoothOn) connectionReadyCallback()
            else connectionNotReadyCallback()
        }
    }
}