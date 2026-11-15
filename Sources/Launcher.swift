import AppKit
import SwiftUI

struct DesktopApp: Identifiable {
    let url: URL
    let name: String
    var id: String { url.path }
}

final class LauncherModel: ObservableObject {
    let music = LauncherMusic()
    @Published var apps: [DesktopApp] = []
    @Published var favorites: [String] = UserDefaults.standard.stringArray(forKey: "launcherFavorites") ?? []
    @Published var query = ""
    @Published var section = "★"
    @Published var icons = UserDefaults.standard.bool(forKey: "launcherOriginalIcons")
    private var iconCache: [String: NSImage] = [:]
    let alphabet = Array("★#ABCDEFGHIJKLMNOPQRSTUVWXYZ⚙").map(String.init)
    func refresh() {
        DispatchQueue.global(qos: .userInitiated).async {
            let roots = ["/Applications", "/System/Applications", "/System/Applications/Utilities", "/System/Cryptexes/App/System/Applications", NSHomeDirectory() + "/Applications"]
            var found: [DesktopApp] = []
            var seen = Set<String>()
            for root in roots {
                guard let files = FileManager.default.enumerator(at: URL(fileURLWithPath: root), includingPropertiesForKeys: [.isDirectoryKey], options: [.skipsHiddenFiles]) else { continue }
                while let url = files.nextObject() as? URL {
                    guard url.pathExtension == "app" else { continue }
                    files.skipDescendants()
                    let bundle = Bundle(url: url)
                    let key = bundle?.bundleIdentifier ?? url.path
                    guard seen.insert(key).inserted else { continue }
                    let name = (bundle?.localizedInfoDictionary?["CFBundleDisplayName"] as? String) ?? (bundle?.infoDictionary?["CFBundleDisplayName"] as? String) ?? url.deletingPathExtension().lastPathComponent
                    found.append(DesktopApp(url: url, name: name))
                }
            }
            let finderURL = URL(fileURLWithPath: "/System/Library/CoreServices/Finder.app")
            if FileManager.default.fileExists(atPath: finderURL.path) { found.append(DesktopApp(url: finderURL, name: "Finder")) }
            let sorted = found.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
            DispatchQueue.main.async {
                self.apps = sorted
                if UserDefaults.standard.object(forKey: "launcherFavorites") == nil {
                    let defaults = ["com.apple.Safari", "com.apple.mail", "com.apple.MobileSMS", "com.apple.finder", "com.apple.systempreferences"]
                    self.favorites = defaults.compactMap { id in sorted.first { Bundle(url: $0.url)?.bundleIdentifier == id }?.id }
                    self.save()
                }
            }
        }
    }
    func save() { UserDefaults.standard.set(favorites, forKey: "launcherFavorites") }
    func toggle(_ app: DesktopApp) {
        if favorites.contains(app.id) { favorites.removeAll { $0 == app.id } }
        else { favorites.append(app.id) }
        save()
    }
    func letter(_ app: DesktopApp) -> String {
        let first = String(app.name.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current).uppercased().prefix(1))
        return first.range(of: "^[A-Z]$", options: .regularExpression) != nil ? first : "#"
    }
    var visible: [DesktopApp] {
        if !query.isEmpty { return apps.filter { $0.name.localizedCaseInsensitiveContains(query) } }
        if section == "★" { return favorites.compactMap { id in apps.first { $0.id == id } } }
        return apps
    }
    func image(_ app: DesktopApp) -> NSImage {
        if let cached = iconCache[app.id] { return cached }
        let image = NSWorkspace.shared.icon(forFile: app.id)
        iconCache[app.id] = image
        return image
    }
    private static let minimalMap: [String: [String: String]] = {
        guard let url = Bundle.main.resourceURL?.appendingPathComponent("MinimalIcons/map.json"),
              let data = try? Data(contentsOf: url),
              let map = try? JSONDecoder().decode([String: [String: String]].self, from: data) else { return [:] }
        return map
    }()
    func minimalIconURL(_ app: DesktopApp) -> URL? {
        let name = app.name.lowercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) }
        let bundle = Bundle(url: app.url)?.bundleIdentifier ?? ""
        guard let file = Self.minimalMap["bundles"]?[bundle] ?? Self.minimalMap["names"]?[name] else { return nil }
        return Bundle.main.resourceURL?.appendingPathComponent("MinimalIcons").appendingPathComponent(file)
    }
    func minimalistImage(_ app: DesktopApp) -> NSImage {
        let key = "minimal:" + app.id
        if let cached = iconCache[key] { return cached }
        let result = minimalIconURL(app).flatMap { NSImage(contentsOf: $0) } ?? image(app)
        iconCache[key] = result
        return result
    }
    func launch(_ app: DesktopApp) { NSWorkspace.shared.openApplication(at: app.url, configuration: NSWorkspace.OpenConfiguration()) }
}

final class LauncherPresentation: ObservableObject {
    @Published var summoned = false
}

struct LauncherBackdrop: View {
    var summoned: Bool
    var body: some View {
        if summoned {
            if #available(macOS 26.0, *) {
                RoundedRectangle(cornerRadius: 24).fill(.clear)
                    .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 24))
            } else {
                RoundedRectangle(cornerRadius: 24).fill(.ultraThinMaterial)
            }
        } else {
            RoundedRectangle(cornerRadius: 24).fill(LinearGradient(colors: [.black.opacity(0.64), .black.opacity(0.3)], startPoint: .leading, endPoint: .trailing))
        }
    }
}

struct LauncherView: View {
    @ObservedObject var model: LauncherModel
    @ObservedObject var presentation = LauncherPresentation()
    @State private var hover: String?
    @State private var railPosition: CGFloat?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var searching: Bool
    var body: some View {
        GeometryReader { geometry in
            VStack(alignment: .leading, spacing: 18) {
                TimelineView(.periodic(from: .now, by: 60)) { time in
                    VStack(alignment: .leading, spacing: 3) {
                        Text(time.date, style: .time).font(.system(size: 48, weight: .ultraLight, design: .rounded))
                        Text(time.date.formatted(.dateTime.weekday(.wide).month(.wide).day())).font(.system(size: 13)).foregroundStyle(.white.opacity(0.6))
                    }
                }.padding(.top, 12)
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.white.opacity(0.55))
                    TextField("Find an app", text: $model.query).textFieldStyle(.plain).focused($searching)
                        .onSubmit { if let app = model.visible.first { model.launch(app) } }
                    if !model.query.isEmpty { Button { model.query = "" } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.plain) }
                }.padding(12).background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 13))
                HStack {
                    Text(model.query.isEmpty ? (model.section == "★" ? "FAVORITES" : "APPLICATIONS") : "SEARCH").font(.system(size: 10, weight: .semibold)).tracking(3).foregroundStyle(.white.opacity(0.45))
                    Spacer()
                    if model.query.isEmpty {
                        Button(model.section == "★" ? "All apps" : "Favorites") { select(model.section == "★" ? "#" : "★") }.buttonStyle(.plain).font(.system(size: 11)).foregroundStyle(.white.opacity(0.65))
                    }
                }
                ScrollViewReader { proxy in
                    HStack(spacing: 2) {
                        ScrollView {
                            LazyVStack(alignment: .leading, spacing: 3) {
                                if model.section == "★" && model.query.isEmpty { MusicPlayerView(music: model.music) }
                                if model.visible.isEmpty {
                                    Text(model.query.isEmpty ? "Add favorites from All apps.\nRight-click an app to pin it here." : "No matching apps").font(.system(size: 13)).foregroundStyle(.white.opacity(0.5)).padding(.vertical, 20)
                                }
                                ForEach(model.visible) { app in
                                    if model.section != "★" && model.query.isEmpty && model.apps.first(where: { model.letter($0) == model.letter(app) })?.id == app.id {
                                        Text(model.letter(app)).font(.system(size: 13, weight: .medium)).foregroundStyle(Color(red: 0.83, green: 0.36, blue: 0.38)).padding(.top, 12).padding(.bottom, 5).id(model.letter(app))
                                    }
                                    Button { model.launch(app) } label: {
                                        HStack(spacing: 14) {
                                            Image(nsImage: model.icons ? model.image(app) : model.minimalistImage(app))
                                                .resizable().interpolation(.high).frame(width: 32, height: 32)
                                            Text(app.name).font(.system(size: 17, weight: .regular)).lineLimit(1)
                                            Spacer(minLength: 0)
                                        }.padding(.horizontal, 10).padding(.vertical, 10).contentShape(Rectangle())
                                            .background(.white.opacity(hover == app.id ? 0.09 : 0), in: RoundedRectangle(cornerRadius: 10))
                                            .offset(x: hover == app.id ? 3 : 0)
                                    }.buttonStyle(.plain).id(app.id).onHover { hover = $0 ? app.id : nil }
                                        .animation(.easeOut(duration: 0.14), value: hover)
                                        .contextMenu {
                                            Button(model.favorites.contains(app.id) ? "Remove from Favorites" : "Add to Favorites") { model.toggle(app) }
                                            Button("Show in Finder") { NSWorkspace.shared.activateFileViewerSelecting([app.url]) }
                                        }
                                }
                            }.padding(.trailing, 6)
                        }.scrollIndicators(.hidden)
                        if model.query.isEmpty {
                            GeometryReader { rail in
                                ZStack {
                                    Path { path in
                                        for step in 0...100 {
                                            let y = rail.size.height * CGFloat(step) / 100
                                            let x = rail.size.width / 2 + AlphabetWave.displacement(y: y, height: rail.size.height, position: railPosition)
                                            if step == 0 { path.move(to: CGPoint(x: x, y: y)) }
                                            else { path.addLine(to: CGPoint(x: x, y: y)) }
                                        }
                                    }.stroke(.white.opacity(railPosition == nil ? 0 : 0.08), lineWidth: 1)
                                    VStack(spacing: 0) {
                                        ForEach(Array(model.alphabet.enumerated()), id: \.element) { index, letter in
                                            let y = rail.size.height * (CGFloat(index) + 0.5) / CGFloat(model.alphabet.count)
                                            let bend = AlphabetWave.displacement(y: y, height: rail.size.height, position: railPosition)
                                            Text(letter).font(.system(size: 10, weight: model.section == letter ? .bold : .regular))
                                                .foregroundStyle(model.section == letter ? Color(red: 0.95, green: 0.5, blue: 0.5) : .white.opacity(0.45 + Double(bend / 54) * 0.4))
                                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                                .scaleEffect(1 + bend / 140)
                                                .offset(x: bend)
                                        }
                                    }
                                    if let position = railPosition {
                                        let index = max(0, min(model.alphabet.count - 1, Int(position * CGFloat(model.alphabet.count))))
                                        Text(model.alphabet[index]).font(.system(size: 22, weight: .light))
                                            .frame(width: 42, height: 42)
                                            .background(Color(red: 0.36, green: 0.12, blue: 0.15), in: Circle())
                                            .position(x: rail.size.width / 2 + 101, y: max(21, min(rail.size.height - 21, position * rail.size.height)))
                                    }
                                }.allowsHitTesting(false)
                                .animation(reduceMotion ? nil : .spring(response: 0.3, dampingFraction: 0.85), value: railPosition == nil)
                                .overlay(alignment: .leading) {
                                    AlphabetInput(onPosition: { position, ended in
                                    railPosition = position
                                    let index = max(0, min(model.alphabet.count - 1, Int(position * CGFloat(model.alphabet.count))))
                                    let letter = model.alphabet[index]
                                    if letter == "⚙" {
                                        if ended { appDelegate.showPreferences() }
                                        return
                                    }
                                    select(letter)
                                    if letter != "★" {
                                        DispatchQueue.main.async { let target = model.apps.first { model.letter($0) >= letter && model.letter($0) != "#" }.map { model.letter($0) } ?? model.apps.last.map { model.letter($0) } ?? letter
                                            proxy.scrollTo(letter == "#" && model.apps.contains(where: { model.letter($0) == "#" }) ? "#" : target, anchor: .top) }
                                    }
                                }, onScroll: {
                                    if model.section == "★" { select("#") }
                                }, onExit: { railPosition = nil })
                                    .frame(width: railPosition == nil ? 24 : 88)
                                }
                            }.frame(width: 24)
                        }
                    }.onChange(of: model.section) { _, value in
                        if value == "★", let first = model.visible.first { proxy.scrollTo(first.id, anchor: .top) }
                    }
                }
                HStack {
                    Text("ARLECCHINO").font(.system(size: 9, weight: .medium)).tracking(4).foregroundStyle(.white.opacity(0.3))
                    Spacer()
                    Button { appDelegate.showPreferences() } label: { Image(systemName: "slider.horizontal.3") }.buttonStyle(.plain).foregroundStyle(.white.opacity(0.45))
                }
            }.padding(24).frame(width: max(0, geometry.size.width - AlphabetWave.gutter), height: geometry.size.height)
                .background(LauncherBackdrop(summoned: presentation.summoned))
                .frame(maxWidth: .infinity, alignment: .leading)
        }.foregroundStyle(.white).preferredColorScheme(.dark)
            .onExitCommand { model.query = ""; searching = false; appDelegate.launcher.dismiss() }
    }
    func select(_ letter: String) {
        model.query = ""
        model.section = letter
    }
}

final class LauncherWindow: NSPanel {
    let presentation = LauncherPresentation()
    var restingFrame: NSRect
    private var transition = 0
    private(set) var isDismissing = false
    private(set) var isTransitioning = false
    init(screen: NSScreen, model: LauncherModel) {
        restingFrame = Self.launcherFrame(screen)
        super.init(contentRect: restingFrame, styleMask: [.borderless, .nonactivatingPanel], backing: .buffered, defer: false)
        level = NSWindow.Level(rawValue: Int(CGWindowLevelForKey(.desktopIconWindow)) + 1)
        collectionBehavior = [.canJoinAllSpaces, .stationary, .ignoresCycle, .fullScreenAuxiliary]
        isOpaque = false
        backgroundColor = .clear
        hasShadow = false
        hidesOnDeactivate = false
        acceptsMouseMovedEvents = true
        isReleasedWhenClosed = false
        contentView = NSHostingView(rootView: LauncherView(model: model, presentation: presentation))
        orderFrontRegardless()
    }
    static func launcherFrame(_ screen: NSScreen) -> NSRect {
        let visible = screen.visibleFrame
        let width = min(370, max(220, visible.width * 0.42)) + AlphabetWave.gutter
        let height = max(200, min(820, visible.height - 40))
        return NSRect(x: visible.minX + 24, y: visible.midY - height / 2, width: width, height: height)
    }
    func summonAnimated() {
        transition += 1
        let token = transition
        let wasVisible = isVisible
        isDismissing = false
        isTransitioning = true
        ignoresMouseEvents = false
        if !wasVisible || !presentation.summoned {
            alphaValue = 0
            setFrame(NSWorkspace.shared.accessibilityDisplayShouldReduceMotion ? restingFrame : restingFrame.offsetBy(dx: -18, dy: 0), display: false)
        }
        presentation.summoned = true
        hasShadow = true
        level = .floating
        makeKeyAndOrderFront(nil)
        NSAnimationContext.runAnimationGroup { context in
            context.duration = NSWorkspace.shared.accessibilityDisplayShouldReduceMotion ? 0.1 : 0.26
            context.timingFunction = CAMediaTimingFunction(controlPoints: 0.16, 1, 0.3, 1)
            animator().alphaValue = 1
            animator().setFrame(restingFrame, display: true)
        } completionHandler: { [weak self] in
            guard let self, self.transition == token else { return }
            self.isTransitioning = false
        }
    }
    func dismissAnimated() {
        guard isVisible, !isDismissing else { return }
        transition += 1
        let token = transition
        isDismissing = true
        isTransitioning = true
        ignoresMouseEvents = true
        let destination = NSWorkspace.shared.accessibilityDisplayShouldReduceMotion ? restingFrame : restingFrame.offsetBy(dx: -12, dy: 0)
        NSAnimationContext.runAnimationGroup { context in
            context.duration = NSWorkspace.shared.accessibilityDisplayShouldReduceMotion ? 0.1 : 0.18
            context.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
            animator().alphaValue = 0
            animator().setFrame(destination, display: true)
        } completionHandler: { [weak self] in
            guard let self, self.transition == token else { return }
            self.presentation.summoned = false
            self.isDismissing = false
            self.isTransitioning = false
            self.orderOut(nil)
            self.hasShadow = false
            self.level = NSWindow.Level(rawValue: Int(CGWindowLevelForKey(.desktopIconWindow)) + 1)
            self.setFrame(self.restingFrame, display: false)
            self.alphaValue = 1
            self.ignoresMouseEvents = false
        }
    }
    override func resignKey() {
        super.resignKey()
        if presentation.summoned && !isDismissing { dismissAnimated() }
    }
    override var canBecomeKey: Bool { true }
    override var canBecomeMain: Bool { false }
}

final class DesktopLauncher {
    let model = LauncherModel()
    var windows: [LauncherWindow] = []
    private var edgeTimer: Timer?
    private var edgeGate = LauncherEdgeGate()
    private var departureGate = LauncherDepartureGate()
    private weak var summonedWindow: LauncherWindow?
    private var menuTracking = false
    private var menuObservers: [NSObjectProtocol] = []
    func startEdgeTrigger() {
        stopEdgeTrigger()
        for (name, tracking) in [(NSMenu.didBeginTrackingNotification, true), (NSMenu.didEndTrackingNotification, false)] {
            menuObservers.append(NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in self?.menuTracking = tracking })
        }
        let timer = Timer(timeInterval: 0.08, repeats: true) { [weak self] _ in
            guard let self else { return }
            let screens = NSScreen.screens
            let point = NSEvent.mouseLocation
            let now = ProcessInfo.processInfo.systemUptime
            if let window = self.summonedWindow, window.isVisible && window.presentation.summoned,
               self.departureGate.update(point: point, frame: window.frame, now: now, paused: self.menuTracking || NSEvent.pressedMouseButtons != 0 || appDelegate.locked) {
                self.dismiss()
            }
            let enabled = !appDelegate.locked && !self.menuTracking && !self.windows.contains(where: { $0.isVisible && $0.presentation.summoned }) && NSEvent.pressedMouseButtons == 0 && NSApp.modalWindow == nil && UserDefaults.standard.bool(forKey: "launcherEdgeTrigger")
            if let index = self.edgeGate.update(point: NSEvent.mouseLocation, frames: screens.map(\.frame), now: ProcessInfo.processInfo.systemUptime, enabled: enabled) {
                self.reveal(on: screens[index])
            }
        }
        edgeTimer = timer
        RunLoop.main.add(timer, forMode: .common)
    }
    func stopEdgeTrigger() {
        edgeTimer?.invalidate(); edgeTimer = nil
        menuObservers.forEach { NotificationCenter.default.removeObserver($0) }; menuObservers.removeAll()
        menuTracking = false
    }
    func rebuild(locked: Bool = false) {
        windows.forEach { $0.close() }; windows.removeAll()
        guard !locked, UserDefaults.standard.bool(forKey: "launcherEnabled") else { return }
        for screen in NSScreen.screens { windows.append(LauncherWindow(screen: screen, model: model)) }
    }
    func updateFrames() {
        guard windows.count == NSScreen.screens.count else { rebuild(locked: appDelegate.locked); return }
        for (window, screen) in zip(windows, NSScreen.screens) {
            let frame = LauncherWindow.launcherFrame(screen)
            window.restingFrame = frame
            if window.frame != frame && !window.isTransitioning { window.setFrame(frame, display: true) }
        }
    }
    func dismiss() {
        summonedWindow = nil
        departureGate = LauncherDepartureGate()
        windows.forEach { window in
            window.dismissAnimated()
        }
    }
    func toggle() {
        if windows.contains(where: { $0.isVisible && !$0.isDismissing && ($0.presentation.summoned || $0.isKeyWindow) }) {
            dismiss()
        } else {
            reveal()
        }
    }
    func reveal(on screen: NSScreen? = nil) {
        guard !appDelegate.locked else { return }
        UserDefaults.standard.set(true, forKey: "launcherEnabled")
        if windows.isEmpty { rebuild() }
        model.refresh()
        NSApp.unhide(nil)
        NSApp.activate(ignoringOtherApps: true)
        let screen = screen ?? NSScreen.screens.first { $0.frame.contains(NSEvent.mouseLocation) }
        let index = screen.flatMap { target in NSScreen.screens.firstIndex { $0.frame == target.frame } } ?? 0
        guard windows.indices.contains(index) else { return }
        let window = windows[index]
        summonedWindow = window
        departureGate = LauncherDepartureGate()
        window.summonAnimated()
    }
}

// Route trackpad/wheel events to the same native scroll view as the app list.
// Keeping the original NSEvent preserves pixel deltas, direction and momentum.
struct AlphabetInput: NSViewRepresentable {
    var onPosition: (CGFloat, Bool) -> Void
    var onScroll: () -> Void
    var onExit: () -> Void = {}
    func makeNSView(context: Context) -> AlphabetInputView {
        let view = AlphabetInputView()
        view.onPosition = onPosition
        view.onScroll = onScroll
        view.onExit = onExit
        return view
    }
    func updateNSView(_ view: AlphabetInputView, context: Context) {
        view.onPosition = onPosition
        view.onScroll = onScroll
        view.onExit = onExit
    }
}
final class AlphabetInputView: NSView {
    var onPosition: (CGFloat, Bool) -> Void = { _, _ in }
    var onScroll: () -> Void = {}
    var onExit: () -> Void = {}
    override func mouseExited(with event: NSEvent) { onExit() }
    private var hoverTracking: NSTrackingArea?
    override func updateTrackingAreas() {
        super.updateTrackingAreas()
        if let tracking = hoverTracking { removeTrackingArea(tracking) }
        let tracking = NSTrackingArea(rect: .zero, options: [.mouseMoved, .mouseEnteredAndExited, .activeAlways, .inVisibleRect], owner: self, userInfo: nil)
        addTrackingArea(tracking)
        hoverTracking = tracking
    }
    override func mouseEntered(with event: NSEvent) { select(event) }
    override func mouseMoved(with event: NSEvent) { select(event) }
    override var isFlipped: Bool { true }
    override var acceptsFirstResponder: Bool { true }
    func select(_ event: NSEvent, ended: Bool = false) {
        let point = convert(event.locationInWindow, from: nil)
        onPosition(max(0, min(0.99999, point.y / max(1, bounds.height))), ended)
    }
    override func mouseDown(with event: NSEvent) { select(event) }
    override func mouseDragged(with event: NSEvent) { select(event) }
    override func mouseUp(with event: NSEvent) { select(event, ended: true) }
    static func listScrollView(in view: NSView) -> NSScrollView? {
        if let scroll = view as? NSScrollView { return scroll }
        for child in view.subviews {
            if let scroll = listScrollView(in: child) { return scroll }
        }
        return nil
    }
    override func scrollWheel(with event: NSEvent) {
        guard event.scrollingDeltaY != 0 || event.phase == .ended || event.momentumPhase == .ended else { return }
        onScroll()
        // The first swipe may replace the favorites with the full app list.
        DispatchQueue.main.async { [weak self] in
            guard let root = self?.window?.contentView,
                  let scroll = Self.listScrollView(in: root) else { return }
            scroll.scrollWheel(with: event)
        }
    }
}

// Match the Android alphabet's broad, local bend without delaying pointer motion.
enum AlphabetWave {
    static let gutter: CGFloat = 104
    static func displacement(y: CGFloat, height: CGFloat, position: CGFloat?) -> CGFloat {
        guard let position else { return 0 }
        let distance = y - position * height
        let spread = max(45, height / 29 * 2.4)
        return 54 * exp(-(distance * distance) / (2 * spread * spread))
    }
}

// One summon per edge visit; leaving the edge rearms it after dismissal.
struct LauncherEdgeGate {
    private var armed = true
    private var candidate: Int?
    private var enteredAt: TimeInterval = 0
    mutating func update(point: CGPoint, frames: [CGRect], now: TimeInterval, enabled: Bool) -> Int? {
        guard enabled else { candidate = nil; armed = false; return nil }
        guard let index = frames.firstIndex(where: { point.x >= $0.minX && point.x <= $0.maxX && point.y >= $0.minY && point.y <= $0.maxY }) else { candidate = nil; return nil }
        let distance = point.x - frames[index].minX
        if distance > 14 { armed = true; candidate = nil; return nil }
        guard armed, distance <= 1 else { candidate = nil; return nil }
        if candidate != index { candidate = index; enteredAt = now; return nil }
        guard now - enteredAt >= 0.12 else { return nil }
        armed = false; candidate = nil
        return index
    }
}

// Arm after the pointer enters, so edge summons can cross the desktop margin
// and keyboard summons can still be used before moving the mouse into them.
struct LauncherDepartureGate {
    private var entered = false
    private var outsideSince: TimeInterval?
    mutating func update(point: CGPoint, frame: CGRect, now: TimeInterval, paused: Bool) -> Bool {
        if paused { outsideSince = nil; return false }
        if frame.contains(point) { entered = true; outsideSince = nil; return false }
        guard entered else { return false }
        // A small tolerance prevents jitter at the border from hiding the panel.
        if frame.insetBy(dx: -8, dy: -8).contains(point) { outsideSince = nil; return false }
        guard let start = outsideSince else { outsideSince = now; return false }
        return now - start >= 0.18
    }
}
