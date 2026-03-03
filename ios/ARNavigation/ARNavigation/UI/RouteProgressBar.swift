import SwiftUI

struct RouteProgressBar: View {
    let progress: Float       // 0.0...1.0
    let distanceRemaining: Float  // meters

    var body: some View {
        VStack(spacing: 4) {
            Text(formatRemaining(distanceRemaining))
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.white.opacity(0.9))

            GeometryReader { geo in
                let width = geo.size.width
                let fillWidth = max(0, min(width, CGFloat(progress) * width))

                ZStack(alignment: .leading) {
                    // Track background
                    Capsule()
                        .fill(Color.white.opacity(0.2))
                        .frame(height: 6)

                    // Fill
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [.blue, .cyan],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: fillWidth, height: 6)

                    // Dot at progress edge
                    if fillWidth > 4 {
                        Circle()
                            .fill(.white)
                            .frame(width: 10, height: 10)
                            .offset(x: fillWidth - 5)
                    }
                }
            }
            .frame(height: 10)
        }
        .padding(.horizontal, 16)
    }

    private func formatRemaining(_ meters: Float) -> String {
        if meters >= 1000 {
            return String(format: "%.1f km remaining", meters / 1000)
        } else {
            return "\(Int(meters)) m remaining"
        }
    }
}
