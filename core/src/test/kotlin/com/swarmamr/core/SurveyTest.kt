package com.swarmamr.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FloorImageTest {
    @Test
    fun scaleFromKnownLength() {
        // A 10.71 m table drawn 510 px long.
        val mpp = assertNotNull(FloorImage.metresPerPx(Vec2(100.0, 200.0), Vec2(610.0, 200.0), 10.71))
        assertEquals(0.021, mpp, 1e-9)
        val img = FloorImage("plan.png", 2346, 1434, mpp)
        assertEquals(49.266, img.widthM, 1e-3)
        val g = img.grid()
        assertTrue(g.openFloor)
        assertEquals(50.0, g.widthM, 1e-9)
        assertEquals(31.0, g.depthM, 1e-9)
        // Pixel (0, heightPx) is the bottom-left corner, the site origin.
        assertEquals(Vec2(0.0, 0.0), img.toSite(0.0, 1434.0))
        assertNull(FloorImage.metresPerPx(Vec2(1.0, 1.0), Vec2(1.0, 1.0), 5.0))
    }

    @Test
    fun siteWithImageRoundTrips() {
        val s = Site(image = FloorImage("p.png", 10, 20, 0.5))
        assertEquals(s.image, Site.fromJson(s.toJson()).image)
        assertNull(Site.fromJson(Site().toJson()).image)
    }
}

class WalkRecordingTest {
    @Test
    fun scansAreSpreadAlongTheLine() {
        // 10 m walk over 20 s, one scan a second; signal of "a" grows along the way.
        val start = 1_000_000L
        val scans = (1..21).map { s ->
            TimedScan(start + s * 1000L, mapOf("a" to -80.0 + s, "b" to -60.0, "c" to -70.0))
        }
        val fps = WalkRecording.fingerprints(scans, Vec2(0.0, 0.0), Vec2(10.0, 0.0), start, start + 20_000, "W1", stepM = 2.0)
        assertEquals(5, fps.size)
        assertEquals("W1.1", fps.first().markerId)
        // Positions go from start to end, in order, along y = 0.
        assertTrue(fps.zipWithNext().all { (p, q) -> q.x > p.x })
        assertTrue(fps.first().x < 2.0 && fps.last().x > 8.0)
        assertTrue(fps.all { it.y == 0.0 })
        // Stronger "a" toward the end.
        assertTrue(fps.last().rssi.getValue("a") > fps.first().rssi.getValue("a"))
    }

    @Test
    fun walkNumbers() {
        assertEquals("W1", WalkRecording.nextPrefix(emptyList()))
        val fp = Fingerprint(0.0, 0.0, emptyMap(), "W7.3")
        assertEquals("W8", WalkRecording.nextPrefix(listOf(fp, Fingerprint(0.0, 0.0, emptyMap(), "P001"))))
        assertTrue(WalkRecording.isWalk("W7.3"))
        assertTrue(!WalkRecording.isWalk("W7"))
    }
}

class CoverageTest {
    @Test
    fun goodNoneAndAlike() {
        val g = GridSpec.open(20, 10)
        val wifiA = mapOf("a" to -40.0, "b" to -70.0, "c" to -80.0)
        val wifiB = mapOf("a" to -80.0, "b" to -40.0, "c" to -60.0)
        val one = Site(grid = g, fingerprints = listOf(Fingerprint(2.0, 2.0, wifiA, "P1")))
        val c1 = Coverage.of(one)
        assertTrue(c1.good > 0)
        assertEquals(0, c1.alike)
        assertTrue(c1.covered < 0.2)
        // Same Wi-Fi recorded 15 m away: both areas turn red.
        val two = one.copy(fingerprints = one.fingerprints + Fingerprint(17.0, 8.0, wifiA, "P2") + Fingerprint(10.0, 5.0, wifiB, "P3"))
        val c2 = Coverage.of(two)
        assertTrue(c2.alike > 0)
        val cellAt = { p: Vec2 -> c2.cells.first { p.x >= it.x && p.x < it.x + it.sizeM && p.y >= it.y && p.y < it.y + it.sizeM } }
        assertEquals(Coverage.Level.ALIKE, cellAt(Vec2(2.2, 2.2)).level)
        assertEquals(Coverage.Level.GOOD, cellAt(Vec2(10.2, 5.2)).level)
        assertEquals(Coverage.Level.NONE, cellAt(Vec2(0.5, 9.5)).level)
    }
}

class ApFilterTest {
    @Test
    fun hotspotsAreDropped() {
        val f = ApFilter()
        assertTrue(f.allows("7c:b6:8d:01:02:03"))
        assertTrue(!f.allows("da:a1:19:00:00:01"))          // random (0xda has bit 0x02)
        assertTrue(ApFilter.isRandom("7e:b6:8d:01:02:03"))
        assertTrue(ApFilter(dropRandom = false).allows("da:a1:19:00:00:01"))
    }

    @Test
    fun officeOnly() {
        val f = ApFilter(allowPrefixes = listOf("7C:B6:8D"), allowSsids = listOf("Office"))
        assertTrue(f.allows("7c:b6:8d:aa:bb:cc"))
        assertTrue(f.allows("7c:b6:8d:aa:bb:cc", "Guest"))   // prefix wins
        assertTrue(f.allows("00:11:22:33:44:55", "Office"))
        assertTrue(!f.allows("00:11:22:33:44:55", "iPhone"))
        assertTrue(!f.allows("00:11:22:33:44:55"))           // stored recording, unknown name
        assertTrue(!f.allows("7e:11:22:33:44:55", "Office")) // random still dropped unless prefix
        // Only names: an unknown name on a stored recording is kept.
        assertTrue(ApFilter(allowSsids = listOf("Office")).allows("00:11:22:33:44:55"))
    }

    @Test
    fun cleanRecording() {
        val fp = Fingerprint(1.0, 1.0, mapOf("7c:b6:8d:00:00:01" to -50.0, "da:00:00:00:00:01" to -40.0), "P1", 5,
            mapOf("7c:b6:8d:00:00:01" to 2.0, "da:00:00:00:00:01" to 3.0))
        val c = ApFilter().clean(fp)
        assertEquals(setOf("7c:b6:8d:00:00:01"), c.rssi.keys)
        assertEquals(setOf("7c:b6:8d:00:00:01"), c.std.keys)
        // Missing field in an old site file: defaults.
        assertEquals(ApFilter(), Site.fromJson("""{"id":"x"}""").apFilter)
    }
}
