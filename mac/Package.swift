// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "SwarmLocate",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "SwarmLocate", targets: ["SwarmLocate"]),
        .library(name: "LocateCore", targets: ["LocateCore"]),
    ],
    targets: [
        // Site file and Wi-Fi matching. Foundation only, so it is easy to test.
        .target(name: "LocateCore"),
        // The Mac app: CoreWLAN scans, Location permission, SwiftUI window.
        .executableTarget(name: "SwarmLocate", dependencies: ["LocateCore"]),
        .testTarget(name: "LocateCoreTests", dependencies: ["LocateCore"]),
    ]
)
