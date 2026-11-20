# Homebrew cask for the SwarmLocate Mac app.
#   brew tap rmaheedharan/swarm-amr https://github.com/rmaheedharan/swarm-amr
#   brew install --cask swarmlocate
# The app has no paid Apple signature, so after installing we remove the download
# "quarantine" flag. Then macOS opens it without the "Open Anyway" step.
# Needs a recent Homebrew (run `brew update` first).
cask "swarmlocate" do
  version "0.4.0"
  sha256 "612711fd9ccbe442c3fdeb09ad22a870cd322b764ce9378af44efe188715e3c1"

  url "https://github.com/rmaheedharan/swarm-amr/releases/download/mac-v#{version}/SwarmLocate-mac-#{version}.zip"
  name "SwarmLocate"
  desc "Shows where your Mac is on the floor plan, from Swarm AMR Wi-Fi recordings"
  homepage "https://github.com/rmaheedharan/swarm-amr/tree/main/mac"

  depends_on macos: :ventura

  app "SwarmLocate.app"

  postflight_steps do
    run "/usr/bin/xattr",
        args:         ["-dr", "com.apple.quarantine", "{{appdir}}/SwarmLocate.app"],
        must_succeed: false
  end

  zap trash: [
    "~/Library/Application Support/SwarmLocate",
    "~/Library/Preferences/com.swarmamr.locate.plist",
  ]
end
