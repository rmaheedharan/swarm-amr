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

class MarkerTest {
    private val ms = listOf(Marker("DESK-4-021", 1.0, 1.0, label = "Desk 21"), Marker("A0-03", 5.0, 1.0), Marker("A0-05", 9.0, 1.0))

    @Test
    fun anyQrTextIsAMarkerId() {
        assertEquals("DESK-4-021", MarkerCodes.parse("  DESK-4-021\n"))
        assertEquals("A3-07", MarkerCodes.parse("SWM:A3-07"))
        assertNull(MarkerCodes.parse("   "))
        val site = Site(markers = ms)
        assertEquals("Desk 21", site.marker("desk-4-021")?.name)
        assertEquals("A0-03", site.marker("A0-03")?.name)
    }

    @Test
    fun walkPlanGoesToTheNearestMarkerNotDone() {
        assertEquals("DESK-4-021", WalkPlan.next(ms, emptySet(), null)?.id)
        assertEquals("A0-03", WalkPlan.next(ms, setOf("DESK-4-021"), Vec2(1.0, 1.0))?.id)
        assertEquals("A0-05", WalkPlan.next(ms, setOf("DESK-4-021"), Vec2(1.0, 1.0), skip = setOf("A0-03"))?.id)
        assertEquals("A0-03", WalkPlan.next(ms, setOf("DESK-4-021", "A0-05"), Vec2(9.0, 1.0), skip = setOf("A0-03"))?.id)
        assertNull(WalkPlan.next(ms, ms.map { it.id }.toSet(), null))
    }

    @Test
    fun namingPatterns() {
        val g = GridSpec(nAisles = 5, aisleLenCells = 70)
        assertEquals("A3-07", MarkerNaming().markers(g).first { it.x == g.aisleX(3) && it.y == g.cellM * 7 }.id)
        assertEquals(Site.defaultMarkers(g).size, 5 * 35)
        val l = MarkerNaming("L{AA}{SS}", firstAisle = 1, everyCells = 2).markers(g)
        assertEquals("L0101", l.first().id)
        assertEquals("L0432", l.first { it.x == g.aisleX(3) && it.y == (2 * 32 - 1) * g.cellM }.id)
        val run = MarkerNaming("{NNNN}", everyCells = 1).markers(GridSpec(nAisles = 2, aisleLenCells = 3))
        assertEquals(listOf("0001", "0002", "0003", "0004", "0005", "0006"), run.map { it.id })
        assertTrue(MarkerNaming("L{AA}{SS}").isUnique(g))
        assertFalse(MarkerNaming("L{AA}").isUnique(g))
    }

    @Test
    fun openFloorDoesNotSnapToAisles() {
        val g = GridSpec.open(20, 12)
        assertEquals(20.0, g.widthM, 1e-9); assertEquals(12.0, g.depthM, 1e-9)
        val mm = MapMatcher(g)
        val s = mm.snap(Vec2(7.3, 5.1))
        assertEquals(7.3, s.pos.x, 1e-9); assertEquals(5.1, s.pos.y, 1e-9)
        assertEquals("Z1-1", s.area)
        assertEquals(20.0, mm.snapRaw(Vec2(25.0, -3.0)).pos.x, 1e-9)
        assertEquals(0.0, mm.snapRaw(Vec2(25.0, -3.0)).pos.y, 1e-9)
    }
}


class LookAlikeTest {
    private fun fp(id: String, x: Double, vararg r: Pair<String, Double>, std: Map<String, Double> = emptyMap()) =
        Fingerprint(x, 0.0, r.toMap(), id, 10, std)

    @Test
    fun averageKeepsTheSpreadOfEachAp() {
        val f = RssiPositioning.average(listOf(mapOf("a" to -50.0, "b" to -70.0), mapOf("a" to -54.0, "b" to -70.0)), Vec2(0.0, 0.0))
        assertEquals(-52.0, f.rssi["a"]!!, 1e-9)
        assertEquals(2.83, f.std["a"]!!, 0.01)
        assertEquals(0.0, f.std["b"]!!, 1e-9)
    }

    @Test
    fun unsteadyApsCountForLess() {
        val scan = mapOf("a" to -50.0, "b" to -60.0, "c" to -70.0)
        val steady = fp("s", 0.0, "a" to -50.0, "b" to -60.0, "c" to -82.0)
        val shaky = fp("u", 0.0, "a" to -50.0, "b" to -60.0, "c" to -82.0, std = mapOf("c" to 12.0))
        assertTrue(RssiPositioning.distance(scan, shaky)!! < RssiPositioning.distance(scan, steady)!!)
    }

    @Test
    fun findsSpotsThatLookAlikeAndSaysWhenUnsure() {
        val prints = listOf(
            fp("441", 1.0, "a" to -62.0, "b" to -63.0, "c" to -67.0),
            fp("4414", 10.0, "a" to -60.0, "b" to -65.0, "c" to -70.0),
            fp("4589", 22.0, "a" to -85.0, "d" to -60.0, "e" to -70.0, "b" to -90.0),
        )
        val pairs = RssiPositioning.lookAlikes(prints)
        assertEquals(1, pairs.size)
        assertEquals(setOf("441", "4414"), setOf(pairs[0].first.markerId, pairs[0].second.markerId))
        val ranked = RssiPositioning.rank(mapOf("a" to -61.0, "b" to -64.0, "c" to -68.5), prints)
        val other = RssiPositioning.runnerUp(ranked)
        assertEquals(setOf("441", "4414"), setOf(ranked[0].first.markerId, other?.markerId))
        assertNull(RssiPositioning.runnerUp(RssiPositioning.rank(mapOf("a" to -85.0, "d" to -60.0, "e" to -70.0, "b" to -90.0), prints)))
    }
}
