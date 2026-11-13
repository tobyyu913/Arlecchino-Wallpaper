import Foundation
import CoreGraphics

func approximately(_ a: CGFloat, _ b: CGFloat) -> Bool { abs(a - b) < 0.001 }
let aspect: CGFloat = 1920.0 / 1082.0
let sizes: [CGSize] = [
    CGSize(width: 1280, height: 800), CGSize(width: 1440, height: 900),
    CGSize(width: 1512, height: 982), CGSize(width: 1728, height: 1117),
    CGSize(width: 1920, height: 1080), CGSize(width: 2560, height: 1440),
    CGSize(width: 3840, height: 2160), CGSize(width: 5120, height: 2880),
    CGSize(width: 6016, height: 3384), CGSize(width: 3440, height: 1440),
    CGSize(width: 5120, height: 1440), CGSize(width: 1080, height: 1920)
]
for size in sizes {
    // Offset origins exercise displays to the left of and above the main display.
    for origin in [CGPoint.zero, CGPoint(x: -2560, y: 900)] {
        let bounds = CGRect(origin: origin, size: size)
        for mode in DisplayLayout.allCases {
            let frame = mode.videoFrame(in: bounds, aspectRatio: aspect)
            precondition(approximately(frame.width / frame.height, aspect), "Image is stretched")
            switch mode {
            case .character:
                precondition(approximately(frame.maxX, bounds.maxX), "Character left the right edge")
                precondition(approximately(frame.minY, bounds.minY) && approximately(frame.height, bounds.height), "Character cropped vertically")
                // The occupied rightmost 31% remains visible, including on portrait monitors.
                precondition(frame.maxX - frame.width * 0.31 >= bounds.minX, "Character cropped horizontally")
            case .fit:
                precondition(frame.minX >= bounds.minX - 0.001 && frame.maxX <= bounds.maxX + 0.001)
                precondition(frame.minY >= bounds.minY - 0.001 && frame.maxY <= bounds.maxY + 0.001)
            case .fill:
                precondition(frame.minX <= bounds.minX + 0.001 && frame.maxX >= bounds.maxX - 0.001)
                precondition(frame.minY <= bounds.minY + 0.001 && frame.maxY >= bounds.maxY - 0.001)
            }
        }
    }
}
precondition(DisplayLayout.character.videoFrame(in: .zero, aspectRatio: aspect) == .zero)
precondition(DisplayLayout.character.videoFrame(in: CGRect(x: 0, y: 0, width: 100, height: 100), aspectRatio: 0) == .zero)
print("PASS: 12 display sizes, 3 layouts, offset displays, aspect preservation, character visibility, invalid sizes")
