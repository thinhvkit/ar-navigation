import Foundation
import simd

enum NavigationPhase {
    case waitingForGPS
    case selectingDestination
    case fetchingRoute
    case navigating
    case recalculating
    case error(String)
}

enum TurnDirection: Equatable {
    case straight, slightLeft, left, sharpLeft
    case slightRight, right, sharpRight, uTurn, arrive

    var sfSymbol: String {
        switch self {
        case .straight:    return "arrow.up"
        case .slightLeft:  return "arrow.up.left"
        case .left:        return "arrow.turn.up.left"
        case .sharpLeft:   return "arrow.turn.down.left"
        case .slightRight: return "arrow.up.right"
        case .right:       return "arrow.turn.up.right"
        case .sharpRight:  return "arrow.turn.down.right"
        case .uTurn:       return "arrow.uturn.down"
        case .arrive:      return "flag.checkered"
        }
    }

    var label: String {
        switch self {
        case .straight:    return "Continue straight"
        case .slightLeft:  return "Slight left"
        case .left:        return "Turn left"
        case .sharpLeft:   return "Sharp left"
        case .slightRight: return "Slight right"
        case .right:       return "Turn right"
        case .sharpRight:  return "Sharp right"
        case .uTurn:       return "U-turn"
        case .arrive:      return "Arrive"
        }
    }
}

struct TurnInstruction: Equatable {
    let direction: TurnDirection
    let distanceToTurn: Float     // meters from start of route to turn point
    let turnPointIndex: Int       // index into route waypoints
}

struct NavigationState {
    var phase: NavigationPhase = .waitingForGPS
    var userLat: Double = 0
    var userLng: Double = 0
    var gpsAccuracy: Float = 0
    var route: [LatLng] = []
    var routeWorldPositions: [SIMD3<Float>] = []
    var currentSegment: Int = 0
    var distanceToRoute: Float = 0
    var heading: Double = 0  // degrees from true north
    var isTracking = false
    var statusMessage = "Waiting for GPS..."

    // Turn-by-turn
    var turnInstruction: TurnInstruction?
    var turnInstructions: [TurnInstruction] = []

    // Progress
    var totalRouteDistance: Float = 0
    var distanceAlongRoute: Float = 0
    var distanceRemaining: Float = 0
    var routeProgress: Float = 0  // 0.0...1.0

    // Off-route
    var offRouteWarningVisible = false
    var isRecalculating = false

    // Signal quality
    var gpsSignalLost = false
    var thermalState: ProcessInfo.ThermalState = .nominal
}
