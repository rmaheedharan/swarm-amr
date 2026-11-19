# Homebrew cask for the SwarmLocate Mac app.
#   brew tap rmaheedharan/swarm-amr https://github.com/rmaheedharan/swarm-amr
#   brew install --cask swarmlocate
# The app has no paid Apple signature, so after installing we remove the download
# "quarantine" flag. Then macOS opens it without the "Open Anyway" step.
cask "swarmlocate" do
  version "0.3.1"
  sha256 "f03ff95d0be651b2e5f7a4bf26f51b96f86affe0f76a7b5460703a0cc0a4a23c"

  url "https://github.com/rmaheedharan/swarm-amr/releases/download/mac-v#{version}/SwarmLocate-mac-#{version}.zip"
  name "SwarmLocate"
  desc "Shows where your Mac is on the floor plan, from Swarm AMR Wi-Fi recordings"
  homepage "https://github.com/rmaheedharan/swarm-amr/tree/main/mac"

  depends_on macos: ">= :ventura"

  app "SwarmLocate.app"

  postflight do
    system_command "/usr/bin/xattr",
                   args: ["-dr", "com.apple.quarantine", "#{appdir}/SwarmLocate.app"]
  end

  zap trash: [
    "~/Library/Application Support/SwarmLocate",
    "~/Library/Preferences/com.swarmamr.locate.plist",
  ]
end
