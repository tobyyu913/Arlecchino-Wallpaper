import AppKit
final class TestDelegate {
    var locked = false
    lazy var launcher = DesktopLauncher()
    func showPreferences() {}
}
let appDelegate = TestDelegate()
let application = NSApplication.shared
application.setActivationPolicy(.accessory)
UserDefaults.standard.register(defaults: ["launcherEnabled": true])
let launcher = DesktopLauncher(frontmostBundle: { "com.apple.Safari" })
launcher.rebuild()
let desktop = launcher.windows[0]
let desktopID = desktop.windowNumber
launcher.model.query = "desktop query"
launcher.model.section = "D"
func later(_ delay: Double, _ task: @escaping () -> Void) { DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: task) }
later(0.1) {
    launcher.reveal()
    later(0.4) {
        let panel = launcher.summonedWindow!
        precondition(panel !== desktop && panel.isVisible && desktop.isVisible)
        precondition(!desktop.presentation.summoned && desktop.level.rawValue < NSWindow.Level.normal.rawValue)
        precondition(launcher.panelModel !== launcher.model && launcher.panelModel.music === launcher.model.music)
        precondition(launcher.panelModel.query.isEmpty && launcher.panelModel.section == "★")
        launcher.panelModel.query = "panel query"
        precondition(launcher.model.query == "desktop query" && launcher.model.section == "D")
        launcher.dismiss()
        later(0.3) {
            precondition(!panel.isVisible && desktop.isVisible && desktop.windowNumber == desktopID)
            launcher.reveal()
            later(0.4) {
                precondition(launcher.summonedWindow === panel && panel.isVisible && desktop.isVisible)
                precondition(launcher.model.query == "desktop query" && launcher.panelModel.query == "panel query")
                let original = UserDefaults.standard.object(forKey: "launcherFavorites")
                launcher.panelModel.favorites = ["/tmp/arlecchino-test-favorite.app"]
                launcher.panelModel.save()
                precondition(launcher.model.favorites == launcher.panelModel.favorites)
                if let original { UserDefaults.standard.set(original, forKey: "launcherFavorites") }
                else { UserDefaults.standard.removeObject(forKey: "launcherFavorites") }
                NotificationCenter.default.post(name: Notification.Name("ArlecchinoFavoritesChanged"), object: nil)
                launcher.hideForLock()
                precondition(!desktop.isVisible && launcher.summonedWindow == nil)
                launcher.rebuild()
                precondition(launcher.windows.count == NSScreen.screens.count && launcher.windows.allSatisfy { $0.isVisible })
                launcher.windows.forEach { $0.close() }
                print("PASS: separate desktop/panel windows, dismiss/reopen preserves desktop, independent navigation, shared favorites/music and lock cleanup")
                exit(0)
            }
        }
    }
}
application.run()
