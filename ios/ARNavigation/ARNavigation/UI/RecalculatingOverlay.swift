import SwiftUI

struct RecalculatingOverlay: View {
    var body: some View {
        HStack(spacing: 12) {
            ProgressView()
                .tint(.white)

            Text("Recalculating route...")
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 14)
        .background(.ultraThinMaterial)
        .background(Color.black.opacity(0.5))
        .cornerRadius(16)
        .transition(.scale.combined(with: .opacity))
    }
}
