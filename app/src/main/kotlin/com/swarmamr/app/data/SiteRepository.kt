package com.swarmamr.app.data

import android.content.Context
import com.swarmamr.core.AccessPoint
import com.swarmamr.core.FloorImage
import com.swarmamr.core.Fingerprint
import com.swarmamr.core.GridSpec
import com.swarmamr.core.Marker
import com.swarmamr.core.MarkerNaming
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
    private val dir = context.filesDir
    private val file = File(dir, "site.json")
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

    /** Add many recordings at once (a walk). */
    fun addFingerprints(fps: List<Fingerprint>) = edit { s ->
        val ids = fps.map { it.markerId }.filter { it.isNotEmpty() }.toSet()
        s.copy(fingerprints = s.fingerprints.filterNot { it.markerId in ids } + fps)
    }

    fun removeFingerprints(ids: Set<String>) = edit { s -> s.copy(fingerprints = s.fingerprints.filterNot { it.markerId in ids }) }

    /**
     * Use a floor plan picture as the map, or remove it (null). The floor becomes an open floor
     * the size of the picture. Markers and recordings keep their positions.
     */
    fun setImage(img: FloorImage?) = edit { s ->
        val old = s.image?.file
        if (old != null && old != img?.file) File(dir, old).delete()
        if (img == null) s.copy(image = null)
        else s.copy(image = img, grid = img.grid(s.grid.originX, s.grid.originY))
    }

    fun clearFingerprints() = edit { it.copy(fingerprints = emptyList()) }

    /** Change the floor plan. Markers stay where they are unless [regenerateMarkers]. */
    fun setGrid(g: GridSpec, regenerateMarkers: Boolean = false) = edit { s ->
        if (regenerateMarkers) s.copy(grid = g, markers = s.naming.markers(g), fingerprints = emptyList())
        else s.copy(grid = g)
    }

    /**
     * Replace all markers with automatic ones named by [naming]. A Wi-Fi recording at the same
     * spot as a new marker is kept and renamed, so changing only the names loses nothing.
     */
    fun makeMarkers(naming: MarkerNaming) = edit { s ->
        val fresh = naming.markers(s.grid)
        val oldPos = s.markers.associate { it.id to it.pos }
        val prints = s.fingerprints.mapNotNull { fp ->
            val at = oldPos[fp.markerId] ?: return@mapNotNull null
            val m = fresh.firstOrNull { it.pos.dist(at) < 0.05 } ?: return@mapNotNull null
            fp.copy(markerId = m.id, x = m.x, y = m.y)
        }
        s.copy(markers = fresh, fingerprints = prints, naming = naming)
    }

    fun setName(name: String) = edit { it.copy(name = name.trim().ifEmpty { it.name }) }

    /** Add a marker, or replace the one with the same id. [oldId] renames an existing marker. */
    fun upsertMarker(m: Marker, oldId: String? = null) = edit { s ->
        val replaced = oldId ?: m.id
        val markers = s.markers.toMutableList()
        val i = markers.indexOfFirst { it.id == replaced }
        if (i >= 0) markers[i] = m else markers += m
        // A moved or renamed marker keeps its Wi-Fi recording, which follows the marker.
        val prints = s.fingerprints.map { fp ->
            if (fp.markerId == replaced) fp.copy(x = m.x, y = m.y, markerId = m.id) else fp
        }
        s.copy(markers = markers.distinctBy { it.id }, fingerprints = prints)
    }

    fun removeMarker(id: String) = edit { s ->
        s.copy(markers = s.markers.filterNot { it.id == id }, fingerprints = s.fingerprints.filterNot { it.markerId == id })
    }

    fun clearMarkers() = edit { it.copy(markers = emptyList(), fingerprints = emptyList()) }

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
            return Site(id = "demo", name = "My place", grid = g, markers = Site.defaultMarkers(g))
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
