package com.swarmamr.app.data

import android.content.Context
import com.swarmamr.core.AccessPoint
import com.swarmamr.core.Fingerprint
import com.swarmamr.core.GridSpec
import com.swarmamr.core.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The site map (grid, APs, markers, fingerprints), saved as JSON in app storage. */
class SiteRepository(context: Context) {
    private val file = File(context.filesDir, "site.json")
    private val _site = MutableStateFlow(load())
    val site: StateFlow<Site> = _site.asStateFlow()
    val current get() = _site.value

    private fun load(): Site = runCatching { Site.fromJson(file.readText()) }.getOrElse { defaultSite() }

    fun replace(s: Site) {
        val next = s.copy(version = s.version)
        file.writeText(next.toJson())
        _site.value = next
    }

    private fun edit(f: (Site) -> Site) = replace(f(_site.value).let { it.copy(version = it.version + 1) })

    fun upsertAp(ap: AccessPoint) = edit { s ->
        s.copy(accessPoints = s.accessPoints.filterNot { it.bssid.equals(ap.bssid, true) } + ap)
    }

    fun removeAp(bssid: String) = edit { s -> s.copy(accessPoints = s.accessPoints.filterNot { it.bssid.equals(bssid, true) }) }

    fun setBiases(bias: Map<String, Double>) = edit { s ->
        s.copy(accessPoints = s.accessPoints.map { ap -> bias[ap.bssid]?.let { ap.copy(rttBiasM = it) } ?: ap })
    }

    fun addFingerprint(fp: Fingerprint) = edit { s ->
        s.copy(fingerprints = s.fingerprints.filterNot { it.markerId.isNotEmpty() && it.markerId == fp.markerId } + fp)
    }

    fun clearFingerprints() = edit { it.copy(fingerprints = emptyList()) }

    fun setGrid(g: GridSpec, regenerateMarkers: Boolean) = edit { s ->
        s.copy(grid = g, markers = if (regenerateMarkers) Site.defaultMarkers(g) else s.markers)
    }

    /** Download the site from the backend: GET {backend}/api/sites/{id}. */
    suspend fun download(backendUrl: String, siteId: String): Result<Site> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("$backendUrl/api/sites/$siteId").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 10000
            try {
                check(conn.responseCode == 200) { "Server answered ${conn.responseCode}" }
                Site.fromJson(conn.inputStream.bufferedReader().readText()).also { replace(it) }
            } finally { conn.disconnect() }
        }
    }

    /** Upload the site (with survey results): PUT {backend}/api/sites/{id}. */
    suspend fun upload(backendUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { Http.send("PUT", "$backendUrl/api/sites/${current.id}", current.toJson()) }
    }

    companion object {
        fun defaultSite(): Site {
            val g = GridSpec()
            return Site(id = "demo", name = "Demo site (edit in Survey)", grid = g, markers = Site.defaultMarkers(g))
        }
    }
}

object Http {
    fun send(method: String, url: String, json: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5000; conn.readTimeout = 10000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use { it.write(json.toByteArray()) }
            check(conn.responseCode in 200..299) { "Server answered ${conn.responseCode}" }
        } finally { conn.disconnect() }
    }
}
