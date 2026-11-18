# Shared core library

`core/` is a plain Kotlin/JVM library with **no Android imports**. Everything that is maths
or data format lives here, so it can be unit-tested in seconds without an emulator.

```bash
./gradlew :core:test      # no Android SDK needed
```

## Files

| File | What it does |
| --- | --- |
| `Site.kt` | Site model: grid, access points, markers, fingerprints, floor plan image, AP filter, marker naming. JSON via kotlinx.serialization. |
| `Trilateration.kt` | RTT trilateration: weighted Gauss-Newton with Huber down-weighting of long ranges. |
| `RssiPositioning.kt` | Fingerprint kNN, ambiguity ("runner-up") test, look-alike finder, weighted centroid fallback, scan averaging. |
| `Kalman2D.kt` | Constant-velocity Kalman filter with Mahalanobis gate and stationary mode. |
| `MapMatcher.kt` | Snap to aisle centrelines and cross-aisles with hysteresis; zones for open floors. |
| `PositionFuser.kt` | Glue: takes RTT results, scans, QR codes and motion state; returns one `Estimate`. |
| `Accuracy.kt` | `AccuracySample`, summary (mean, p50, p90, correct-aisle %), CSV. |
| `Protocol.kt` | MQTT topics and payloads (`PoseMsg`, `StatusMsg`, `DirectiveMsg`, `HeartbeatMsg`). |
| `DemoPickList.kt` | Pick targets for the traffic demo. |

## The `PositionFuser` API

The Android layer only feeds measurements and a clock:

```kotlin
val fuser = PositionFuser(site)

// every 100 ms
fuser.tick(nowMs)

// when Android delivers results
fuser.onRtt(rttResults, nowMs)          // List<RttMeasurement>
fuser.onScan(rssiByBssid, nowMs)        // Map<String, Double>, already AP-filtered
fuser.onQr(markerId, nowMs)             // hard reset at a surveyed marker
fuser.setStationary(still)              // from the motion detector

// read
val e: Estimate? = fuser.estimate(nowMs)
// e.snapped.pos, e.snapped.area ("A3" / "cross" / zone), e.sigmaM, e.source, e.unsureWith
```

Because the clock is passed in, tests can replay a recorded walk at any speed.

## Tests

`core/src/test/kotlin/com/swarmamr/core/`:

- `CoreTest.kt`: trilateration, Kalman gate, map matching, accuracy stats, protocol round trip.
- `FingerprintWalkTest.kt`: walk-and-record splitting and matching.
- `SurveyTest.kt`: marker naming, coverage and site edits.

See [Testing](../contributing/testing.md).
