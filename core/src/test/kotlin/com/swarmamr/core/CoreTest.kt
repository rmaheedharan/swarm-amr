package com.swarmamr.core

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrilaterationTest {
    private val aps = listOf(Vec2(0.0, 0.0), Vec2(20.0, 0.0), Vec2(0.0, 15.0), Vec2(20.0, 15.0))

    @Test
    fun exactRangesGiveExactPoint() {
        val truth = Vec2(7.0, 4.0)
        val fix = assertNotNull(Trilateration.solve(aps.map { Range(it, it.dist(truth), 0.5) }))
        assertTrue(fix.pos.dist(truth) < 1e-3, "got ${fix.pos}")
    }

    @Test
    fun noisyRangesStayClose() {
        val rnd = Random(1)
        val truth = Vec2(12.0, 9.0)
        val errs = (1..200).map {
            val fix = Trilateration.solve(aps.map { Range(it, it.dist(truth) + rnd.nextDouble(-0.5, 0.5), 0.5) })!!
            fix.pos.dist(truth)
        }
        assertTrue(errs.average() < 0.6, "mean error ${errs.average()}")
    }

    @Test
    fun oneBlockedRangeIsDownWeighted() {
        val truth = Vec2(10.0, 7.0)
        val ranges = aps.mapIndexed { i, ap ->
            Range(ap, ap.dist(truth) + if (i == 0) 6.0 else 0.0, 0.5)   // AP 0 reads 6 m long
        } + Range(Vec2(10.0, 15.0), 8.0, 0.5)
        val fix = Trilateration.solve(ranges)!!
        assertTrue(fix.pos.dist(truth) < 1.5, "got ${fix.pos}, error ${fix.pos.dist(truth)}")
    }

    @Test
    fun needsThreeAps() {
        assertNull(Trilateration.solve(aps.take(2).map { Range(it, 5.0, 1.0) }))
    }
}

class KalmanTest {
    @Test
    fun smoothsNoisyFixesOfAWalker() {
        val kf = Kalman2D()
        val rnd = Random(2)
        var rawErr = 0.0
        var kfErr = 0.0
        var n = 0
        for (step in 0..200) {
            val t = step * 0.5
            val truth = Vec2(1.2 * t, 3.0)
            kf.predict(0.5)
            val z = Vec2(truth.x + rnd.nextDouble(-1.5, 1.5), truth.y + rnd.nextDouble(-1.5, 1.5))
            kf.update(z, 1.0, 0.0, 1.0)
            if (step > 20) {
                rawErr += z.dist(truth); kfErr += kf.position.dist(truth); n++
            }
        }
        assertTrue(kfErr / n < rawErr / n, "filter ${kfErr / n} vs raw ${rawErr / n}")
    }

    @Test
    fun rejectsOneOutlierButRecoversFromARealJump() {
        val kf = Kalman2D()
        kf.reset(Vec2(0.0, 0.0), 0.25)
        assertFalse(kf.update(Vec2(30.0, 0.0), 1.0, 0.0, 1.0))
        assertTrue(kf.position.dist(Vec2(0.0, 0.0)) < 0.01)
        kf.update(Vec2(30.0, 0.0), 1.0, 0.0, 1.0)
        assertTrue(kf.update(Vec2(30.0, 0.0), 1.0, 0.0, 1.0))      // third in a row: reset
        assertTrue(kf.position.dist(Vec2(30.0, 0.0)) < 0.01)
    }

    @Test
    fun standingStillDoesNotDrift() {
        val kf = Kalman2D()
        kf.reset(Vec2(5.0, 5.0), 0.25)
        kf.update(Vec2(5.5, 5.0), 1.0, 0.0, 1.0)
        kf.setStationary(true)
        repeat(100) { kf.predict(0.1) }
        assertTrue(kf.speed == 0.0)
    }
}

class MapMatcherTest {
    private val g = GridSpec()

    @Test
    fun snapsToNearestAisleCentreline() {
        val s = MapMatcher(g).snapRaw(Vec2(g.aisleX(3) + 0.4, 6.0))
        assertEquals(3, s.aisle)
        assertEquals(g.aisleX(3), s.pos.x, 1e-9)
    }

    @Test
    fun snapsToCrossAisleNearTheEnds() {
        val s = MapMatcher(g).snapRaw(Vec2(10.0, g.topY() + 0.3))
        assertNull(s.aisle)
        assertEquals(g.topY(), s.pos.y, 1e-9)
    }

    @Test
    fun needsTwoUpdatesToChangeAisle() {
        val m = MapMatcher(g)
        m.force(Vec2(g.aisleX(2), 6.0))
        assertEquals(2, m.snap(Vec2(g.aisleX(3), 6.0)).aisle)
        assertEquals(3, m.snap(Vec2(g.aisleX(3), 6.0)).aisle)
    }
}

class RssiTest {
    @Test
    fun fingerprintPicksTheClosestSurveyPoint() {
        val prints = listOf(
            Fingerprint(0.0, 0.0, mapOf("a" to -40.0, "b" to -70.0, "c" to -80.0)),
            Fingerprint(10.0, 0.0, mapOf("a" to -70.0, "b" to -40.0, "c" to -75.0)),
            Fingerprint(5.0, 10.0, mapOf("a" to -75.0, "b" to -72.0, "c" to -42.0)),
        )
        val fix = RssiPositioning.fingerprint(mapOf("a" to -69.0, "b" to -42.0, "c" to -76.0), prints, k = 1)!!
        assertEquals(10.0, fix.pos.x, 1e-9)
    }

    @Test
    fun averageDropsRarelySeenAps() {
        val fp = RssiPositioning.average(
            listOf(mapOf("a" to -50.0, "b" to -80.0), mapOf("a" to -52.0), mapOf("a" to -54.0)),
            Vec2(1.0, 2.0),
        )
        assertEquals(-52.0, fp.rssi["a"]!!, 1e-9)
        assertNull(fp.rssi["b"])
    }
}

class AccuracyTest {
    private fun sample(err: Double, aisleOk: Boolean) = AccuracySample(
        0, "A1-01", 0.0, 0.0, err, 0.0, "RTT", 4, 1.0, "A1", if (aisleOk) "A1" else "A2",
    )

    @Test
    fun percentilesAndAisleRate() {
        val s = Accuracy.summarise((1..10).map { sample(it.toDouble(), it != 10) })
        assertEquals(5.0, s.p50M, 1e-9)
        assertEquals(9.0, s.p90M, 1e-9)
        assertEquals(90.0, s.correctAislePct, 1e-9)
        assertFalse(s.passesGate1)
    }

    @Test
    fun csvUsesDotDecimals() {
        val csv = Accuracy.toCsv(listOf(sample(1.25, true)))
        assertTrue(csv.lines()[1].contains("1.250"))
    }
}

class FuserTest {
    private val grid = GridSpec()
    private val site = Site(
        grid = grid,
        accessPoints = listOf(
            AccessPoint("aa:00", 0.0, 0.0), AccessPoint("aa:01", 24.0, 0.0),
            AccessPoint("aa:02", 0.0, 15.6), AccessPoint("aa:03", 24.0, 15.6),
        ),
        markers = Site.defaultMarkers(grid),
    )

    @Test
    fun rttWalkInsideAnAisleStaysInThatAisle() {
        val f = PositionFuser(site)
        val rnd = Random(3)
        var ok = 0
        var n = 0
        for (i in 0..60) {
            val now = 1_000L + i * 500
            val truth = Vec2(grid.aisleX(3), 1.2 + (i % 20) * 0.5)
            val res = site.accessPoints.map {
                RttMeasurement(it.bssid, it.pos.dist(truth) + rnd.nextDouble(-0.8, 0.8), 0.6, -60, 8, 8)
            }
            f.onRtt(res, now)
            val e = f.estimate(now)!!
            if (i > 5) { n++; if (e.snapped.aisle == 3) ok++ }
        }
        assertTrue(ok.toDouble() / n > 0.9, "aisle correct $ok / $n")
    }

    @Test
    fun qrResetsPosition() {
        val f = PositionFuser(site)
        assertTrue(f.onQr("A2-05", 1000))
        val m = site.marker("A2-05")!!
        val e = f.estimate(1000)!!
        assertTrue(abs(e.raw.x - m.x) < 1e-9 && abs(e.raw.y - m.y) < 1e-9)
        assertEquals(Fix.Source.QR, e.source)
    }

    @Test
    fun siteJsonRoundTrip() {
        assertEquals(site, Site.fromJson(site.toJson()))
    }

    @Test
    fun protocolRoundTrip() {
        val d = DirectiveMsg("tc58-1", 7, 1000, CueState.HOLD, "Wait 6 s", aisle = 2, holdS = 6, expiresMs = 6000)
        val text = WireJson.encodeToString(DirectiveMsg.serializer(), d)
        assertTrue(text.contains("\"state\":\"hold\""))
        assertEquals(d, WireJson.decodeFromString(DirectiveMsg.serializer(), text))
    }
}
