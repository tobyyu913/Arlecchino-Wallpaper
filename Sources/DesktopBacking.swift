import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

enum DesktopBacking {
    static func render(poster: CGImage, size: CGSize, layout: DisplayLayout) -> CGImage? {
        guard size.width > 0, size.height > 0,
              let context = CGContext(data: nil, width: Int(size.width), height: Int(size.height),
                bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return nil }
        let bounds = CGRect(origin: .zero, size: size)
        context.setFillColor(CGColor(gray: 0, alpha: 1)); context.fill(bounds)
        context.interpolationQuality = .high
        context.draw(poster, in: layout.videoFrame(in: bounds, aspectRatio: CGFloat(poster.width) / CGFloat(poster.height)))
        return context.makeImage()
    }
    static func write(poster: CGImage, size: CGSize, layout: DisplayLayout, to url: URL) throws {
        guard let image = render(poster: poster, size: size, layout: layout),
              let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
            throw NSError(domain: "ArlecchinoBacking", code: 1)
        }
        CGImageDestinationAddImage(destination, image, nil)
        guard CGImageDestinationFinalize(destination) else { throw NSError(domain: "ArlecchinoBacking", code: 2) }
    }
}
