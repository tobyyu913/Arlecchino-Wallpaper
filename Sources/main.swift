import AppKit
import AVFoundation
import SwiftUI
import Carbon
import ServiceManagement

final class WallpaperSurface: NSView {
    let videoLayer = AVPlayerLayer()
    var aspectRatio: CGFloat = 1920.0 / 1082.0
    override init(frame: NSRect) {
        super.init(frame: frame)
        wantsLayer = true
        layer?.backgroundColor = NSColor.black.cgColor
        layer?.masksToBounds = true
        videoLayer.videoGravity = .resize
        videoLayer.actions = ["opacity": NSNull(), "position": NSNull(), "bounds": NSNull()]
        layer?.addSublayer(videoLayer)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func layout() {
        super.layout()
        let mode = DisplayLayout(rawValue: UserDefaults.standard.string(forKey: "displayLayout") ?? "character") ?? .character
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        videoLayer.frame = mode.videoFrame(in: bounds, aspectRatio: aspectRatio)
        videoLayer.contentsScale = window?.backingScaleFactor ?? 2
        CATransaction.commit()
    }
    override func viewDidChangeBackingProperties() {
        super.viewDidChangeBackingProperties()
        needsLayout = true
    }
}

final class WallpaperWindow: NSWindow {
    let surface: WallpaperSurface
    var videoLayer: AVPlayerLayer { surface.videoLayer }
    init(screen: NSScreen, player: AVPlayer, aspectRatio: CGFloat) {
        surface = WallpaperSurface(frame: NSRect(origin: .zero, size: screen.frame.size))
        super.init(contentRect: screen.frame, styleMask: .borderless, backing: .buffered, defer: false)
        // Borderless desktop windows must include the menu bar and Dock areas.
        setFrame(screen.frame, display: false)
        level = NSWindow.Level(rawValue: Int(CGWindowLevelForKey(.desktopWindow)) + 1)
        collectionBehavior = [.canJoinAllSpaces, .stationary, .ignoresCycle]
        ignoresMouseEvents = true
        canHide = false
        hidesOnDeactivate = false
        animationBehavior = .none
        isReleasedWhenClosed = false
        backgroundColor = .black
        surface.aspectRatio = aspectRatio
        surface.videoLayer.player = player
        contentView = surface
        surface.needsLayout = true
        orderFrontRegardless()
    }
    func update(screen: NSScreen, aspectRatio: CGFloat) {
        if frame != screen.frame { setFrame(screen.frame, display: true) }
        if surface.aspectRatio != aspectRatio { surface.aspectRatio = aspectRatio; surface.needsLayout = true }
        if videoLayer.contentsScale != screen.backingScaleFactor { surface.needsLayout = true }
        surface.layoutSubtreeIfNeeded()
    }
    override var canBecomeKey: Bool { false }
    override var canBecomeMain: Bool { false }
}

typealias StoredState<Value> = SwiftUI.State<Value>

struct Preferences: View {
    @AppStorage("trigger") var trigger = "both"
    @AppStorage("lockBackground") var lockBackground = "black"
    @AppStorage("displayLayout") var displayLayout = "character"
    @AppStorage("closeKey") var closeKey = "q"
    @StoredState var login = SMAppService.mainApp.status == .enabled
    @StoredState var message = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Arlecchino Wallpaper").font(.largeTitle.bold())
            Text("Your own little theatre, behind the desktop.").foregroundStyle(.secondary)
            Divider()
            Picker("Open the curtain", selection: $trigger) {
                Text("On wake and unlock").tag("both")
                Text("On unlock").tag("unlock")
                Text("On wake / lid opening").tag("wake")
                Text("Only manually").tag("manual")
            }
            Picker("Screen layout", selection: $displayLayout) {
                Text("Keep full character · right edge").tag("character")
                Text("Fit entire video").tag("fit")
                Text("Fill screen · crop to fit").tag("fill")
            }.onChange(of: displayLayout) { appDelegate.updateDisplayLayout() }
            Picker("Lock-screen background", selection: $lockBackground) {
                Text("Black curtain").tag("black")
                Text("Keep Mac wallpaper").tag("system")
            }.onChange(of: lockBackground) { appDelegate.applyLockBackground() }
            Text("Black uses a plain black Mac wallpaper behind the animation. Your previous wallpaper is restored when you quit.").font(.caption).foregroundStyle(.secondary)
            Picker("Close shortcut", selection: $closeKey) {
                Text("⌃⌥⌘ Q").tag("q")
                Text("⌃⌥⌘ C").tag("c")
                Text("⌃⌥⌘ Space").tag("space")
            }.onChange(of: closeKey) { appDelegate.registerShortcuts() }
            Text("Open / replay: ⌃⌥⌘ O\nClosing leaves the curtain shut until it opens again.").font(.callout).foregroundStyle(.secondary)
            Toggle("Start at login", isOn: $login).onChange(of: login) { _, value in
                do {
                    if value { try SMAppService.mainApp.register() }
                    else { try SMAppService.mainApp.unregister() }
                    if SMAppService.mainApp.status == .requiresApproval {
                        message = "Allow Arlecchino Wallpaper in System Settings → General → Login Items."
                    } else { message = "" }
                } catch { message = error.localizedDescription; login = SMAppService.mainApp.status == .enabled }
            }
            if !message.isEmpty { Text(message).font(.caption).foregroundStyle(.orange) }
            Divider()
            HStack {
                Button("Open curtain") { appDelegate.openCurtain() }
                Button("Close curtain") { appDelegate.closeCurtain() }
                Spacer()
                Button("Quit app") { NSApp.terminate(nil) }
            }
            Text("The opening plays once; the face and hair then move in a seamless, silent loop. The curtain appears after macOS unlocks, not on the secure lock screen.").font(.caption).foregroundStyle(.secondary)
        }.padding(26).frame(width: 470)
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    let windowRegistry = WindowRegistry<WallpaperWindow>()
    var windows: [WallpaperWindow] { windowRegistry.windows }
    var player = AVQueuePlayer()
    var looper: AVPlayerLooper?
    var status: NSStatusItem!
    var preferences: NSWindow?
    var assets: [String: AVURLAsset] = [:]
    var state = "loading"
    var hotKeys: [EventHotKeyRef] = []
    var handler: EventHandlerRef?
    var lastOpening = Date.distantPast
    var videoAspectRatio: CGFloat = 1920.0 / 1082.0
    var locked = false
    var pendingWakeOpening = false
    var ready = false
    var test = CommandLine.arguments.contains("--self-test")
    var observedMiddle = false
    var observedLoop = false
    var lastMiddleTime = 0.0
    var testTimer: Timer?

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        UserDefaults.standard.register(defaults: ["trigger": "both", "closeKey": "q", "lockBackground": "black", "displayLayout": "character"])
        buildMenu()
        registerShortcuts()
        if !test { applyLockBackground() }
        let workspace = NSWorkspace.shared.notificationCenter
        workspace.addObserver(self, selector: #selector(unlocked), name: NSWorkspace.sessionDidBecomeActiveNotification, object: nil)
        workspace.addObserver(self, selector: #selector(screenLocked), name: NSWorkspace.sessionDidResignActiveNotification, object: nil)
        workspace.addObserver(self, selector: #selector(woke), name: NSWorkspace.didWakeNotification, object: nil)
        workspace.addObserver(self, selector: #selector(sleeping), name: NSWorkspace.willSleepNotification, object: nil)
        workspace.addObserver(self, selector: #selector(screenWoke), name: NSWorkspace.screensDidWakeNotification, object: nil)
        workspace.addObserver(self, selector: #selector(sleeping), name: NSWorkspace.screensDidSleepNotification, object: nil)
        let distributed = DistributedNotificationCenter.default()
        distributed.addObserver(self, selector: #selector(unlocked), name: NSNotification.Name("com.apple.screenIsUnlocked"), object: nil)
        distributed.addObserver(self, selector: #selector(screenLocked), name: NSNotification.Name("com.apple.screenIsLocked"), object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(rebuildWindows), name: NSApplication.didChangeScreenParametersNotification, object: nil)
        Task { @MainActor in
            do {
                for name in ["opening", "middle", "closing"] {
                    guard let url = Bundle.main.url(forResource: name, withExtension: "mp4") else { throw NSError(domain: "Missing movie: \(name)", code: 1) }
                    let asset = AVURLAsset(url: url)
                    let duration = try await asset.load(.duration)
                    guard duration.seconds > 0 else { throw NSError(domain: "Invalid movie: \(name)", code: 2) }
                    assets[name] = asset
                }
                if let asset = assets["opening"], let track = try await asset.loadTracks(withMediaType: .video).first {
                    let size = try await track.load(.naturalSize)
                    let transform = try await track.load(.preferredTransform)
                    let oriented = CGRect(origin: .zero, size: size).applying(transform)
                    if oriented.height != 0 { videoAspectRatio = abs(oriented.width / oriented.height) }
                }
                ready = true
                openCurtain()
                if test { runTest() }
                else if !UserDefaults.standard.bool(forKey: "didSetup") { showPreferences(); UserDefaults.standard.set(true, forKey: "didSetup") }
            } catch {
                let alert = NSAlert(); alert.messageText = "Cannot load curtain animation"; alert.informativeText = error.localizedDescription; alert.runModal(); NSApp.terminate(nil)
            }
        }
    }
    func buildMenu() {
        status = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        status.button?.image = NSImage(systemSymbolName: "theatermasks", accessibilityDescription: "Arlecchino Wallpaper")
        let menu = NSMenu()
        menu.addItem(withTitle: "Open curtain    ⌃⌥⌘O", action: #selector(openCurtain), keyEquivalent: "")
        menu.addItem(withTitle: "Close curtain", action: #selector(closeCurtain), keyEquivalent: "")
        menu.addItem(.separator())
        menu.addItem(withTitle: "Settings…", action: #selector(showPreferences), keyEquivalent: ",")
        menu.addItem(withTitle: "Quit Arlecchino Wallpaper", action: #selector(quit), keyEquivalent: "q")
        for item in menu.items { item.target = self }
        status.menu = menu
    }
    func swapPlayer(_ newPlayer: AVQueuePlayer) {
        player.pause(); looper?.disableLooping(); looper = nil
        player = newPlayer; player.isMuted = true
        if windows.isEmpty { rebuildWindows() }
        else { windows.forEach { $0.videoLayer.player = player } }
    }
    @objc func rebuildWindows() {
        let screens = NSScreen.screens
        var byID: [String: NSScreen] = [:]
        let ids = screens.map { screen -> String in
            let id = displayKey(screen)
            byID[id] = screen
            return id
        }
        let previousIDs = windowRegistry.displayIDs
        windowRegistry.reconcile(displayIDs: ids, create: { id in
            let window = WallpaperWindow(screen: byID[id]!, player: player, aspectRatio: videoAspectRatio)
            window.videoLayer.opacity = locked ? 0 : 1
            return window
        }, update: { id, window in
            window.update(screen: byID[id]!, aspectRatio: videoAspectRatio)
        }, remove: { $0.close() })
        // App/Space changes can emit redundant screen notifications. They must
        // neither replace the video layer nor rewrite the underlying wallpaper.
        if !test && !Set(ids).subtracting(previousIDs).isEmpty { applyLockBackground() }
    }
    func updateDisplayLayout() {
        windows.forEach { $0.surface.needsLayout = true; $0.surface.layoutSubtreeIfNeeded() }
    }
    @objc func openCurtain() {
        guard !locked, ready, let opening = assets["opening"], let middle = assets["middle"] else { return }
        lastOpening = Date()
        let queue = AVQueuePlayer(items: [AVPlayerItem(asset: opening)])
        swapPlayer(queue)
        looper = AVPlayerLooper(player: queue, templateItem: AVPlayerItem(asset: middle), timeRange: .invalid, existingItemsOrdering: .loopingItemsFollowExistingItems)
        state = "open"
        player.play()
    }
    @objc func closeCurtain() {
        guard !locked, ready, state != "closed", let closing = assets["closing"] else { return }
        let queue = AVQueuePlayer(items: [AVPlayerItem(asset: closing)])
        queue.actionAtItemEnd = .pause
        swapPlayer(queue)
        state = "closed"
        player.play()
    }
    func automaticOpen(for event: String) {
        let trigger = UserDefaults.standard.string(forKey: "trigger") ?? "both"
        if trigger == "both" || trigger == event {
            if Date().timeIntervalSince(lastOpening) > 5 { openCurtain() }
            else if state == "open" { player.play() }
        } else if state == "open" { player.play() }
    }
    @objc func woke() {
        if locked { pendingWakeOpening = true; return }
        automaticOpen(for: "wake")
    }
    @objc func screenWoke() { woke() }
    @objc func screenLocked() {
        locked = true
        player.pause()
        windows.forEach { $0.videoLayer.opacity = 0 }
    }
    @objc func unlocked() {
        // Both the session and screen-unlock notifications can describe one
        // unlock. An already-active desktop is not a new opening trigger.
        guard locked else { return }
        let wasLocked = locked
        locked = false
        windows.forEach { $0.videoLayer.opacity = 1 }
        let trigger = UserDefaults.standard.string(forKey: "trigger") ?? "both"
        if wasLocked && (trigger == "unlock" || trigger == "both" || (trigger == "wake" && pendingWakeOpening)) {
            openCurtain()
        } else { automaticOpen(for: "unlock") }
        pendingWakeOpening = false
    }
    func displayKey(_ screen: NSScreen) -> String {
        let displayID = (screen.deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")] as? NSNumber)?.uint32Value ?? 0
        if let uuid = CGDisplayCreateUUIDFromDisplayID(displayID)?.takeRetainedValue() {
            return CFUUIDCreateString(nil, uuid) as String
        }
        return String(displayID)
    }
    func applyLockBackground() {
        guard let black = Bundle.main.url(forResource: "black", withExtension: "png") else { return }
        if UserDefaults.standard.string(forKey: "lockBackground") != "black" { restoreWallpaper(); return }
        var saved = UserDefaults.standard.dictionary(forKey: "previousWallpapers") as? [String: String] ?? [:]
        for screen in NSScreen.screens {
            let key = displayKey(screen)
            let current = NSWorkspace.shared.desktopImageURL(for: screen)
            if current?.standardizedFileURL == black.standardizedFileURL { continue }
            if let current, current.lastPathComponent != "black.png" { saved[key] = current.absoluteString }
            // Save before changing the wallpaper, so a restart can still restore it.
            UserDefaults.standard.set(saved, forKey: "previousWallpapers")
            do {
                try NSWorkspace.shared.setDesktopImageURL(black, for: screen, options: [.imageScaling: NSImageScaling.scaleAxesIndependently.rawValue])
            } catch {
                NSLog("Cannot set black lock-screen background: %@", error.localizedDescription)
            }
        }
    }
    func restoreWallpaper() {
        var saved = UserDefaults.standard.dictionary(forKey: "previousWallpapers") as? [String: String] ?? [:]
        for screen in NSScreen.screens {
            let key = displayKey(screen)
            guard let original = saved[key], let url = URL(string: original) else { continue }
            let current = NSWorkspace.shared.desktopImageURL(for: screen)
            // Preserve wallpaper changes made by the user while this app was running.
            if current?.lastPathComponent == "black.png" {
                do { try NSWorkspace.shared.setDesktopImageURL(url, for: screen, options: [:]) }
                catch { NSLog("Cannot restore wallpaper: %@", error.localizedDescription); continue }
            }
            saved.removeValue(forKey: key)
        }
        UserDefaults.standard.set(saved, forKey: "previousWallpapers")
    }
    @objc func sleeping() { player.pause() }
    @objc func quit() { NSApp.terminate(nil) }
    @objc func showPreferences() {
        if preferences == nil {
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 522, height: 430), styleMask: [.titled, .closable], backing: .buffered, defer: false)
            window.title = "Arlecchino Wallpaper Settings"
            let hosting = NSHostingView(rootView: Preferences())
            window.contentView = hosting
            window.setContentSize(hosting.fittingSize)
            window.isReleasedWhenClosed = false
            window.center(); preferences = window
        }
        NSApp.activate(ignoringOtherApps: true)
        preferences?.makeKeyAndOrderFront(nil)
    }
    func registerShortcuts() {
        hotKeys.forEach { UnregisterEventHotKey($0) }; hotKeys.removeAll()
        if handler == nil {
            var spec = EventTypeSpec(eventClass: OSType(kEventClassKeyboard), eventKind: UInt32(kEventHotKeyPressed))
            InstallEventHandler(GetApplicationEventTarget(), { _, event, _ in
                guard let event else { return OSStatus(eventNotHandledErr) }
                var id = EventHotKeyID()
                GetEventParameter(event, EventParamName(kEventParamDirectObject), EventParamType(typeEventHotKeyID), nil, MemoryLayout<EventHotKeyID>.size, nil, &id)
                DispatchQueue.main.async { if id.id == 1 { appDelegate.closeCurtain() } else { appDelegate.openCurtain() } }
                return noErr
            }, 1, &spec, nil, &handler)
        }
        let key: UInt32
        switch UserDefaults.standard.string(forKey: "closeKey") { case "c": key = UInt32(kVK_ANSI_C); case "space": key = UInt32(kVK_Space); default: key = UInt32(kVK_ANSI_Q) }
        for (number, code) in [(UInt32(1), key), (UInt32(2), UInt32(kVK_ANSI_O))] {
            var reference: EventHotKeyRef?
            let result = RegisterEventHotKey(code, UInt32(controlKey | optionKey | cmdKey), EventHotKeyID(signature: 0x43555254, id: number), GetApplicationEventTarget(), 0, &reference)
            if result == noErr, let reference { hotKeys.append(reference) }
            else { NSLog("Curtain shortcut registration failed: %d", result) }
        }
    }
    func runTest() {
        // Exercise real decoder playback, the queued transition, a full loop wrap, and closing.
        let start = Date()
        testTimer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [self] timer in
            let elapsed = Date().timeIntervalSince(start)
            if let asset = player.currentItem?.asset as? AVURLAsset, asset.url.lastPathComponent == "middle.mp4" {
                observedMiddle = true
                let time = player.currentTime().seconds
                if lastMiddleTime > 13 && time < 2 { observedLoop = true }
                lastMiddleTime = time
            }
            if elapsed > 21 && state == "open" { closeCurtain() }
            if elapsed > 25 {
                let passed = observedMiddle && observedLoop && player.rate == 0 && player.currentTime().seconds > 2.8 && hotKeys.count == 2 && !windows.isEmpty && player.currentItem?.error == nil
                let result = "playback=\(passed ? "PASS" : "FAIL") middle=\(observedMiddle) loopWrap=\(observedLoop) closingTime=\(player.currentTime().seconds) hotkeys=\(hotKeys.count) screens=\(windows.count)\n"
                try? result.write(toFile: ProcessInfo.processInfo.environment["CURTAIN_TEST_LOG"] ?? "/tmp/curtain-test.txt", atomically: true, encoding: .utf8)
                timer.invalidate(); NSApp.terminate(nil)
            }
        }
    }
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        showPreferences(); return true
    }
    func applicationWillTerminate(_ notification: Notification) {
        if !test { restoreWallpaper() }
        player.pause(); hotKeys.forEach { UnregisterEventHotKey($0) }
    }
}
let appDelegate = AppDelegate()
let application = NSApplication.shared
application.delegate = appDelegate
application.run()
