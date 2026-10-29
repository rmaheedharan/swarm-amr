package com.swarmamr.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.swarmamr.app.sensing.PositioningService
import com.swarmamr.app.ui.AccuracyScreen
import com.swarmamr.app.ui.LiveScreen
import com.swarmamr.app.ui.SettingsScreen
import com.swarmamr.app.ui.SurveyScreen
import com.swarmamr.app.ui.SwarmTheme
import com.swarmamr.app.ui.parseMarkerQr
import com.swarmamr.core.Site

class MainActivity : ComponentActivity() {
    private val app get() = application as SwarmApp
    private var lastQr by mutableStateOf<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                PositioningService.start(this)
            } else {
                toast("Location permission is needed to read Wi-Fi.")
            }
        }

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        parseMarkerQr(result.contents)?.let(::onMarkerScanned)
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            app.sites.replace(Site.fromJson(text))
        }.onSuccess { toast("Site imported.") }.onFailure { toast("Not a valid site file: ${it.message}") }
    }

    /** Zebra DataWedge sends scans of QR checkpoints as a broadcast (profile made below). */
    private val dataWedge = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            parseMarkerQr(intent.getStringExtra(DW_DATA))?.let(::onMarkerScanned)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureDataWedge()
        setContent {
            SwarmTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            TABS.forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i, onClick = { tab = i },
                                    icon = { Icon(icon, contentDescription = label) }, label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> LiveScreen(app, onStart = ::startPositioning, onStop = { PositioningService.stop(this@MainActivity) }, onScanQr = ::scanQr)
                            1 -> AccuracyScreen(app, lastQr, onScanQr = ::scanQr)
                            2 -> SurveyScreen(app, lastQr, onScanQr = ::scanQr, onImportSite = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                            else -> SettingsScreen(app)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, dataWedge, IntentFilter(DW_ACTION), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(dataWedge) }
        super.onStop()
    }

    private fun onMarkerScanned(id: String) {
        lastQr = id
        val known = app.sites.current.marker(id) != null
        if (known && app.runtime.running.value) app.runtime.qrCheckpoint(id)
        toast(if (known) "Marker $id" else "Marker $id is not in the site map")
    }

    private fun scanQr() {
        qrLauncher.launch(
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the marker label")
                .setBeepEnabled(true).setOrientationLocked(true),
        )
    }

    private fun startPositioning() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) PositioningService.start(this) else permissionLauncher.launch(missing.toTypedArray())
    }

    /** Creates a DataWedge profile that sends QR scans to this app. Harmless on non-Zebra phones. */
    private fun configureDataWedge() {
        val intentParams = Bundle().apply {
            putString("intent_output_enabled", "true")
            putString("intent_action", DW_ACTION)
            putString("intent_delivery", "2")          // broadcast
        }
        val intentPlugin = Bundle().apply {
            putString("PLUGIN_NAME", "INTENT")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", intentParams)
        }
        val keystroke = Bundle().apply {
            putString("PLUGIN_NAME", "KEYSTROKE")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", Bundle().apply { putString("keystroke_output_enabled", "false") })
        }
        val appEntry = Bundle().apply {
            putString("PACKAGE_NAME", packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
        val profile = Bundle().apply {
            putString("PROFILE_NAME", "SwarmAMR")
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")
            putParcelableArray("APP_LIST", arrayOf(appEntry))
            putParcelableArrayList("PLUGIN_CONFIG", arrayListOf(intentPlugin, keystroke))
        }
        sendBroadcast(
            Intent("com.symbol.datawedge.api.ACTION")
                .setPackage("com.symbol.datawedge")
                .putExtra("com.symbol.datawedge.api.SET_CONFIG", profile),
        )
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        const val DW_ACTION = "com.swarmamr.app.SCAN"
        const val DW_DATA = "com.symbol.datawedge.data_string"
        private val TABS = listOf(
            "Live" to Icons.Filled.MyLocation,
            "Accuracy" to Icons.Filled.Straighten,
            "Survey" to Icons.Filled.Map,
            "Settings" to Icons.Filled.Settings,
        )
    }
}
