import Foundation
import CoreGraphics
import ImageIO

let source = CGContext(data: nil, width: 200, height: 100, bitsPerComponent: 8, bytesPerRow: 0,
    space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
source.setFillColor(CGColor(gray: 0, alpha: 1)); source.fill(CGRect(x: 0, y: 0, width: 200, height: 100))
source.setFillColor(CGColor(gray: 1, alpha: 1)); source.fill(CGRect(x: 150, y: 0, width: 50, height: 100))
let poster = source.makeImage()!
for size in [CGSize(width: 1200, height: 800), CGSize(width: 3200, height: 800), CGSize(width: 400, height: 800)] {
    let image = DesktopBacking.render(poster: poster, size: size, layout: .character)!
    let pixels = image.dataProvider!.data! as Data
    let offset = (Int(size.height) / 2) * image.bytesPerRow + (Int(size.width) - 2) * 4
    precondition(pixels[offset] > 245 && pixels[offset + 1] > 245, "Right-edge character is missing from system wallpaper")
    if size.width > 400 {
        let left = (Int(size.height) / 2) * image.bytesPerRow
        precondition(pixels[left] < 10, "Letterbox canvas must be black")
    }
}
for mode in DisplayLayout.allCases {
    let url = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("arlecchino-backing-\(mode.rawValue)-\(UUID().uuidString).png")
    defer { try? FileManager.default.removeItem(at: url) }
    try DesktopBacking.write(poster: poster, size: CGSize(width: 1440, height: 900), layout: mode, to: url)
    let decoded = CGImageSourceCreateImageAtIndex(CGImageSourceCreateWithURL(url as CFURL, nil)!, 0, nil)!
    precondition(decoded.width == 1440 && decoded.height == 900, "Wallpaper backing resolution mismatch")
}
print("PASS: portrait/landscape/ultrawide system backings retain right-edge image; black margins; PNG round-trip for all layouts")
