import SwiftUI

struct OffRouteWarningBanner: View {
    let distanceToRoute: Float  // meters

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)

            Text("Off route by \(Int(distanceToRoute))m")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color.orange)
        .cornerRadius(12)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}
