import Foundation

final class Window { let display: String; init(_ display: String) { self.display = display } }
let registry = WindowRegistry<Window>()
var created = 0, updated = 0, removed = 0
func sync(_ ids: [String]) {
    registry.reconcile(displayIDs: ids, create: { created += 1; return Window($0) },
        update: { _, _ in updated += 1 }, remove: { _ in removed += 1 })
}
sync(["built-in"])
let original = registry.windows[0]
for _ in 0..<100 { sync(["built-in"]) }
precondition(created == 1 && removed == 0 && registry.windows[0] === original,
    "Returning to desktop must not replace its window or AVPlayerLayer")
sync(["built-in", "external"])
let external = registry.windows[1]
precondition(created == 2 && registry.windows[0] === original)
sync(["external", "built-in"])
precondition(registry.windows[0] === external && registry.windows[1] === original)
sync(["built-in"])
precondition(removed == 1 && registry.windows[0] === original)
sync(["built-in", "built-in"])
precondition(registry.windows.count == 1 && created == 2)
sync([])
precondition(removed == 2 && registry.windows.isEmpty)
print("PASS: 100 redundant screen events preserve window identity; hot-plug, reorder, removal, and duplicates")
