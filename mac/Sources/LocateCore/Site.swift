import Foundation

// The place file the Android app shares ("Share place file") and the server stores.
// Same format as core/.../Site.kt and docs/site-file.md. Missing fields get the same
// defaults as the Android app, and unknown fields are ignored.

public struct Vec2: Equatable, Hashable {
    public var x: Double
    public var y: Double
    public init(_ x: Double, _ y: Double) { self.x = x; self.y = y }
    public func dist(_ o: Vec2) -> Double { hypot(x - o.x, y - o.y) }
}

public struct GridSpec: Decodable, Equatable {
    public var originX = 0.0
    public var originY = 0.0
    public var aislePitchM = 3.0
    public var cellM = 1.2
    public var nAisles = 8
    public var aisleLenCells = 12
    public var narrowAisles: [Int] = [1, 2, 5, 6]
    public var openFloor = false

    public init() {}

    public static let zoneM = 4.0

    public var halfPitch: Double { aislePitchM / 2 }
    public var topRow: Int { aisleLenCells + 1 }
    public var widthCells: Int { 2 * nAisles + 1 }
    public func aisleX(_ a: Int) -> Double { originX + Double(2 * a + 1) * halfPitch }
    public var bottomY: Double { originY }
    public var topY: Double { originY + Double(topRow) * cellM }
    public var widthM: Double { Double(nAisles) * aislePitchM }
    public var depthM: Double { topY - bottomY }
    public var maxX: Double { originX + Double(widthCells - 1) * halfPitch }

    /** "A3" for a pick aisle, "cross" for a cross-aisle, "Z2-3" on an open floor (same as the app). */
    public func area(_ p: Vec2) -> String {
        if openFloor {
            let qx = min(max(p.x, originX), maxX)
            let qy = min(max(p.y, bottomY), topY)
            let ix = clampInt(Int((qx - originX) / Self.zoneM), 0, max(0, Int(widthM / Self.zoneM)))
            let iy = clampInt(Int((qy - originY) / Self.zoneM), 0, max(0, Int(depthM / Self.zoneM)))
            return "Z\(ix)-\(iy)"
        }
        let cx = min(max(p.x, originX), maxX)
        var best = hypot(cx - p.x, min(abs(p.y - bottomY), abs(p.y - topY)))
        var name = "cross"
        for a in 0..<nAisles {
            let q = Vec2(aisleX(a), min(max(p.y, bottomY), topY))
            let d = q.dist(p)
            if d < best { best = d; name = "A\(a)" }
        }
        return name
    }

    enum K: String, CodingKey {
        case originX, originY, aislePitchM, cellM, nAisles, aisleLenCells, narrowAisles, openFloor
    }

    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        originX = try c.decodeIfPresent(Double.self, forKey: .originX) ?? originX
        originY = try c.decodeIfPresent(Double.self, forKey: .originY) ?? originY
        aislePitchM = try c.decodeIfPresent(Double.self, forKey: .aislePitchM) ?? aislePitchM
        cellM = try c.decodeIfPresent(Double.self, forKey: .cellM) ?? cellM
        nAisles = try c.decodeIfPresent(Int.self, forKey: .nAisles) ?? nAisles
        aisleLenCells = try c.decodeIfPresent(Int.self, forKey: .aisleLenCells) ?? aisleLenCells
        narrowAisles = try c.decodeIfPresent([Int].self, forKey: .narrowAisles) ?? narrowAisles
        openFloor = try c.decodeIfPresent(Bool.self, forKey: .openFloor) ?? openFloor
    }
}

func clampInt(_ v: Int, _ lo: Int, _ hi: Int) -> Int { min(max(v, lo), hi) }

public struct AccessPoint: Decodable, Equatable {
    public var bssid: String
    public var x: Double
    public var y: Double
    public var label: String
    public var pos: Vec2 { Vec2(x, y) }

    enum K: String, CodingKey { case bssid, x, y, label }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        bssid = Bssid.normal(try c.decode(String.self, forKey: .bssid))
        x = try c.decodeIfPresent(Double.self, forKey: .x) ?? 0
        y = try c.decodeIfPresent(Double.self, forKey: .y) ?? 0
        label = try c.decodeIfPresent(String.self, forKey: .label) ?? ""
    }
}

public struct Marker: Decodable, Equatable, Identifiable, Hashable {
    public var id: String
    public var x: Double
    public var y: Double
    public var label: String
    public var pos: Vec2 { Vec2(x, y) }
    public var name: String { label.trimmingCharacters(in: .whitespaces).isEmpty ? id : label }

    public init(id: String, x: Double, y: Double, label: String = "") {
        self.id = id; self.x = x; self.y = y; self.label = label
    }

    enum K: String, CodingKey { case id, x, y, label }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        id = try c.decode(String.self, forKey: .id)
        x = try c.decodeIfPresent(Double.self, forKey: .x) ?? 0
        y = try c.decodeIfPresent(Double.self, forKey: .y) ?? 0
        label = try c.decodeIfPresent(String.self, forKey: .label) ?? ""
    }
}

/** Mean RSSI per BSSID recorded at one point by the phone. */
public struct Fingerprint: Decodable, Equatable {
    public var x: Double
    public var y: Double
    public var rssi: [String: Double]
    public var markerId: String
    public var samples: Int
    /** How much each AP's signal moved during the recording (dB). */
    public var std: [String: Double]
    public var pos: Vec2 { Vec2(x, y) }

    public init(_ x: Double, _ y: Double, _ rssi: [String: Double], markerId: String = "", std: [String: Double] = [:]) {
        self.x = x; self.y = y; self.markerId = markerId; self.samples = 1
        self.rssi = Dictionary(rssi.map { (Bssid.normal($0.key), $0.value) }, uniquingKeysWith: max)
        self.std = Dictionary(std.map { (Bssid.normal($0.key), $0.value) }, uniquingKeysWith: max)
    }

    enum K: String, CodingKey { case x, y, rssi, markerId, samples, std }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        x = try c.decodeIfPresent(Double.self, forKey: .x) ?? 0
        y = try c.decodeIfPresent(Double.self, forKey: .y) ?? 0
        let r = try c.decodeIfPresent([String: Double].self, forKey: .rssi) ?? [:]
        rssi = Dictionary(r.map { (Bssid.normal($0.key), $0.value) }, uniquingKeysWith: max)
        markerId = try c.decodeIfPresent(String.self, forKey: .markerId) ?? ""
        samples = try c.decodeIfPresent(Int.self, forKey: .samples) ?? 1
        let s = try c.decodeIfPresent([String: Double].self, forKey: .std) ?? [:]
        std = Dictionary(s.map { (Bssid.normal($0.key), $0.value) }, uniquingKeysWith: max)
    }
}

/** Floor plan picture. Its bottom-left corner sits at the grid origin. */
public struct FloorImage: Decodable, Equatable {
    public var file: String
    public var widthPx: Int
    public var heightPx: Int
    public var metresPerPx: Double
    public init(file: String, widthPx: Int, heightPx: Int, metresPerPx: Double) {
        self.file = file; self.widthPx = widthPx; self.heightPx = heightPx; self.metresPerPx = metresPerPx
    }

    /** Picture pixel (y grows down) of a site point, when the bottom-left corner is at [origin]. */
    public func toPx(_ p: Vec2, origin: Vec2) -> Vec2 {
        Vec2((p.x - origin.x) / metresPerPx, Double(heightPx) - (p.y - origin.y) / metresPerPx)
    }

    /** Scale from two picture pixels that are [lengthM] apart in real life. */
    public static func metresPerPx(_ a: Vec2, _ b: Vec2, lengthM: Double) -> Double? {
        let d = a.dist(b)
        if d < 1 || lengthM <= 0 { return nil }
        return lengthM / d
    }

    public var widthM: Double { Double(widthPx) * metresPerPx }
    public var heightM: Double { Double(heightPx) * metresPerPx }
}

public struct Site: Decodable, Equatable {
    public var id = "demo"
    public var name = "My place"
    public var version = 1
    public var grid = GridSpec()
    public var accessPoints: [AccessPoint] = []
    public var markers: [Marker] = []
    public var fingerprints: [Fingerprint] = []
    public var image: FloorImage?
    /** Which access points to use. Missing in older files: then only random-address hotspots are dropped. */
    public var apFilter = ApFilter()

    public init() {}

    /**
     * The same place with every access point the filter rejects taken out of the recordings.
     * [ssids] maps BSSID to Wi-Fi name, learned from scans (recordings do not store names).
     */
    public func filtered(_ f: ApFilter, ssids: [String: String] = [:]) -> Site {
        var s = self
        s.fingerprints = fingerprints.map { fp in
            var c = fp
            c.rssi = fp.rssi.filter { f.keeps($0.key, ssid: ssids[$0.key]) }
            c.std = fp.std.filter { c.rssi[$0.key] != nil }
            return c
        }
        return s
    }

    public static func load(_ data: Data) throws -> Site {
        try JSONDecoder().decode(Site.self, from: data)
    }

    public func marker(_ id: String) -> Marker? {
        markers.first { $0.id == id } ?? markers.first { $0.id.caseInsensitiveCompare(id) == .orderedSame }
    }

    /** Where a recording or marker id is, by marker id (falls back to the recording's own point). */
    public func position(of id: String) -> Vec2? {
        marker(id)?.pos ?? fingerprints.first { $0.markerId == id }?.pos
    }

    /** Friendly name of a marker or recording id. */
    public func displayName(_ id: String) -> String { marker(id)?.name ?? id }

    /** The marker nearest to a point, if any is within [withinM]. */
    public func nearestMarker(_ p: Vec2, withinM: Double = .infinity) -> Marker? {
        guard let m = markers.min(by: { $0.pos.dist(p) < $1.pos.dist(p) }), m.pos.dist(p) <= withinM else { return nil }
        return m
    }

    /** Markers that have a Wi-Fi recording: the only ones useful for calibration. */
    public var recordedMarkers: [Marker] {
        let ids = Set(fingerprints.map { $0.markerId })
        return markers.filter { ids.contains($0.id) }
    }

    /** Every BSSID in any recording. */
    public var knownBssids: Set<String> { Set(fingerprints.flatMap { $0.rssi.keys }) }

    enum K: String, CodingKey { case id, name, version, grid, accessPoints, markers, fingerprints, image, apFilter }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? id
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? name
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? version
        grid = try c.decodeIfPresent(GridSpec.self, forKey: .grid) ?? grid
        accessPoints = try c.decodeIfPresent([AccessPoint].self, forKey: .accessPoints) ?? []
        markers = try c.decodeIfPresent([Marker].self, forKey: .markers) ?? []
        fingerprints = try c.decodeIfPresent([Fingerprint].self, forKey: .fingerprints) ?? []
        image = try c.decodeIfPresent(FloorImage.self, forKey: .image)
        apFilter = (try? c.decodeIfPresent(ApFilter.self, forKey: .apFilter)) ?? ApFilter()
    }
}

/**
 * Which access points count. Phone and laptop hotspots move and switch off, so they spoil
 * recordings. Most use a random ("locally administered") address: the 0x02 bit of the first byte.
 */
public struct ApFilter: Decodable, Equatable {
    /** Drop BSSIDs with a random (locally administered) address. */
    public var dropRandom = true
    /** If not empty: keep only BSSIDs that start with one of these, e.g. "7c:b6:8d". */
    public var allowPrefixes: [String] = []
    /** If not empty: keep only access points with one of these Wi-Fi names. */
    public var allowSsids: [String] = []

    public init(dropRandom: Bool = true, allowPrefixes: [String] = [], allowSsids: [String] = []) {
        self.dropRandom = dropRandom
        self.allowPrefixes = allowPrefixes.map(Bssid.prefix).filter { !$0.isEmpty }
        self.allowSsids = allowSsids.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }

    public var hasAllowList: Bool { !allowPrefixes.isEmpty || !allowSsids.isEmpty }

    public static func isRandom(_ bssid: String) -> Bool {
        guard let first = UInt8(Bssid.normal(bssid).prefix(2), radix: 16) else { return false }
        return first & 0x02 != 0
    }

    /**
     * Should this access point be used? [ssid] is nil when the name is not known (recordings).
     * Same order as the phone and the server (docs/site-file.md): an allowed prefix wins, then
     * random addresses are dropped, then the Wi-Fi name list applies.
     */
    public func keeps(_ bssid: String, ssid: String?) -> Bool {
        let b = Bssid.normal(bssid)
        if allowPrefixes.contains(where: { b.hasPrefix($0) }) { return true }
        if dropRandom && Self.isRandom(b) { return false }
        if hasAllowList {
            guard let ssid else { return allowPrefixes.isEmpty }
            return allowSsids.contains { $0.caseInsensitiveCompare(ssid) == .orderedSame }
        }
        return true
    }

    /** "7c:b6:8d, Office WiFi" -> prefixes and Wi-Fi names. */
    public static func parse(_ text: String, dropRandom: Bool = true) -> ApFilter {
        var p: [String] = [], n: [String] = []
        for raw in text.split(separator: ",") {
            let t = raw.trimmingCharacters(in: .whitespaces)
            if t.isEmpty { continue }
            if t.range(of: #"^[0-9A-Fa-f]{2}([:-][0-9A-Fa-f]{2}){0,5}$"#, options: .regularExpression) != nil { p.append(t) } else { n.append(t) }
        }
        return ApFilter(dropRandom: dropRandom, allowPrefixes: p, allowSsids: n)
    }

    /** The allow list as one line of text, the opposite of [parse]. */
    public var text: String { (allowPrefixes + allowSsids).joined(separator: ", ") }

    enum K: String, CodingKey { case dropRandom, allowPrefixes, allowSsids }
    public init(from d: Decoder) throws {
        let c = try d.container(keyedBy: K.self)
        self.init(dropRandom: try c.decodeIfPresent(Bool.self, forKey: .dropRandom) ?? true,
                  allowPrefixes: try c.decodeIfPresent([String].self, forKey: .allowPrefixes) ?? [],
                  allowSsids: try c.decodeIfPresent([String].self, forKey: .allowSsids) ?? [])
    }
}

public enum Bssid {
    /** "7C-B6-8D" to "7c:b6:8d" (part of an address, any length). */
    public static func prefix(_ s: String) -> String {
        s.trimmingCharacters(in: .whitespaces).lowercased()
            .split(whereSeparator: { $0 == ":" || $0 == "-" })
            .map { $0.count == 1 ? "0" + $0 : String($0) }.joined(separator: ":")
    }

    /** "A:B:C:D:E:F" or "0a-0b-..." to "0a:0b:0c:0d:0e:0f". macOS sometimes drops leading zeros. */
    public static func normal(_ s: String) -> String {
        let parts = s.trimmingCharacters(in: .whitespaces).lowercased()
            .split(whereSeparator: { $0 == ":" || $0 == "-" })
        guard parts.count == 6 else { return s.lowercased() }
        return parts.map { $0.count == 1 ? "0" + $0 : String($0) }.joined(separator: ":")
    }
}
