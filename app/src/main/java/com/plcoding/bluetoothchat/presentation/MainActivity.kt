package com.plcoding.bluetoothchat.presentation

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.plcoding.bluetoothchat.presentation.components.ChatScreen
import com.plcoding.bluetoothchat.presentation.components.DeviceScreen
import com.plcoding.bluetoothchat.ui.theme.BluetoothChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val bluetoothManager by lazy {
        applicationContext.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = ViewModelProvider(this)[BluetoothViewModel::class.java]

        val enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { /* Not needed */ }

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            val canEnableBluetooth = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms[Manifest.permission.BLUETOOTH_CONNECT] == true
            } else true

            if(canEnableBluetooth && !isBluetoothEnabled) {
                enableBluetoothLauncher.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )
            }
        }

        val discoverableLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_CANCELED) {
                viewModel.waitForIncomingConnections()
            }
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
             permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }

        setContent {
            BluetoothChatTheme {
                val state by viewModel.state.collectAsState()

                LaunchedEffect(key1 = state.errorMessage) {
                    state.errorMessage?.let { message ->
                        Toast.makeText(
                            applicationContext,
                            message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LaunchedEffect(key1 = state.isConnected) {
                    if(state.isConnected) {
                        Toast.makeText(
                            applicationContext,
                            "You're connected!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                Surface(
                    color = MaterialTheme.colors.background
                ) {
                    when {
                        state.isConnecting -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator()
                                    Text(text = "Connecting...")
                                }
                                IconButton(
                                    onClick = viewModel::disconnectFromDevice,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Disconnect"
                                    )
                                }
                            }
                        }
                        state.isConnected -> {
                            ChatScreen(
                                state = state,
                                onDisconnect = viewModel::disconnectFromDevice,
                                onSendMessage = viewModel::sendMessage
                            )
                        }
                        else -> {
                            DeviceScreen(
                                state = state,
                                onStartScan = viewModel::startScan,
                                onStopScan = viewModel::stopScan,
                                onDeviceClick = viewModel::connectToDevice,
                                onStartServer = {
                                    discoverableLauncher.launch(
                                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}