import AppKit
import CoreLocation
import CoreWLAN
import Foundation
import LocateCore

/** One access point heard in a scan. */
struct HeardAP: Identifiable, Hashable {
    var bssid: String
    var ssid: String
    var rssi: Double
    var channel: Int
    var id: String { bssid }
}

struct ScanResult {
    var aps: [HeardAP] = []
    /** Networks macOS showed without their BSSID: Location permission is missing. */
    var hidden = 0
    /** Networks in the fresh scan (before adding macOS's recent results). */
    var fresh = 0
    var error: String?

    /** BSSID to dBm, strongest reading per BSSID. */
    var rssi: [String: Double] {
        Dictionary(aps.map { ($0.bssid, $0.rssi) }, uniquingKeysWith: max)
    }
}

/** Wi-Fi scans with Apple's CoreWLAN. A scan blocks for a second or two, so it runs off the main thread. */
final class WifiScanner {
    private let queue = DispatchQueue(label: "wifi-scan", qos: .userInitiated)

    func scan() async -> ScanResult {
        await withCheckedContinuation { cont in
            queue.async { cont.resume(returning: self.scanNow()) }
        }
    }

    private func scanNow() -> ScanResult {
        guard let iface = CWWiFiClient.shared().interface() else {
            return ScanResult(error: "This Mac has no Wi-Fi.")
        }
        if !iface.powerOn() {
            return ScanResult(error: "Wi-Fi is off. Turn it on in the menu bar.")
        }
        do {
            let fresh = try iface.scanForNetworks(withSSID: nil)
            // macOS often returns only part of what it hears in one scan. Its own recent results
            // (cachedScanResults) fill the gaps; a fresh reading wins for the same BSSID.
            let cached = iface.cachedScanResults() ?? []
            var r = ScanResult()
            r.fresh = fresh.count
            var seen = Set<String>()
            for n in Array(fresh) + Array(cached) {
                guard let b = n.bssid, !b.isEmpty else { r.hidden += 1; continue }
                let key = Bssid.normal(b)
                if !seen.insert(key).inserted { continue }
                r.aps.append(HeardAP(bssid: key, ssid: n.ssid ?? "", rssi: Double(n.rssiValue),
                                     channel: n.wlanChannel?.channelNumber ?? 0))
            }
            r.aps.sort { $0.rssi > $1.rssi }
            return r
        } catch {
            return ScanResult(error: "Wi-Fi scan failed: \(error.localizedDescription)")
        }
    }
}

/**
 * macOS shows the BSSID of each access point only to apps that have Location permission.
 * This asks for it once and tracks the answer.
 */
final class LocationGate: NSObject, ObservableObject, CLLocationManagerDelegate {
    enum State { case notAsked, allowed, denied }

    @Published var state: State = .notAsked
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        update()
    }

    func ask() {
        manager.requestWhenInUseAuthorization()
        // On some macOS versions the question only shows once the app asks for a location.
        manager.startUpdatingLocation()
    }

    func openSettings() {
        let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_LocationServices")!
        NSWorkspace.shared.open(url)
    }

    private func update() {
        let s = manager.authorizationStatus
        let next: State
        switch s {
        case .notDetermined: next = .notAsked
        case .denied, .restricted: next = .denied
        default: next = .allowed
        }
        if next == .allowed { manager.stopUpdatingLocation() }
        DispatchQueue.main.async { self.state = next }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) { update() }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        manager.stopUpdatingLocation()
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}
}
