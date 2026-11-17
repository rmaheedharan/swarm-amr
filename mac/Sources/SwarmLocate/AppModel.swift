import AppKit
import Foundation
import LocateCore
import UniformTypeIdentifiers

/** Everything the window shows. Scans Wi-Fi every few seconds and matches it to the place file. */
@MainActor
final class AppModel: ObservableObject {
    @Published var site: Site?
    @Published var siteFile: String = ""
    @Published var plan: NSImage?
    @Published var fix: Fix?
    @Published var heard: [HeardAP] = []
    @Published var hidden = 0
    @Published var lastScan: Date?
    @Published var scanCount = 0
    @Published var problem: String?
    @Published var note: String?
    @Published var paused = false
    @Published var selectedMarker: String = ""
    @Published var samples: [AccuracySample] = []
    @Published var mode: MatchMode = .auto {
        didSet { if !loading { saveCorrection() } ; relocate() }
    }
    @Published var offsetDb: Double = 0
    @Published var calibratedAt: String = ""
    @Published var interval: Double = 4 {
        didSet { prefs.set(interval, forKey: "interval") }
    }

    let location = LocationGate()
    private let scanner = WifiScanner()
    private let tracker = Tracker()
    private var loop: Task<Void, Never>?
    private var loading = false
    private let prefs = UserDefaults.standard

    init() {
        let i = prefs.double(forKey: "interval")
        if i >= 2 { interval = i }
        if let path = prefs.string(forKey: "lastSite"), FileManager.default.fileExists(atPath: path) {
            try? openSite(URL(fileURLWithPath: path))
        }
    }

    // MARK: Scanning

    func start() {
        guard loop == nil else { return }
        if location.state == .notAsked { location.ask() }
        loop = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                if !self.paused { await self.scanOnce() }
                let ns = UInt64(max(2, self.interval) * 1_000_000_000)
                try? await Task.sleep(nanoseconds: ns)
            }
        }
    }

    func scanOnce() async {
        let r = await scanner.scan()
        lastScan = Date()
        scanCount += 1
        hidden = r.hidden
        if let e = r.error {
            problem = e
            return
        }
        heard = r.aps
        if r.aps.isEmpty && r.hidden > 0 {
            problem = "macOS hides the Wi-Fi details from this app. Allow Location for SwarmLocate (button below)."
            return
        }
        if r.aps.isEmpty {
            problem = "No Wi-Fi heard."
            return
        }
        problem = nil
        if tracker.add(r.rssi) { note = "Wi-Fi changed a lot: looks like you moved. Starting fresh." } else { note = nil }
        relocate()
    }

    /** Match again with the current scans (after a change of setting). */
    func relocate() {
        guard let site else { fix = nil; return }
        fix = tracker.locate(site, mode: mode, offsetDb: offsetDb)
    }

    /** Forget the averaged scans, for example after carrying the laptop to another desk. */
    func freshStart() {
        tracker.clear()
        fix = nil
        note = "Cleared. The next scan starts a new average."
    }

    var scansAveraged: Int { tracker.scans.count }

    /** How many APs heard now also appear in the place file. */
    var knownHeard: Int {
        guard let site else { return 0 }
        let known = site.knownBssids
        return heard.filter { known.contains($0.bssid) }.count
    }

    // MARK: Place file

    func openSite(_ url: URL) throws {
        let s = try Site.load(Data(contentsOf: url))
        site = s
        siteFile = url.lastPathComponent
        prefs.set(url.path, forKey: "lastSite")
        loadCorrection(for: s)
        tracker.clear()
        fix = nil
        samples = []
        selectedMarker = s.recordedMarkers.first?.id ?? ""
        plan = nil
        if let p = prefs.string(forKey: "plan." + s.id), let img = NSImage(contentsOfFile: p) { plan = img }
        if plan == nil, s.image != nil {
            // The phone keeps the picture next to the place file under its own name; try that.
            let sibling = url.deletingLastPathComponent().appendingPathComponent(s.image!.file)
            plan = NSImage(contentsOf: sibling)
        }
        problem = nil
    }

    func chooseSiteFile() {
        let p = NSOpenPanel()
        p.title = "Open the place file from the phone"
        p.allowedContentTypes = [.json]
        p.allowsMultipleSelection = false
        guard p.runModal() == .OK, let url = p.url else { return }
        do { try openSite(url) } catch { alert("Could not read this file", "\(error.localizedDescription)\n\nUse Setup > Share place file on the phone, and open that .json file.") }
    }

    func downloadSite(server: String, id: String) async {
        var base = server.trimmingCharacters(in: .whitespacesAndNewlines)
        if !base.lowercased().hasPrefix("http") { base = "http://" + base }
        while base.hasSuffix("/") { base.removeLast() }
        guard let url = URL(string: "\(base)/api/sites/\(id.trimmingCharacters(in: .whitespaces))") else {
            alert("That address does not look right", base); return
        }
        do {
            let (data, resp) = try await URLSession.shared.data(from: url)
            if let h = resp as? HTTPURLResponse, h.statusCode != 200 { throw URLError(.badServerResponse, userInfo: [NSLocalizedDescriptionKey: "Server answered \(h.statusCode)"]) }
            _ = try Site.load(data)
            let dir = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("SwarmLocate", isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let file = dir.appendingPathComponent("site-\(id).json")
            try data.write(to: file)
            try openSite(file)
        } catch {
            alert("Could not get the place from the server", error.localizedDescription)
        }
    }

    func choosePlanPicture() {
        guard let site else { return }
        let p = NSOpenPanel()
        p.title = "Choose the floor plan picture used on the phone"
        p.allowedContentTypes = [.image]
        guard p.runModal() == .OK, let url = p.url, let img = NSImage(contentsOf: url) else { return }
        plan = img
        prefs.set(url.path, forKey: "plan." + site.id)
    }

    // MARK: Signal correction

    private func loadCorrection(for s: Site) {
        loading = true
        offsetDb = prefs.double(forKey: "offset." + s.id)
        calibratedAt = prefs.string(forKey: "calibratedAt." + s.id) ?? ""
        mode = MatchMode(rawValue: prefs.string(forKey: "mode." + s.id) ?? "") ?? (calibratedAt.isEmpty ? .auto : .offset)
        loading = false
    }

    private func saveCorrection() {
        guard let site else { return }
        prefs.set(offsetDb, forKey: "offset." + site.id)
        prefs.set(calibratedAt, forKey: "calibratedAt." + site.id)
        prefs.set(mode.rawValue, forKey: "mode." + site.id)
    }

    /** You stand at a recorded spot: measure how much weaker or stronger the Mac hears than the phone did. */
    func calibrate() {
        guard let site, let m = site.marker(selectedMarker) else { return }
        guard let fp = site.fingerprints.first(where: { $0.markerId == m.id }) else {
            alert("No recording at \(m.name)", "Pick a spot that the phone recorded (shown with a ring on the map)."); return
        }
        guard tracker.scans.count > 0 else { alert("No scan yet", "Wait for a Wi-Fi scan, then try again."); return }
        guard let o = Matcher.offset(tracker.averaged, fp) else {
            alert("Too few access points in common", "The Mac and the phone recording share fewer than 3 access points here. Try another spot."); return
        }
        offsetDb = (o.db * 10).rounded() / 10
        calibratedAt = m.name
        loading = true; mode = .offset; loading = false
        saveCorrection()
        relocate()
        note = String(format: "Correction set: %+.1f dB from %d access points at %@.", offsetDb, o.common, m.name)
    }

    // MARK: Accuracy check

    func recordTest() {
        guard let site, let m = site.marker(selectedMarker) else { return }
        guard let fix else { alert("No position yet", "Wait until the app shows where you are."); return }
        samples.append(AccuracySample(
            time: Date(), markerId: m.id, truth: m.pos, est: fix.pos, sigmaM: fix.sigmaM, apsUsed: knownHeard,
            truthArea: site.grid.area(m.pos), estArea: site.grid.area(fix.pos), mode: mode))
        note = String(format: "Saved: %.1f m off at %@.", fix.pos.dist(m.pos), m.name)
    }

    func saveCsv() {
        let p = NSSavePanel()
        p.allowedContentTypes = [.commaSeparatedText]
        p.nameFieldStringValue = "mac-accuracy-\(Int(Date().timeIntervalSince1970)).csv"
        guard p.runModal() == .OK, let url = p.url else { return }
        do { try Accuracy.csv(samples, device: Host.current().localizedName ?? "mac").write(to: url, atomically: true, encoding: .utf8) }
        catch { alert("Could not save", error.localizedDescription) }
    }

    /** Pick the recorded spot nearest to a tap on the map. */
    func tap(_ p: Vec2) {
        guard let site else { return }
        let pool = site.recordedMarkers.isEmpty ? site.markers : site.recordedMarkers
        if let m = pool.min(by: { $0.pos.dist(p) < $1.pos.dist(p) }) { selectedMarker = m.id }
    }

    private func alert(_ title: String, _ text: String) {
        let a = NSAlert()
        a.messageText = title
        a.informativeText = text
        a.runModal()
    }
}
