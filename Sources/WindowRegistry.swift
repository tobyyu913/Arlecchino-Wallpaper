import Foundation

/// Preserve each display's window and video layer across redundant screen events.
final class WindowRegistry<Window> {
    private var byDisplay: [String: Window] = [:]
    private(set) var windows: [Window] = []
    var displayIDs: Set<String> { Set(byDisplay.keys) }

    func reconcile(displayIDs: [String], create: (String) -> Window,
                   update: (String, Window) -> Void, remove: (Window) -> Void) {
        let connected = Set(displayIDs)
        for id in Array(byDisplay.keys) where !connected.contains(id) {
            if let window = byDisplay.removeValue(forKey: id) { remove(window) }
        }
        var ordered: [Window] = []
        var seen: Set<String> = []
        for id in displayIDs where seen.insert(id).inserted {
            if let window = byDisplay[id] {
                update(id, window)
                ordered.append(window)
            } else {
                let window = create(id)
                byDisplay[id] = window
                ordered.append(window)
            }
        }
        windows = ordered
    }
}
