package com.example.bluetoothreconnectionbug

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.DeadObjectException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.bluetooth.BluetoothLe
import androidx.bluetooth.ScanFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bluetoothreconnectionbug.ui.theme.BluetoothReconnectionBugTheme
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {

    private var connectionState by mutableStateOf<ConnectionState>(ConnectionState.Idle)
    private var targetDeviceAddress: String? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BluetoothReconnectionBugTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StatusView(state = connectionState, modifier = Modifier.padding(innerPadding))
                }
            }
        }

        // When using State.RESUMED, if while the app is open, you toggle Bluetooth off and on,
        //  the app will enter the PAUSED state in case of Samsung devices where a bottom sheet
        //  type of screen is overlaid on top of the app. Thus the lib will try to stop a connection
        //  that was already stopped when toggling Bluetooth off, leading to the DeadObjectException.
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                connectToDeviceViaGoogle(
                    applicationContext = applicationContext,
                    updateState = { newState ->
                        Timber.d("Connection state updated: $newState")
                        connectionState = newState
                    },
                    reconnection = targetDeviceAddress != null
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Timber.d("MainActivity onPause")
    }

    override fun onResume() {
        super.onResume()
        Timber.d("MainActivity onResume")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    suspend fun connectToDeviceViaGoogle(
        applicationContext: Context,
        updateState: (ConnectionState) -> Unit,
        reconnection: Boolean,
    ) {
        if (reconnection) {
            Timber.d("Reconnecting to device with address: $targetDeviceAddress")
            updateState(ConnectionState.Reconnecting)
        } else {
            Timber.d("Starting new connection")
            updateState(ConnectionState.Scanning)
        }
        try {
            val scanFilter = ScanFilter(
                manufacturerId = 1529,
            )

            val bluetoothLe = BluetoothLe(context = applicationContext)
            updateState(ConnectionState.Scanning)

            bluetoothLe
                .scan(listOf(scanFilter))
                .first { scanResult ->
                    Timber.d("startScanning found device ${scanResult.device.name}")

                    if (scanResult.isConnectable()) {
                        if (scanResult.deviceAddress.address == "F4:37:B6:19:89:AC") {
                            Timber.d("connectToDeviceViaGoogle found our device")
                            targetDeviceAddress = scanResult.deviceAddress.address
                            if (reconnection) {
                                updateState(ConnectionState.Reconnecting)
                            } else {
                                updateState(ConnectionState.Connecting)
                            }
                            bluetoothLe.connectGatt(scanResult.device) {
                                if (reconnection) {
                                    updateState(ConnectionState.Reconnected)
                                } else {
                                    updateState(ConnectionState.Connected)
                                }

                                awaitCancellation()
                            }
                        } else {
                            Timber.d("connectToDeviceViaGoogle found other device")
                            false
                        }
                    } else {
                        false
                    }
                }
        } catch (_: SecurityException) {
            updateState(ConnectionState.SecurityException)
            Timber.d("connectToDeviceViaGoogle SecurityException")
        } catch (_: TimeoutCancellationException) {
            updateState(ConnectionState.Timeout)
            Timber.d("connectToDeviceViaGoogle TimeoutCancellationException")
        } catch (_: CancellationException) {
            updateState(ConnectionState.Cancelled)
            Timber.d("connectToDeviceViaGoogle CancellationException")
        } catch (exception: Exception) {
            updateState(ConnectionState.Exception)
            Timber.d("connectToDeviceViaGoogle Exception $exception")
        }
    }
}

// Connection states
sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Scanning : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object Cancelled : ConnectionState()
    data object Reconnecting : ConnectionState()
    data object Reconnected : ConnectionState()
    data object Timeout : ConnectionState()
    data object Exception : ConnectionState()
    data object SecurityException : ConnectionState()
}

@Composable
private fun StatusView(state: ConnectionState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            ConnectionState.Idle -> Text("Idle")
            ConnectionState.Scanning -> Text("Scanning...")
            ConnectionState.Connecting -> Text("Connecting...")
            ConnectionState.Connected -> Text("Connected")
            ConnectionState.Cancelled -> Text("Cancelled")
            ConnectionState.Reconnecting -> Text("Reconnecting...")
            ConnectionState.Reconnected -> Text("Reconnected")
            ConnectionState.Timeout -> Text("Timeout")
            ConnectionState.Exception -> Text("Exception occurred")
            ConnectionState.SecurityException -> Text("Security Exception")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BluetoothReconnectionBugTheme {
        StatusView(
            state = ConnectionState.Scanning,
            modifier = Modifier.padding(16.dp)
        )
    }
}