# Contributing

Thanks for helping. The full guide is on the docs site:
**https://rmaheedharan.github.io/swarm-amr/contributing/**

Short version:

1. Fork, then `git clone` your fork and branch from `main`.
2. Make the change with tests. Keep positioning maths in `core/`.
3. Run the checks for what you touched:
   - `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug`
   - `cd backend && python -m pytest -q`
   - `cd mac && swift test`
   - `mkdocs build --strict` (docs)
4. If you change the site file or MQTT messages, change the Kotlin, Python and Swift copies
   together.
5. Open a pull request against `main` with what changed, why, and how you tested it.

Never report accuracy numbers that were not measured on a real floor.
