# Homebrew cask for the SwarmLocate Mac app.
#   brew tap rmaheedharan/swarm-amr https://github.com/rmaheedharan/swarm-amr
#   brew install --cask swarmlocate
# The app has no paid Apple signature, so after installing we remove the download
# "quarantine" flag. Then macOS opens it without the "Open Anyway" step.
cask "swarmlocate" do
  version "0.2.1"
  sha256 "ba915d4d8352f7b8aa406e66244fc427a8ed90d4585a1abbcaf40d23fbad4db3"

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
