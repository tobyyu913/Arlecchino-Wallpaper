import AppKit
final class TestDelegate {
    var locked = false
    lazy var launcher = DesktopLauncher()
    func showPreferences() {}
}
let appDelegate = TestDelegate()
let application = NSApplication.shared
let model = LauncherModel()
model.apps = (0..<2000).map { i in
    let letter = String(UnicodeScalar(65 + i / 77)!)
    return DesktopApp(url: URL(fileURLWithPath: "/tmp/arlecchino-performance-\(i).app"), name: "\(letter) app \(i)")
}
model.favorites = [model.apps[11].id, model.apps[8].id, "/tmp/unavailable.app"]
precondition(model.visible.map(\.id) == [model.apps[11].id, model.apps[8].id])
model.section = "A"
let rounds = 10
var indexedHeads = 0, linearHeads = 0
let start = CFAbsoluteTimeGetCurrent()
for _ in 0..<rounds { for app in model.apps { if model.isSectionHead(app) { indexedHeads += 1 } } }
let indexedTime = CFAbsoluteTimeGetCurrent() - start
let referenceStart = CFAbsoluteTimeGetCurrent()
for _ in 0..<rounds { for app in model.apps { if model.apps.first(where: { $0.section == app.section })?.id == app.id { linearHeads += 1 } } }
let linearTime = CFAbsoluteTimeGetCurrent() - referenceStart
precondition(indexedHeads == linearHeads && indexedHeads == 26 * rounds)
print(String(format: "PASS: 2,000-app section index matches reference; %d queries: indexed %.4fs vs linear %.4fs", rounds * model.apps.count, indexedTime, linearTime))
var refreshes = 0
model.catalogUpdated = { _ in refreshes += 1 }
model.refresh(force: true)
for _ in 0..<30 { model.refresh() }
DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
    precondition(refreshes == 1, "Repeated summons must coalesce into one catalog scan")
    for _ in 0..<30 { model.refresh() }
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
        precondition(refreshes == 1, "Warm summons must reuse the catalog")
        print("PASS: concurrent and warm catalog refresh requests reuse one scan")
        exit(0)
    }
}
application.run()
