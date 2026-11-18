# Releases

## Android

| Trigger | Result |
| --- | --- |
| Push to `main` | CI builds and tests; the APK is under **Actions > Android > run > Artifacts**. |
| Push a tag `v*` (for example `v0.8.0`) | Also creates a **GitHub Release** with `swarm-amr-<version>.apk`. |
| **Actions > Android > Run workflow** with a release tag | Same as a tag push (the action creates the tag). |

```bash
git tag v0.8.0
git push origin v0.8.0
```

**Signing.** Without secrets, the APK is signed with the public demo key
(`app/demo-signing.jks`) so updates install over each other. That is fine for a demo, not for
production. For your own key, add these repository secrets: `SIGNING_KEYSTORE_BASE64`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

The version name comes from the tag (`v0.8.0` → `0.8.0`); the version code is the CI run number.

## Mac

Run **Actions > Mac > Run workflow** and type a tag such as `mac-v0.3.0`. The release gets
`SwarmLocate-mac-<version>.zip`. When run from `main`, the workflow also updates `version`
and `sha256` in the Homebrew cask (`Casks/swarmlocate.rb`), so `brew upgrade --cask
swarmlocate` picks up the new version.

## Docs

The **Docs** workflow builds the site on every pull request and publishes it to GitHub Pages
on every push to `main`. Nothing to do by hand.

## Versioning

- Android and backend: `vMAJOR.MINOR.PATCH`.
- Mac app: `mac-vMAJOR.MINOR.PATCH`.
- Bump MINOR for new features, PATCH for fixes. Note breaking site file or protocol changes in
  the release notes.
