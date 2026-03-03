import Foundation

/// Fetches walking routes from the Mapbox Directions API.
final class RouteService {
    private let token = "YOUR_MAPBOX_PUBLIC_TOKEN"
    private let baseURL = "https://api.mapbox.com/directions/v5/mapbox/walking"

    func fetchRoute(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double
    ) async throws -> RouteInfo {
        // Mapbox expects coordinates as lng,lat
        let coords = String(
            format: "%.6f,%.6f;%.6f,%.6f",
            originLng, originLat, destLng, destLat
        )
        let urlString = "\(baseURL)/\(coords)?geometries=geojson&overview=full&access_token=\(token)"

        guard let url = URL(string: urlString) else {
            throw RouteError.invalidURL
        }

        let (data, response) = try await URLSession.shared.data(from: url)

        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw RouteError.httpError(
                (response as? HTTPURLResponse)?.statusCode ?? -1
            )
        }

        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let routes = json["routes"] as? [[String: Any]],
              let firstRoute = routes.first,
              let geometry = firstRoute["geometry"] as? [String: Any],
              let coordinates = geometry["coordinates"] as? [[Double]]
        else {
            throw RouteError.parseError
        }

        let distance = firstRoute["distance"] as? Double ?? 0
        let duration = firstRoute["duration"] as? Double ?? 0

        // Convert from Mapbox [lng, lat] to our LatLng
        let waypoints = coordinates.map { coord in
            LatLng(lat: coord[1], lng: coord[0])
        }

        guard !waypoints.isEmpty else {
            throw RouteError.noRoute
        }

        return RouteInfo(
            waypoints: waypoints,
            distanceMeters: distance,
            durationSeconds: duration
        )
    }
}

enum RouteError: LocalizedError {
    case invalidURL
    case httpError(Int)
    case parseError
    case noRoute

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid URL"
        case .httpError(let code): return "HTTP error \(code)"
        case .parseError: return "Failed to parse route response"
        case .noRoute: return "No route found"
        }
    }
}
