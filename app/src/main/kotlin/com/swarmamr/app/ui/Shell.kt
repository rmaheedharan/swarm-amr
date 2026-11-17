package com.swarmamr.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.swarmamr.app.SwarmApp
import com.swarmamr.core.MarkerCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** The three main tabs, in the order a new user needs them. */
enum class Tab(val label: String, val icon: ImageVector, val outline: ImageVector) {
    SETUP("Set up", Icons.Filled.Checklist, Icons.Outlined.Checklist),
    TEST("Test", Icons.Filled.Straighten, Icons.Outlined.Straighten),
    LIVE("Live", Icons.Filled.MyLocation, Icons.Outlined.MyLocation),
}

/** Screens opened on top of a tab. */
sealed interface Screen {
    data object FloorPlan : Screen
    data object Markers : Screen
    /** Make automatic markers named by a pattern. */
    data object MarkerNames : Screen
    /** Edit [id], or add a new marker; [code] is the text read from a QR code (null = type a name). */
    data class MarkerEdit(val id: String? = null, val code: String? = null) : Screen
    /** Guided walk that records Wi-Fi at each marker. */
    data object Survey : Screen
    /** Tap the map or scan a code, then record Wi-Fi there at once (no marker needed first). */
    data object QuickRecord : Screen
    /** Guided walk that records the accuracy test at each marker. */
    data object TestWalk : Screen
    data object AccessPoints : Screen
    data object Settings : Screen
}

class Nav(start: Tab) {
    var tab by mutableStateOf(start)
    val stack = mutableStateListOf<Screen>()
    val top: Screen? get() = stack.lastOrNull()

    fun push(s: Screen) { stack += s }
    fun pop(): Boolean = if (stack.isEmpty()) false else { stack.removeAt(stack.lastIndex); true }
    fun goTab(t: Tab) { stack.clear(); tab = t }
}

/** Things only the Activity can do, offered to the screens. */
interface Host {
    val permissionsGranted: Boolean
    fun requestPermissions()
    /** Start positioning; asks for permissions first when needed. */
    fun startPositioning()
    fun stopPositioning()
    fun openDeveloperOptions()
    fun openAppSettings()
    fun share(file: File, mime: String)
    fun importPlace()
    /** True / false on Android 11+, null when the phone cannot tell. */
    fun scanThrottleOn(): Boolean?
}

/**
 * QR scans from the camera or a Zebra scan trigger go to the screen on top. Text is turned
 * into a marker id first (MarkerCodes).
 */
class ScanRouter(private val openCamera: (prompt: String) -> Unit) {
    private val handlers = mutableListOf<(String) -> Unit>()

    fun register(h: (String) -> Unit): () -> Unit {
        handlers += h
        return { handlers.remove(h) }
    }

    fun deliver(raw: String?) {
        val id = MarkerCodes.parse(raw) ?: return
        handlers.lastOrNull()?.invoke(id)
    }

    fun camera(prompt: String = "Point the camera at the QR code") = openCamera(prompt)
}

val LocalApp = staticCompositionLocalOf<SwarmApp> { error("no app") }
val LocalNav = staticCompositionLocalOf<Nav> { error("no nav") }
val LocalHost = staticCompositionLocalOf<Host> { error("no host") }
val LocalScan = staticCompositionLocalOf<ScanRouter> { error("no scanner") }
val LocalSnack = staticCompositionLocalOf<SnackbarHostState> { error("no snackbar") }

/** While this composable is shown, scanned codes go to [handler]. */
@Composable
fun OnScan(handler: (String) -> Unit) {
    val router = LocalScan.current
    val current by rememberUpdatedState(handler)
    DisposableEffect(router) {
        val unregister = router.register { current(it) }
        onDispose { unregister() }
    }
}

/** Show a short message at the bottom; [onAction] runs if the user taps the action. */
fun CoroutineScope.snack(host: SnackbarHostState, text: String, action: String? = null, onAction: () -> Unit = {}) {
    launch {
        host.currentSnackbarData?.dismiss()
        val r = host.showSnackbar(text, actionLabel = action, withDismissAction = action == null,
            duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) onAction()
    }
}

/**
 * Every screen uses this frame: a top app bar (back arrow on sub-screens, settings gear on
 * tabs), the bottom navigation on tabs, and the shared snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    back: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val nav = LocalNav.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (back) IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    actions()
                    if (!back) IconButton(onClick = { nav.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        bottomBar = {
            if (!back) ShortNavBar(nav.tab, onSelect = { nav.goTab(it) })
        },
        floatingActionButton = fab,
        snackbarHost = { SnackbarHost(LocalSnack.current) },
        content = content,
    )
}

/**
 * The short bottom bar of current Google apps: 64 dp high, a pill behind the chosen icon,
 * filled icon when chosen and outlined when not.
 */
@Composable
fun ShortNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val on = t == selected
                val pill by animateColorAsState(if (on) scheme.secondaryContainer else Color.Transparent, label = "pill")
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(t) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.clip(CircleShape).background(pill).padding(horizontal = 18.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (on) t.icon else t.outline, null, Modifier.size(24.dp),
                            tint = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        t.label,
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
