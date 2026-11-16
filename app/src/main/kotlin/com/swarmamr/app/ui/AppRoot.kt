package com.swarmamr.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swarmamr.app.SwarmApp

/** Provides the shared objects, then shows the first-run screens or the screen on top. */
@Composable
fun AppRoot(app: SwarmApp, nav: Nav, host: Host, scan: ScanRouter, onOnboardingDone: () -> Unit) {
    val snackHost = remember { SnackbarHostState() }
    CompositionLocalProvider(
        LocalApp provides app,
        LocalNav provides nav,
        LocalHost provides host,
        LocalScan provides scan,
        LocalSnack provides snackHost,
    ) {
        val settings by app.settings.state.collectAsStateWithLifecycle()
        if (!settings.onboardingDone) {
            OnboardingScreen(onDone = onOnboardingDone)
        } else {
            MainScreens(app, nav, snackHost)
        }
    }
}

@Composable
private fun MainScreens(app: SwarmApp, nav: Nav, snackHost: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    BackHandler(enabled = nav.top != null) { nav.pop() }
    // Scans that no screen handles: say what the code is, or offer to add it.
    OnScan { id ->
        val mk = app.sites.current.marker(id)
        if (mk != null) scope.snack(snackHost, "This is ${mk.name}")
        else scope.snack(snackHost, "New code \"$id\"", "Add marker") { nav.push(Screen.MarkerEdit(code = id)) }
    }
    when (val top = nav.top) {
        null -> when (nav.tab) {
            Tab.SETUP -> SetupScreen()
            Tab.TEST -> TestScreen()
            Tab.LIVE -> LiveScreen()
        }
        Screen.FloorPlan -> FloorPlanScreen()
        Screen.Markers -> MarkersScreen()
        Screen.MarkerNames -> MarkerNamesScreen()
        is Screen.MarkerEdit -> androidx.compose.runtime.key(top) { MarkerEditScreen(top.id, top.code) }
        Screen.Survey -> WalkScreen(WalkKind.SURVEY)
        Screen.TestWalk -> WalkScreen(WalkKind.TEST)
        Screen.AccessPoints -> AccessPointsScreen()
        Screen.Settings -> SettingsScreen()
    }
}
