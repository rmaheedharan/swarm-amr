# Testing

Run the checks for the parts you changed before you open a pull request. CI runs the same
commands.

| Part | Command | CI workflow |
| --- | --- | --- |
| Core | `./gradlew :core:test` | Android |
| Android | `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug` | Android |
| Backend | `cd backend && python -m pytest -q` | Backend (also builds the Docker image) |
| Mac | `cd mac && swift test` | Mac |
| Docs | `mkdocs build --strict` | Docs |

## What to test

- **Positioning maths:** add a JVM test in `core/src/test`. Use synthetic sites (a few APs at
  known positions) and assert on error in metres. Pass the clock in; never sleep.
- **Engine and coordinator:** `backend/tests/test_server.py` drives the `Coordinator` with fake
  MQTT messages and a fake clock. Add a case for any new cue rule.
- **Protocol:** when you add a field, add a round-trip test on both sides.
- **Mac matcher:** `mac/Tests/LocateCoreTests/MatcherTests.swift`; keep the same cases as the
  Kotlin tests where the algorithm is shared.

## Field testing

Unit tests cannot prove positioning works. For changes to positioning, run the
[accuracy test](../guide/accuracy-test.md) on a real floor before and after, and put both
summaries in the pull request.
