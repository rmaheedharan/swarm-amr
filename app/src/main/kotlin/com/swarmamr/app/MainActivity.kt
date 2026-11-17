package com.swarmamr.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.swarmamr.app.sensing.PositioningService
import com.swarmamr.app.ui.AppRoot
import com.swarmamr.app.ui.Host
import com.swarmamr.app.ui.Nav
import com.swarmamr.app.ui.ScanRouter
import com.swarmamr.app.ui.SwarmTheme
import com.swarmamr.app.ui.Tab
import com.swarmamr.core.Site
import java.io.File

class MainActivity : ComponentActivity() {
    private val app get() = application as SwarmApp
    private var granted by mutableStateOf(false)
    private var startAfterGrant = false
    private lateinit var nav: Nav

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = hasLocation()
            if (granted && startAfterGrant) PositioningService.start(this)
            if (!granted) toast("Without location, the app cannot read Wi-Fi.")
            startAfterGrant = false
        }

    private val qrLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(ScanContract()) { result -> scan.deliver(result.contents) }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            app.sites.replace(Site.fromJson(text))
            app.settings.update { it.copy(floorPlanSaved = true) }
        }.onSuccess { toast("Place file opened.") }.onFailure { toast("This is not a place file: ${it.message}") }
    }

    private val scan: ScanRouter = ScanRouter { prompt ->
        qrLauncher.launch(
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE, ScanOptions.DATA_MATRIX, ScanOptions.CODE_128)
                .setPrompt(prompt).setBeepEnabled(true).setOrientationLocked(true),
        )
    }

    /** Zebra DataWedge sends scans as a broadcast (profile made below). */
    private val dataWedge = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scan.deliver(intent.getStringExtra(DW_DATA))
        }
    }

    private val host = object : Host {
        override val permissionsGranted: Boolean get() = granted
        override fun requestPermissions() {
            startAfterGrant = true
            permissionLauncher.launch(neededPermissions().toTypedArray())
        }
        override fun startPositioning() {
            if (hasLocation()) PositioningService.start(this@MainActivity) else requestPermissions()
        }
        override fun stopPositioning() = PositioningService.stop(this@MainActivity)
        override fun openDeveloperOptions() {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                .onFailure { toast("Turn on Developer options first: tap Build number 7 times in About phone.") }
        }
        override fun openAppSettings() {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        override fun share(file: File, mime: String, extra: List<File>) = com.swarmamr.app.ui.share(this@MainActivity, file, mime, extra)
        override fun importPlace() = importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        override fun scanThrottleOn(): Boolean? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                applicationContext.getSystemService(WifiManager::class.java)?.isScanThrottleEnabled
            } else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = app.sites.current
        val ready = site.markers.isNotEmpty() && (site.fingerprints.isNotEmpty() || site.accessPoints.isNotEmpty())
        nav = Nav(if (ready) Tab.LIVE else Tab.SETUP)
        granted = hasLocation()
        enableEdgeToEdge()
        configureDataWedge()
        setContent {
            SwarmTheme {
                AppRoot(app, nav, host, scan, onOnboardingDone = {
                    app.settings.update { it.copy(onboardingDone = true) }
                    nav.goTab(Tab.SETUP)
                    if (hasLocation()) PositioningService.start(this)
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted = hasLocation()
        // Positioning starts by itself once the user has seen the welcome screens.
        if (granted && app.settings.current.onboardingDone && !app.runtime.running.value) PositioningService.start(this)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, dataWedge, IntentFilter(DW_ACTION), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStop() {
        runCatching { unregisterReceiver(dataWedge) }
        super.onStop()
    }

    private fun neededPermissions() = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasLocation(): Boolean {
        val must = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return must.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
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
    }
}
