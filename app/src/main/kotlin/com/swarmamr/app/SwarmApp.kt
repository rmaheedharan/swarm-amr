package com.swarmamr.app

import android.app.Application
import com.swarmamr.app.data.SettingsStore
import com.swarmamr.app.data.SiteRepository
import kotlinx.coroutines.MainScope

class SwarmApp : Application() {
    lateinit var settings: SettingsStore; private set
    lateinit var sites: SiteRepository; private set
    lateinit var runtime: Runtime; private set
    lateinit var tools: FieldTools; private set

    /** For field tools that must keep running when the user switches tabs. */
    val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        sites = SiteRepository(this)
        runtime = Runtime(this, settings, sites)
        tools = FieldTools(this, runtime)
    }
}
