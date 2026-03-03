import SwiftUI

struct TurnInstructionCard: View {
    let instruction: TurnInstruction

    var body: some View {
        HStack(spacing: 14) {
            // Icon
            Image(systemName: instruction.direction.sfSymbol)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 48, height: 48)
                .background(Color.blue)
                .cornerRadius(12)

            // Label + distance
            VStack(alignment: .leading, spacing: 2) {
                Text(instruction.direction.label)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)

                Text(formatDistance(instruction.distanceToTurn))
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.white)
            }

            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial)
        .background(Color.black.opacity(0.4))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.3), radius: 8, y: 4)
    }

    private func formatDistance(_ meters: Float) -> String {
        if meters >= 1000 {
            return String(format: "%.1f km", meters / 1000)
        } else if meters >= 100 {
            let rounded = Int((meters / 10).rounded()) * 10
            return "\(rounded) m"
        } else {
            return "\(Int(meters)) m"
        }
    }
}
