import AppKit
import Foundation
import SwiftUI

struct MusicSnapshot: Decodable {
    var title = ""
    var artist = ""
    var artworkURL: String? = nil
    var state = "stopped"
    var position: Double = 0
    var duration: Double = 0
    var playing: Bool { state == "playing" }
    var hasMedia: Bool { !title.isEmpty && ["playing", "paused"].contains(state) }
    var progress: Double { duration > 0 ? max(0, min(1, position / duration)) : 0 }
}

/// Uses the players' public scripting dictionaries; never reads browser history
/// or uses private MediaRemote APIs. Automation starts only after Connect.
final class LauncherMusic: ObservableObject {
    enum Player: String, CaseIterable {
        case music = "com.apple.Music", spotify = "com.spotify.client"
        var title: String { self == .music ? "Apple Music" : "Spotify" }
    }
    enum Action: String { case toggle = "playpause", previous = "previousTrack", next = "nextTrack" }
    @Published var player: Player? = UserDefaults.standard.string(forKey: "launcherMusicPlayer").flatMap(Player.init(rawValue:))
    @Published var snapshot = MusicSnapshot()
    @Published var artwork: NSImage?
    private var artworkKey = ""
    private var artworkTask: URLSessionDataTask?
    @Published var message = "Connect a player"
    @Published var available = false
    @Published var busy = false
    private var pending = 0
    private var denied = false
    private var revision = 0
    private var timer: Timer?
    private let queue = DispatchQueue(label: "local.toby.curtainwallpaper.music", qos: .utility)
    private let artworkQueue = DispatchQueue(label: "local.toby.curtainwallpaper.music.artwork", qos: .utility)
    var shouldPoll: () -> Bool = { true }

    func start() {
        guard timer == nil else { refresh(); return }
        let timer = Timer(timeInterval: 2, repeats: true) { [weak self] _ in self?.refresh() }
        self.timer = timer
        RunLoop.main.add(timer, forMode: .common)
        refresh()
    }
    func stop() { timer?.invalidate(); timer = nil }
    func connect(_ value: Player?) {
        revision += 1
        player = value; snapshot = MusicSnapshot(); denied = false; available = false
        artworkTask?.cancel(); artwork = nil; artworkKey = ""
        if let value { UserDefaults.standard.set(value.rawValue, forKey: "launcherMusicPlayer") }
        else { UserDefaults.standard.removeObject(forKey: "launcherMusicPlayer") }
        message = value == nil ? "Connect a player" : "Start music in \(value!.title)"
        refresh()
    }
    func refresh() {
        guard !busy, !denied, shouldPoll(), let player else { return }
        guard NSWorkspace.shared.runningApplications.contains(where: { $0.bundleIdentifier == player.rawValue }) else {
            available = false; snapshot = MusicSnapshot(); artwork = nil; artworkKey = ""; message = "Open \(player.title) to listen"; return
        }
        execute(player: player, action: nil)
    }
    func control(_ action: Action) {
        guard available, pending < 4, let player else { return }
        execute(player: player, action: action)
    }
    func openPlayer() {
        guard let player, let url = NSWorkspace.shared.urlForApplication(withBundleIdentifier: player.rawValue) else { return }
        NSWorkspace.shared.openApplication(at: url, configuration: NSWorkspace.OpenConfiguration())
    }
    // Inputs are enums, never track titles or other external text.
    static func script(player: Player, action: Action?) -> String {
        let command = action.map { "app.\($0.rawValue)();" } ?? ""
        return """
        const app = Application('\(player.rawValue)');
        \(command)
        const result = {title:'', artist:'', state:String(app.playerState()), position:0, duration:0};
        try {
          const track = app.currentTrack();
          result.title = String(track.name());
          result.artist = String(track.artist());
          result.duration = Number(track.duration()) / \(player == .spotify ? "1000" : "1");
          result.position = Number(app.playerPosition());
          try { result.artworkURL = String(track.artworkUrl()); } catch (_) {}
        } catch (_) {}
        JSON.stringify(result);
        """
    }
    private func updateArtwork(_ snapshot: MusicSnapshot, player: Player) {
        guard available else { artworkTask?.cancel(); artwork = nil; artworkKey = ""; return }
        let key = "\(player.rawValue)|\(snapshot.title)|\(snapshot.artist)|\(snapshot.artworkURL ?? "")"
        guard key != artworkKey else { return }
        artworkKey = key; artworkTask?.cancel(); artwork = nil
        guard available else { return }
        if player == .spotify, let value = snapshot.artworkURL, let url = URL(string: value), url.scheme == "https" {
            var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
            request.httpShouldHandleCookies = false
            artworkTask = URLSession.shared.dataTask(with: request) { [weak self] data, _, _ in
                guard let data, data.count < 8_000_000, let image = NSImage(data: data) else { return }
                DispatchQueue.main.async { [weak self] in
                    guard let self, self.artworkKey == key else { return }
                    self.artwork = image
                }
            }
            artworkTask?.resume()
        } else if player == .music {
            artworkQueue.async { [weak self] in
                let url = FileManager.default.temporaryDirectory.appendingPathComponent("arlecchino-art-" + UUID().uuidString)
                defer { try? FileManager.default.removeItem(at: url) }
                let path = url.path.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "\"", with: "\\\"")
                let script = """
                set outputFile to POSIX file "\(path)"
                tell application id "com.apple.Music" to set coverData to raw data of artwork 1 of current track
                set handle to open for access outputFile with write permission
                try
                    set eof handle to 0
                    write coverData to handle
                    close access handle
                on error
                    try
                        close access handle
                    end try
                end try
                """
                let process = Process()
                process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
                process.arguments = ["-e", script]
                process.standardOutput = FileHandle.nullDevice; process.standardError = FileHandle.nullDevice
                do {
                    try process.run()
                    let timeout = DispatchWorkItem { if process.isRunning { process.terminate() } }
                    DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 5, execute: timeout)
                    process.waitUntilExit(); timeout.cancel()
                    guard process.terminationStatus == 0, let data = try? Data(contentsOf: url), data.count < 8_000_000, let image = NSImage(data: data) else { return }
                    DispatchQueue.main.async { [weak self] in
                        guard let self, self.artworkKey == key else { return }
                        self.artwork = image
                    }
                } catch {}
            }
        }
    }
    private func execute(player: Player, action: Action?) {
        pending += 1; busy = true
        let token = revision
        let source = Self.script(player: player, action: action)
        queue.async { [weak self] in
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
            process.arguments = ["-l", "JavaScript", "-e", source]
            let output = Pipe(), errors = Pipe()
            process.standardOutput = output; process.standardError = errors
            var snapshot: MusicSnapshot?
            var failure = ""
            do {
                try process.run()
                let timeout = DispatchWorkItem { if process.isRunning { process.terminate() } }
                DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 20, execute: timeout)
                // Read while the child runs, so unusually long metadata cannot fill the pipe.
                let data = output.fileHandleForReading.readDataToEndOfFile()
                process.waitUntilExit(); timeout.cancel()
                if process.terminationStatus == 0 { snapshot = try JSONDecoder().decode(MusicSnapshot.self, from: data) }
                else { failure = String(data: errors.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? "" }
            } catch { failure = "Player unavailable" }
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.pending -= 1; self.busy = self.pending > 0
                guard self.revision == token else { self.refresh(); return }
                if let snapshot {
                    self.snapshot = snapshot; self.available = snapshot.hasMedia
                    self.updateArtwork(snapshot, player: player)
                    self.message = "Start music in \(player.title)"
                } else {
                    self.available = false; self.artwork = nil; self.artworkKey = ""
                    self.denied = failure.contains("1743") || failure.contains("not authorized") || failure.contains("not permitted")
                    self.message = self.denied ? "Allow access in Privacy & Security → Automation" : "Couldn't connect · choose the player to retry"
                }
            }
        }
    }
}

struct MusicConnectionSettings: View {
    @ObservedObject var music: LauncherMusic
    var body: some View {
        HStack {
            Text("Music player")
            Spacer()
            Menu(music.player?.title ?? "Connect…") {
                ForEach(LauncherMusic.Player.allCases, id: \.rawValue) { player in
                    Button(player.title) { music.connect(player) }
                }
                if music.player != nil { Button("Disconnect") { music.connect(nil) } }
            }.fixedSize()
        }
        if music.player != nil {
            Text(music.available ? "The player appears on Favorites while media is active or paused." : music.message).font(.caption).foregroundStyle(.secondary)
        }
    }
}

struct MusicPlayerView: View {
    @ObservedObject var music: LauncherMusic
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        Group {
            if music.available {
                HStack(spacing: 16) {
                    Button { music.openPlayer() } label: {
                        Group {
                            if let artwork = music.artwork { Image(nsImage: artwork).resizable().scaledToFill() }
                            else { Image(systemName: "music.note").font(.system(size: 24, weight: .ultraLight)).frame(maxWidth: .infinity, maxHeight: .infinity).background(.white.opacity(0.05)) }
                        }.frame(width: 76, height: 76).clipShape(RoundedRectangle(cornerRadius: 6))
                    }.buttonStyle(.plain).accessibilityLabel("Album artwork · open player")
                    VStack(alignment: .leading, spacing: 3) {
                        Button { music.openPlayer() } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(music.snapshot.title).font(.system(size: 14)).lineLimit(1)
                                Text(music.snapshot.artist).font(.system(size: 12)).foregroundStyle(.white.opacity(0.75)).lineLimit(1)
                            }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                        }.buttonStyle(.plain)
                        HStack {
                            transport("backward.end", "Previous track", .previous, prominent: false)
                            Spacer(minLength: 0)
                            transport(music.snapshot.playing ? "pause" : "play", music.snapshot.playing ? "Pause" : "Play", .toggle, prominent: true)
                            Spacer(minLength: 0)
                            transport("forward.end", "Next track", .next, prominent: false)
                        }
                    }
                }.padding(.horizontal, 10).padding(.vertical, 8)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
            }
        }.animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: music.available)
    }
    private func transport(_ symbol: String, _ label: String, _ action: LauncherMusic.Action, prominent: Bool) -> some View {
        Button { music.control(action) } label: {
            Image(systemName: symbol).font(.system(size: prominent ? 18 : 13, weight: .light))
                .frame(width: 38, height: 38)
                .overlay(Circle().stroke(.white.opacity(prominent ? 0.85 : 0), lineWidth: 1.3))
                .contentShape(Rectangle())
        }.buttonStyle(.plain).disabled(!music.available).accessibilityLabel(label)
    }
}
