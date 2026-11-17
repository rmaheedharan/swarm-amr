import Foundation

/**
 * How to compare a MacBook scan with recordings made by a phone.
 * A MacBook antenna hears Wi-Fi differently from a phone, often 5 to 10 dB weaker or stronger.
 */
public enum MatchMode: String, CaseIterable, Identifiable {
    /** Add one fixed number of dB, measured once at a known spot ("Calibrate here"). Best after calibration. */
    case offset
    /** Guess the dB gap again for every recording (the middle value of the differences). No calibration needed. */
    case auto
    /** Only compare which APs are stronger than which. Ignores the dB gap completely. Roughest. */
    case rank

    public var id: String { rawValue }
    public var title: String {
        switch self {
        case .offset: return "Fixed correction"
        case .auto: return "Automatic correction"
        case .rank: return "Order only"
        }
    }
}

/** One position from Wi-Fi. */
public struct Fix {
    public var pos: Vec2
    public var sigmaM: Double
    /** How different the scan is from the best recording (dB for the dB modes). Lower is better. */
    public var bestDistance: Double
    public var best: Fingerprint
    /** Neighbours that were averaged. */
    public var used: Int
    /** A recording far away that matches almost as well: you may be there instead. */
    public var runnerUp: Fingerprint?
}

/**
 * Wi-Fi fingerprint matching. The dB distance, the neighbour weighting and the "unsure"
 * test are the same as core/.../RssiPositioning.kt in the Android app.
 */
public enum Matcher {
    public static let missingDbm = -100.0
    static let minCommon = 3
    static let nearDb = 3.0
    static let kernelDb = 1.0
    public static let steadyDb = 4.0
    static let maxDiffDb = 20.0
    /** The automatic correction never moves a scan by more than this. */
    public static let maxAutoOffsetDb = 15.0

    /** How different a scan is from a recording, in dB (root mean square over the APs). Same as the app. */
    public static func distance(_ scan: [String: Double], _ fp: Fingerprint) -> Double? {
        let common = fp.rssi.keys.filter { scan[$0] != nil }.count
        if common < minCommon { return nil }
        let keys = Set(fp.rssi.keys).union(scan.keys)
        var d2 = 0.0
        for b in keys {
            let a = scan[b] ?? missingDbm
            let c = fp.rssi[b] ?? missingDbm
            let d = min(max(a - c, -maxDiffDb), maxDiffDb)
            let k = steadyDb / max(steadyDb, fp.std[b] ?? steadyDb)
            d2 += d * d * k * k
        }
        return (d2 / Double(keys.count)).squareRoot()
    }

    /** Add [db] to every AP in a scan. */
    public static func shift(_ scan: [String: Double], by db: Double) -> [String: Double] {
        db == 0 ? scan : scan.mapValues { $0 + db }
    }

    /**
     * The dB to add to this scan so it looks like the phone recording: the middle value of
     * (phone - Mac) over the APs both heard. Nil if fewer than 3 APs in common.
     */
    public static func offset(_ scan: [String: Double], _ fp: Fingerprint) -> (db: Double, common: Int)? {
        let diffs = fp.rssi.compactMap { b, v in scan[b].map { v - $0 } }
        guard diffs.count >= minCommon else { return nil }
        return (median(diffs), diffs.count)
    }

    /**
     * Order-only distance, 0 (same order) to about 20 (nothing alike). Compares the order of
     * the APs both heard, plus how many strong APs only one side heard.
     */
    public static func rankDistance(_ scan: [String: Double], _ fp: Fingerprint) -> Double? {
        let common = fp.rssi.keys.filter { scan[$0] != nil }.sorted()
        let n = common.count
        if n < minCommon { return nil }
        let rs = ranks(common.map { scan[$0]! })
        let rf = ranks(common.map { fp.rssi[$0]! })
        var foot = 0.0
        for i in 0..<n { foot += abs(rs[i] - rf[i]) }
        let maxFoot = Double((n * n) / 2)
        let order = maxFoot > 0 ? foot / maxFoot : 0
        let strong = -75.0
        let onlyPhone = fp.rssi.filter { scan[$0.key] == nil && $0.value >= strong }.count
        let onlyMac = scan.filter { fp.rssi[$0.key] == nil && $0.value >= strong }.count
        let missing = Double(onlyPhone + onlyMac) / Double(n + onlyPhone + onlyMac)
        return 10 * order + 10 * missing
    }

    /** Distance of a scan to one recording with the chosen correction. */
    public static func score(_ scan: [String: Double], _ fp: Fingerprint, mode: MatchMode, offsetDb: Double) -> Double? {
        switch mode {
        case .offset:
            return distance(shift(scan, by: offsetDb), fp)
        case .auto:
            guard let o = offset(scan, fp) else { return nil }
            let db = min(max(o.db, -maxAutoOffsetDb), maxAutoOffsetDb)
            return distance(shift(scan, by: db), fp)
        case .rank:
            return rankDistance(scan, fp)
        }
    }

    /** All recordings, best match first. */
    public static func rank(_ scan: [String: Double], _ prints: [Fingerprint], mode: MatchMode = .offset, offsetDb: Double = 0) -> [(Fingerprint, Double)] {
        prints.compactMap { fp in score(scan, fp, mode: mode, offsetDb: offsetDb).map { (fp, $0) } }
            .sorted { $0.1 < $1.1 }
    }

    /** Weighted average of the best few recordings, like the app. */
    public static func fromRanked(_ scored: [(Fingerprint, Double)], k: Int = 3) -> Fix? {
        guard let first = scored.first else { return nil }
        let best = first.1
        let near = scored.prefix(k).filter { $0.1 - best <= nearDb }
        let w = near.map { exp(-0.5 * pow(($0.1 - best) / kernelDb, 2)) }
        let sw = w.reduce(0, +)
        var x = 0.0, y = 0.0
        for (i, n) in near.enumerated() { x += n.0.x * w[i]; y += n.0.y * w[i] }
        let pos = Vec2(x / sw, y / sw)
        var spread = 0.0
        for (i, n) in near.enumerated() { spread += w[i] * pow(n.0.pos.dist(pos), 2) }
        spread /= sw
        let v = max(spread, 1.2 * 1.2)
        return Fix(pos: pos, sigmaM: v.squareRoot(), bestDistance: best, best: first.0, used: near.count,
                   runnerUp: runnerUp(scored))
    }

    /** A recording at least [apartM] from the best one that matches almost as well, or nil. */
    public static func runnerUp(_ scored: [(Fingerprint, Double)], withinDb: Double = 1.5, apartM: Double = 4.0) -> Fingerprint? {
        guard scored.count >= 2 else { return nil }
        let (best, d0) = scored[0]
        return scored.dropFirst().first { $0.1 - d0 <= withinDb && $0.0.pos.dist(best.pos) >= apartM }?.0
    }

    /** Average scans into one, keeping APs heard in at least [minSeen] of them. */
    public static func mean(_ scans: [[String: Double]], minSeen: Double = 0.5) -> [String: Double] {
        var sums: [String: (Double, Int)] = [:]
        for s in scans { for (b, v) in s { let o = sums[b] ?? (0, 0); sums[b] = (o.0 + v, o.1 + 1) } }
        var out: [String: Double] = [:]
        for (b, (sum, n)) in sums where Double(n) >= minSeen * Double(scans.count) { out[b] = sum / Double(n) }
        return out
    }

    public static func median(_ v: [Double]) -> Double {
        let s = v.sorted()
        if s.isEmpty { return 0 }
        return s.count % 2 == 1 ? s[s.count / 2] : (s[s.count / 2 - 1] + s[s.count / 2]) / 2
    }

    /** 1 = strongest. Ties share the average rank. */
    static func ranks(_ v: [Double]) -> [Double] {
        let order = v.indices.sorted { v[$0] > v[$1] }
        var r = [Double](repeating: 0, count: v.count)
        var i = 0
        while i < order.count {
            var j = i
            while j + 1 < order.count && v[order[j + 1]] == v[order[i]] { j += 1 }
            let avg = Double(i + j) / 2 + 1
            for t in i...j { r[order[t]] = avg }
            i = j + 1
        }
        return r
    }
}

/**
 * Keeps the last few scans and turns them into a position. A laptop usually sits still, so
 * averaging a few scans removes much of the noise. When the Wi-Fi suddenly looks different
 * (the laptop was carried somewhere else), the old scans are dropped.
 */
public final class Tracker {
    public var maxScans: Int
    public var maxAge: TimeInterval
    public var movedDb: Double
    public private(set) var scans: [(Date, [String: Double])] = []

    public init(maxScans: Int = 5, maxAge: TimeInterval = 45, movedDb: Double = 10) {
        self.maxScans = maxScans; self.maxAge = maxAge; self.movedDb = movedDb
    }

    /** Add a scan. Returns true when it looked like the laptop moved, and old scans were dropped. */
    @discardableResult
    public func add(_ scan: [String: Double], at t: Date = Date()) -> Bool {
        var moved = false
        if !scans.isEmpty {
            // Compare only APs heard in both: weak APs come and go between scans anyway.
            let before = averaged
            let diffs = scan.compactMap { b, v in before[b].map { v - $0 } }
            let rms = diffs.isEmpty ? Double.infinity : (diffs.map { $0 * $0 }.reduce(0, +) / Double(diffs.count)).squareRoot()
            // Only a clear change counts as a move. A Mac scan often returns just part of the
            // access points (a few channels at a time), so few APs in common is normal, not a move.
            if diffs.count >= 3 && rms > movedDb { scans.removeAll(); moved = true }
        }
        scans.append((t, scan))
        scans.removeAll { t.timeIntervalSince($0.0) > maxAge }
        while scans.count > maxScans { scans.removeFirst() }
        return moved
    }

    public func clear() { scans.removeAll() }

    public var averaged: [String: Double] {
        // Keep an AP heard in any of the recent scans: partial scans add up to a full picture.
        scans.count == 1 ? scans[0].1 : Matcher.mean(scans.map { $0.1 }, minSeen: 0)
    }

    /** Most access points the averaged scan shares with any one recording (3 are needed). */
    public func bestCommon(_ site: Site) -> Int {
        let a = averaged
        return site.fingerprints.map { fp in fp.rssi.keys.filter { a[$0] != nil }.count }.max() ?? 0
    }

    public func locate(_ site: Site, mode: MatchMode, offsetDb: Double) -> Fix? {
        guard !scans.isEmpty else { return nil }
        return Matcher.fromRanked(Matcher.rank(averaged, site.fingerprints, mode: mode, offsetDb: offsetDb))
    }
}

/** One accuracy check: where the Mac said it was while it stood on a known marker. */
public struct AccuracySample {
    public var time: Date
    public var markerId: String
    public var truth: Vec2
    public var est: Vec2
    public var sigmaM: Double
    public var apsUsed: Int
    public var truthArea: String
    public var estArea: String
    public var mode: MatchMode
    public var errorM: Double { truth.dist(est) }
    public var areaCorrect: Bool { truthArea == estArea }

    public init(time: Date, markerId: String, truth: Vec2, est: Vec2, sigmaM: Double, apsUsed: Int,
                truthArea: String, estArea: String, mode: MatchMode) {
        self.time = time; self.markerId = markerId; self.truth = truth; self.est = est; self.sigmaM = sigmaM
        self.apsUsed = apsUsed; self.truthArea = truthArea; self.estArea = estArea; self.mode = mode
    }
}

public struct AccuracySummary {
    public var samples = 0
    public var p50M = 0.0
    public var p90M = 0.0
    public var maxM = 0.0
    public var correctAreaPct = 0.0
}

public enum Accuracy {
    /** Same columns as the Android app, so tools/accuracy_report.py reads both. */
    public static let csvHeader = "time_ms,marker,truth_x,truth_y,est_x,est_y,error_m,source,aps_used,sigma_m,truth_aisle,est_aisle,aisle_ok,device"

    /** Nearest-rank percentile of a sorted list (same as the app and the server). */
    public static func percentile(_ sorted: [Double], _ pct: Double) -> Double {
        if sorted.isEmpty { return 0 }
        let rank = clampInt(Int((pct / 100 * Double(sorted.count)).rounded(.up)), 1, sorted.count)
        return sorted[rank - 1]
    }

    public static func summarise(_ s: [AccuracySample]) -> AccuracySummary {
        if s.isEmpty { return AccuracySummary() }
        let e = s.map { $0.errorM }.sorted()
        return AccuracySummary(samples: s.count, p50M: percentile(e, 50), p90M: percentile(e, 90), maxM: e.last!,
                               correctAreaPct: 100 * Double(s.filter { $0.areaCorrect }.count) / Double(s.count))
    }

    public static func csv(_ s: [AccuracySample], device: String = "mac") -> String {
        func f3(_ v: Double) -> String { String(format: "%.3f", v) }
        func clean(_ t: String) -> String { t.replacingOccurrences(of: ",", with: " ") }
        var out = csvHeader + "\n"
        for r in s {
            out += [
                String(Int64(r.time.timeIntervalSince1970 * 1000)), clean(r.markerId), f3(r.truth.x), f3(r.truth.y),
                f3(r.est.x), f3(r.est.y), f3(r.errorM), "MAC_" + r.mode.rawValue.uppercased(), String(r.apsUsed),
                f3(r.sigmaM), r.truthArea, r.estArea, r.areaCorrect ? "1" : "0", clean(device),
            ].joined(separator: ",") + "\n"
        }
        return out
    }
}
