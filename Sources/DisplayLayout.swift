import Foundation
import CoreGraphics

/// All dimensions use macOS points; Retina backing scale never changes composition.
enum DisplayLayout: String, CaseIterable {
    case character, fit, fill

    func videoFrame(in bounds: CGRect, aspectRatio: CGFloat) -> CGRect {
        guard bounds.width > 0, bounds.height > 0, aspectRatio.isFinite, aspectRatio > 0 else { return .zero }
        if self == .character {
            // The original is black on the left. Extend that black canvas on wide
            // displays and crop only its empty left margin on narrower displays.
            let width = bounds.height * aspectRatio
            return CGRect(x: bounds.maxX - width, y: bounds.minY, width: width, height: bounds.height)
        }
        let heightForWidth = bounds.width / aspectRatio
        let height = self == .fit ? min(bounds.height, heightForWidth) : max(bounds.height, heightForWidth)
        let width = height * aspectRatio
        return CGRect(x: bounds.midX - width / 2, y: bounds.midY - height / 2, width: width, height: height)
    }
}
