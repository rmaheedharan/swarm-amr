import XCTest
@testable import LocateCore

final class MatcherTests: XCTestCase {
    let prints = [
        Fingerprint(0, 0, ["a": -40, "b": -70, "c": -80], markerId: "P1"),
        Fingerprint(10, 0, ["a": -70, "b": -40, "c": -75], markerId: "P2"),
        Fingerprint(5, 10, ["a": -75, "b": -72, "c": -42], markerId: "P3"),
    ]

    func testPicksTheClosestRecordingLikeTheApp() {
        let ranked = Matcher.rank(["a": -69, "b": -42, "c": -76], prints)
        let fix = Matcher.fromRanked(ranked, k: 1)!
        XCTAssertEqual(fix.pos.x, 10, accuracy: 1e-9)
        XCTAssertEqual(fix.best.markerId, "P2")
    }

    func testSameDistanceAsKotlin() {
        // scan a=-50, b=-60 (3 APs needed) vs fp; computed by hand like RssiPositioning.distance.
        let fp = Fingerprint(0, 0, ["a": -52, "b": -60, "c": -70])
        let d = Matcher.distance(["a": -50, "b": -60, "c": -70, "d": -80], fp)!
        // diffs: a 2, b 0, c 0, d (-80 - -100 = 20) over 4 keys
        XCTAssertEqual(d, ((4.0 + 400.0) / 4).squareRoot(), accuracy: 1e-9)
        XCTAssertNil(Matcher.distance(["a": -50, "b": -60], fp))
    }

    /** A MacBook that hears everything 8 dB weaker than the phone. */
    func testWeakerMacStillFindsTheSpotWithCorrection() {
        let mac = ["a": -77.0, "b": -48, "c": -84]          // P2 minus 7 to 9 dB
        XCTAssertEqual(Matcher.fromRanked(Matcher.rank(mac, prints, mode: .auto))!.best.markerId, "P2")
        XCTAssertEqual(Matcher.fromRanked(Matcher.rank(mac, prints, mode: .rank))!.best.markerId, "P2")
        let o = Matcher.offset(mac, prints[1])!
        XCTAssertEqual(o.db, 8, accuracy: 1e-9)
        XCTAssertEqual(o.common, 3)
        XCTAssertEqual(Matcher.fromRanked(Matcher.rank(mac, prints, mode: .offset, offsetDb: 8))!.best.markerId, "P2")
    }

    func testAutoCorrectionIsLimited() {
        let fp = Fingerprint(0, 0, ["a": -40, "b": -45, "c": -50])
        let far = ["a": -80.0, "b": -85, "c": -90]
        // 40 dB weaker: correction stops at 15 dB, so 25 dB stays.
        XCTAssertEqual(Matcher.score(far, fp, mode: .auto, offsetDb: 0)!, 20, accuracy: 1e-9)    // clamped at 20 per AP
    }

    func testOrderOnlyIgnoresTheGap() {
        let fp = Fingerprint(0, 0, ["a": -40, "b": -50, "c": -60, "d": -70])
        XCTAssertEqual(Matcher.rankDistance(["a": -55, "b": -65, "c": -75, "d": -85], fp)!, 0, accuracy: 1e-9)
        XCTAssertGreaterThan(Matcher.rankDistance(["a": -85, "b": -75, "c": -65, "d": -55], fp)!, 9)
    }

    func testUnsureWhenTwoFarSpotsLookAlike() {
        let p = [
            Fingerprint(0, 0, ["a": -60, "b": -65, "c": -70], markerId: "L1"),
            Fingerprint(20, 0, ["a": -62, "b": -63, "c": -67], markerId: "L2"),
        ]
        let fix = Matcher.fromRanked(Matcher.rank(["a": -61, "b": -64, "c": -68.5], p))!
        XCTAssertNotNil(fix.runnerUp)
    }

    func testMeanKeepsApsHeardHalfTheTime() {
        let m = Matcher.mean([["a": -50, "b": -70], ["a": -54], ["a": -52]])
        XCTAssertEqual(m["a"]!, -52, accuracy: 1e-9)
        XCTAssertNil(m["b"])
    }

    func testTrackerDropsOldScansWhenMoved() {
        let t = Tracker()
        let t0 = Date(timeIntervalSince1970: 1000)
        XCTAssertFalse(t.add(["a": -50, "b": -60, "c": -70], at: t0))
        XCTAssertFalse(t.add(["a": -52, "b": -61, "c": -69], at: t0.addingTimeInterval(5)))
        XCTAssertEqual(t.scans.count, 2)
        XCTAssertTrue(t.add(["a": -80, "b": -45, "c": -55], at: t0.addingTimeInterval(10)))
        XCTAssertEqual(t.scans.count, 1)
    }

    func testPercentileSameAsApp() {
        XCTAssertEqual(Accuracy.percentile([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 90), 9)
        XCTAssertEqual(Accuracy.percentile([1, 2, 3, 4], 50), 2)
    }
}

final class SiteTests: XCTestCase {
    func testReadsTheAndroidPlaceFile() throws {
        let json = """
        {
          "id": "office", "name": "Office", "version": 4,
          "grid": { "originX": 0.0, "originY": 0.0, "aislePitchM": 1.0, "cellM": 1.0, "nAisles": 20,
                    "aisleLenCells": 11, "narrowAisles": [], "openFloor": true },
          "accessPoints": [],
          "markers": [ { "id": "D12", "x": 4.5, "y": 3.6, "note": "", "label": "Desk 12" } ],
          "fingerprints": [
            { "x": 4.5, "y": 3.6, "rssi": { "AA:BB:CC:DD:EE:01": -52.5 }, "markerId": "D12", "samples": 6,
              "std": { "aa:bb:cc:dd:ee:01": 2.0 } }
          ],
          "naming": { "pattern": "A{A}-{RR}", "firstAisle": 0, "firstSpot": 1, "everyCells": 2 },
          "image": { "file": "plan.png", "widthPx": 2000, "heightPx": 1200, "metresPerPx": 0.01 }
        }
        """
        let s = try Site.load(Data(json.utf8))
        XCTAssertEqual(s.name, "Office")
        XCTAssertTrue(s.grid.openFloor)
        XCTAssertEqual(s.markers.first?.name, "Desk 12")
        XCTAssertEqual(s.fingerprints.first?.rssi["aa:bb:cc:dd:ee:01"], -52.5)
        XCTAssertEqual(s.image?.widthM ?? 0, 20, accuracy: 1e-9)
        XCTAssertEqual(s.recordedMarkers.map { $0.id }, ["D12"])
        XCTAssertEqual(s.grid.area(Vec2(4.5, 3.6)), "Z1-0")
    }

    func testMissingFieldsGetDefaults() throws {
        let s = try Site.load(Data(#"{"markers":[{"id":"A1-03","x":4.5,"y":3.6}]}"#.utf8))
        XCTAssertEqual(s.grid.nAisles, 8)
        XCTAssertFalse(s.grid.openFloor)
        XCTAssertEqual(s.grid.area(Vec2(4.5, 3.6)), "A1")
        XCTAssertEqual(s.grid.area(Vec2(6.0, 0.1)), "cross")
    }

    func testBssidSpellings() {
        XCTAssertEqual(Bssid.normal("A:B:C:D:E:F"), "0a:0b:0c:0d:0e:0f")
        XCTAssertEqual(Bssid.normal("AA-BB-CC-DD-EE-01"), "aa:bb:cc:dd:ee:01")
    }
}

final class ApFilterTests: XCTestCase {
    let office = "7c:b6:8d:01:02:03"
    let hotspot = "da:a1:19:00:00:01"      // 0xda has the 0x02 bit: random address
    let other = "00:11:22:33:44:55"

    func testDefaultDropsOnlyRandomAddresses() {
        let f = ApFilter()
        XCTAssertTrue(ApFilter.isRandom(hotspot))
        XCTAssertFalse(ApFilter.isRandom(office))
        XCTAssertTrue(f.keeps(office, ssid: nil))
        XCTAssertTrue(f.keeps(other, ssid: "Cafe"))
        XCTAssertFalse(f.keeps(hotspot, ssid: "Ravi's iPhone"))
    }

    func testAllowedPrefixWinsOverRandom() {
        let f = ApFilter(allowPrefixes: ["7E-B6-8D"])
        XCTAssertTrue(f.keeps("7e:b6:8d:00:00:01", ssid: nil))
        XCTAssertFalse(f.keeps(other, ssid: "Cafe"))
        XCTAssertFalse(f.keeps(other, ssid: nil))
    }

    func testWifiNames() {
        let f = ApFilter(allowSsids: ["Office WiFi"])
        XCTAssertTrue(f.keeps(other, ssid: "office wifi"))
        XCTAssertFalse(f.keeps(other, ssid: "Cafe"))
        XCTAssertTrue(f.keeps(other, ssid: nil))          // name unknown, no prefix list: keep
        XCTAssertFalse(f.keeps(hotspot, ssid: "Office WiFi"))
    }

    func testParseAndFilterRecordings() {
        let f = ApFilter.parse("7c:b6:8d, Office WiFi")
        XCTAssertEqual(f.allowPrefixes, ["7c:b6:8d"])
        XCTAssertEqual(f.allowSsids, ["Office WiFi"])
        var site = Site()
        site.fingerprints = [Fingerprint(0, 0, [office: -50, hotspot: -40, other: -60], std: [hotspot: 2])]
        let used = site.filtered(f, ssids: [other: "Cafe"])
        XCTAssertEqual(Set(used.fingerprints[0].rssi.keys), [office])
        XCTAssertTrue(used.fingerprints[0].std.isEmpty)
    }

    func testReadsFilterFromPlaceFile() throws {
        let s = try Site.load(Data(#"{"apFilter":{"dropRandom":false,"allowPrefixes":["7C:B6:8D"],"allowSsids":[]}}"#.utf8))
        XCTAssertFalse(s.apFilter.dropRandom)
        XCTAssertEqual(s.apFilter.allowPrefixes, ["7c:b6:8d"])
        XCTAssertTrue(try Site.load(Data("{}".utf8)).apFilter.dropRandom)
    }
}
