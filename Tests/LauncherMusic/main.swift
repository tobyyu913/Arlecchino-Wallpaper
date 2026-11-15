import AppKit

func evaluate(_ player: LauncherMusic.Player, _ action: LauncherMusic.Action?, _ fixture: String) throws -> MusicSnapshot {
    let source = fixture + "\n" + LauncherMusic.script(player: player, action: action).replacingOccurrences(of: "Application(", with: "mockApplication(")
    let process = Process()
    process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
    process.arguments = ["-l", "JavaScript", "-e", source]
    let output = Pipe(); process.standardOutput = output
    try process.run()
    let data = output.fileHandleForReading.readDataToEndOfFile()
    process.waitUntilExit()
    precondition(process.terminationStatus == 0)
    return try JSONDecoder().decode(MusicSnapshot.self, from: data)
}
let fixture = """
function mockApplication(id) {
    var playing = false, title = 'A "quoted" song · 夜';
    return {
      playerState: function(){ return playing ? 'playing' : 'paused'; },
      currentTrack: function(){ return {name:function(){return title;}, artist:function(){return 'Artist';}, artworkUrl:function(){return 'https://example.invalid/cover.png';}, duration:function(){return id === 'com.spotify.client' ? 240000 : 240;}}; },
      playerPosition: function(){ return 60; },
      playpause: function(){playing = !playing;},
      nextTrack: function(){title = 'Next song';},
      previousTrack: function(){title = 'Previous song';}
    };
}
"""
for player in LauncherMusic.Player.allCases {
    let paused = try evaluate(player, nil, fixture)
    precondition(paused.hasMedia && paused.artworkURL == "https://example.invalid/cover.png")
    precondition(!paused.playing && paused.title == "A \"quoted\" song · 夜" && paused.duration == 240 && paused.progress == 0.25)
    let toggled = try evaluate(player, .toggle, fixture)
    precondition(toggled.playing)
    let next = try evaluate(player, .next, fixture)
    precondition(next.title == "Next song")
    let previous = try evaluate(player, .previous, fixture)
    precondition(previous.title == "Previous song")
}
let stopped = try evaluate(.music, nil, "function mockApplication(){return {playerState:function(){return 'stopped';}, currentTrack:function(){throw Error('No track');}};}")
precondition(!MusicSnapshot(title: "Old track", state: "stopped").hasMedia)
precondition(!stopped.hasMedia && !stopped.playing && stopped.title.isEmpty && stopped.progress == 0)
precondition(MusicSnapshot(position: -10, duration: 5).progress == 0)
precondition(MusicSnapshot(position: 30, duration: 5).progress == 1)
print("PASS: Music/Spotify script commands, metadata escaping, duration normalization, paused/stopped states and progress bounds")
