import AppKit
final class TestDelegate {
    var locked = false
    lazy var launcher = DesktopLauncher()
    func showPreferences() {}
}
let appDelegate = TestDelegate()
let application = NSApplication.shared
let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 400), styleMask: .borderless, backing: .buffered, defer: false)
let root = NSView(frame: NSRect(x: 0, y: 0, width: 400, height: 400))
let scroll = NSScrollView(frame: NSRect(x: 0, y: 0, width: 350, height: 400))
scroll.documentView = NSView(frame: NSRect(x: 0, y: 0, width: 350, height: 2000))
scroll.hasVerticalScroller = true
root.addSubview(scroll)
let stripe = AlphabetInputView(frame: NSRect(x: 350, y: 0, width: 40, height: 400))
root.addSubview(stripe)
window.contentView = root
var positions: [(CGFloat, Bool)] = []
var swipes = 0
stripe.onPosition = { positions.append(($0, $1)) }
stripe.onScroll = { swipes += 1 }
let initial = scroll.contentView.bounds.origin.y
let event = NSEvent(cgEvent: CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 1, wheel1: 120, wheel2: 0, wheel3: 0)!)!
stripe.scrollWheel(with: event)
DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
    let moved = scroll.contentView.bounds.origin.y != initial
    precondition(swipes == 1 && moved, "Native scrolling must move the document")
    precondition(AlphabetInputView.listScrollView(in: root) === scroll)
    for (type, ended) in [(NSEvent.EventType.leftMouseDown, false), (.leftMouseDragged, false), (.leftMouseUp, true)] {
        let point = stripe.convert(NSPoint(x: 20, y: 200), to: nil)
        let mouse = NSEvent.mouseEvent(with: type, location: point, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: 1)!
        stripe.select(mouse, ended: ended)
    }
    precondition(positions.count == 3 && positions.allSatisfy { abs($0.0 - 0.5) < 0.001 } && positions.last!.1)
    stripe.updateTrackingAreas()
    precondition(stripe.trackingAreas.count == 1 && stripe.trackingAreas[0].options.contains(.activeAlways))
    for y: CGFloat in [100, 300] {
        let point = stripe.convert(NSPoint(x: 20, y: y), to: nil)
        let hover = NSEvent.mouseEvent(with: .mouseMoved, location: point, modifierFlags: [], timestamp: 0, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 0, pressure: 0)!
        stripe.mouseMoved(with: hover)
    }
    precondition(positions.count == 5 && abs(positions[3].0 - 0.25) < 0.001 && abs(positions[4].0 - 0.75) < 0.001 && !positions[4].1)
    precondition(AlphabetWave.displacement(y: 200, height: 400, position: nil) == 0)
    precondition(abs(AlphabetWave.displacement(y: 200, height: 400, position: 0.5) - 54) < 0.001)
    precondition(abs(AlphabetWave.displacement(y: 150, height: 400, position: 0.5) - AlphabetWave.displacement(y: 250, height: 400, position: 0.5)) < 0.001)
    precondition(AlphabetWave.displacement(y: 0, height: 400, position: 0.5) < 1)
    var gate = LauncherEdgeGate()
    let displays = [CGRect(x: 0, y: 0, width: 1920, height: 1080), CGRect(x: -1280, y: 0, width: 1280, height: 800)]
    precondition(gate.update(point: CGPoint(x: 0, y: 400), frames: displays, now: 1, enabled: true) == nil)
    precondition(gate.update(point: CGPoint(x: 0, y: 400), frames: displays, now: 1.16, enabled: true) == 0)
    precondition(gate.update(point: CGPoint(x: 0, y: 400), frames: displays, now: 2, enabled: true) == nil)
    _ = gate.update(point: CGPoint(x: 50, y: 400), frames: displays, now: 3, enabled: true)
    _ = gate.update(point: CGPoint(x: -1280, y: 400), frames: displays, now: 4, enabled: true)
    precondition(gate.update(point: CGPoint(x: -1280, y: 400), frames: displays, now: 4.16, enabled: true) == 1)
    _ = gate.update(point: CGPoint(x: 50, y: 400), frames: displays, now: 5, enabled: false)
    precondition(gate.update(point: CGPoint(x: 0, y: 400), frames: displays, now: 6, enabled: true) == nil)
    precondition(gate.update(point: CGPoint(x: 0, y: 400), frames: displays, now: 7, enabled: true) == nil)
    _ = gate.update(point: CGPoint(x: 50, y: 400), frames: displays, now: 8, enabled: true)
    _ = gate.update(point: CGPoint(x: 0, y: 400), frames: displays, now: 9, enabled: true)
    precondition(gate.update(point: CGPoint(x: 0, y: 400), frames: displays, now: 9.16, enabled: true) == 0)
    var departure = LauncherDepartureGate()
    let panel = CGRect(x: 24, y: 0, width: 474, height: 820)
    precondition(!departure.update(point: CGPoint(x: 0, y: 400), frame: panel, now: 0, paused: false))
    precondition(!departure.update(point: CGPoint(x: 100, y: 400), frame: panel, now: 1, paused: false))
    precondition(!departure.update(point: CGPoint(x: 600, y: 400), frame: panel, now: 2, paused: false))
    precondition(!departure.update(point: CGPoint(x: 600, y: 400), frame: panel, now: 2.1, paused: false))
    precondition(departure.update(point: CGPoint(x: 600, y: 400), frame: panel, now: 2.2, paused: false))
    precondition(!departure.update(point: CGPoint(x: 600, y: 400), frame: panel, now: 3, paused: true))
    precondition(!departure.update(point: CGPoint(x: 600, y: 400), frame: panel, now: 4, paused: false))
    precondition(!departure.update(point: CGPoint(x: 100, y: 400), frame: panel, now: 4.1, paused: false))
    precondition(!departure.update(point: CGPoint(x: 500, y: 400), frame: panel, now: 5, paused: false))
    print("PASS: pointer departure grace, reentry, border tolerance and menu pause;  edge dwell, multi-display targeting, rearming and lock suppression;  wave peaks at cursor and relaxes symmetrically;  hover follows cursor without clicking;  stripe forwards native pixel scrolling; drag and release track pointer position")
    exit(0)
}
application.run()
