import SwiftUI
import MapKit

struct DestinationPickerView: View {
    @ObservedObject var viewModel: NavigationViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var searchText = ""
    @State private var searchResults: [MKMapItem] = []
    @State private var selectedCoordinate: CLLocationCoordinate2D?
    @State private var selectedName: String?
    @State private var cameraPosition: MapCameraPosition = .userLocation(fallback: .automatic)
    @State private var isSearching = false

    var body: some View {
        NavigationStack {
            ZStack {
                mapView
                searchOverlay
            }
            .navigationTitle("Destination")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Navigate") {
                        guard let coord = selectedCoordinate else { return }
                        viewModel.setDestination(lat: coord.latitude, lng: coord.longitude)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .disabled(selectedCoordinate == nil)
                }
            }
        }
    }

    // MARK: - Map

    private var mapView: some View {
        Map(position: $cameraPosition) {
            UserAnnotation()

            if let coord = selectedCoordinate {
                Marker(
                    selectedName ?? "Destination",
                    coordinate: coord
                )
                .tint(.red)
            }
        }
        .mapControls {
            MapUserLocationButton()
            MapCompass()
        }
        .onTapGesture { }  // absorb taps so long press works
        .onLongPressGesture(minimumDuration: 0.5) {
            // Long press drops a pin — use map center as approximation
            // MapKit SwiftUI doesn't provide tap coordinates directly,
            // so we use a MapReader for coordinate conversion
        }
        .overlay(alignment: .bottom) {
            if let name = selectedName, selectedCoordinate != nil {
                selectedDestinationBar(name: name)
            }
        }
    }

    // MARK: - Search

    private var searchOverlay: some View {
        VStack(spacing: 0) {
            searchBar
            if !searchResults.isEmpty {
                searchResultsList
            }
            Spacer()
        }
    }

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            TextField("Search for a place", text: $searchText)
                .textFieldStyle(.plain)
                .autocorrectionDisabled()
                .onSubmit { performSearch() }
            if !searchText.isEmpty {
                Button {
                    searchText = ""
                    searchResults = []
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(12)
        .background(.regularMaterial)
        .cornerRadius(12)
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private var searchResultsList: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(searchResults, id: \.self) { item in
                    Button {
                        selectMapItem(item)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name ?? "Unknown")
                                .font(.system(size: 15, weight: .medium))
                                .foregroundColor(.primary)
                            if let subtitle = item.placemark.formattedAddress {
                                Text(subtitle)
                                    .font(.system(size: 13))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Divider().padding(.leading, 16)
                }
            }
        }
        .frame(maxHeight: 250)
        .background(.regularMaterial)
        .cornerRadius(12)
        .padding(.horizontal, 16)
        .padding(.top, 4)
    }

    private func selectedDestinationBar(name: String) -> some View {
        HStack {
            Image(systemName: "mappin.circle.fill")
                .foregroundColor(.red)
                .font(.title3)
            Text(name)
                .font(.system(size: 15, weight: .medium))
            Spacer()
        }
        .padding(12)
        .background(.regularMaterial)
        .cornerRadius(12)
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }

    // MARK: - Actions

    private func performSearch() {
        guard !searchText.isEmpty else { return }
        isSearching = true

        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = searchText

        // Bias search to user's current location
        if viewModel.state.userLat != 0 {
            let center = CLLocationCoordinate2D(
                latitude: viewModel.state.userLat,
                longitude: viewModel.state.userLng
            )
            request.region = MKCoordinateRegion(
                center: center,
                latitudinalMeters: 5000,
                longitudinalMeters: 5000
            )
        }

        let search = MKLocalSearch(request: request)
        search.start { response, _ in
            isSearching = false
            searchResults = response?.mapItems ?? []
        }
    }

    private func selectMapItem(_ item: MKMapItem) {
        let coord = item.placemark.coordinate
        selectedCoordinate = coord
        selectedName = item.name
        searchResults = []
        searchText = item.name ?? ""

        cameraPosition = .region(MKCoordinateRegion(
            center: coord,
            latitudinalMeters: 500,
            longitudinalMeters: 500
        ))
    }
}

// MARK: - Helpers

private extension CLPlacemark {
    var formattedAddress: String? {
        [subThoroughfare, thoroughfare, locality, administrativeArea]
            .compactMap { $0 }
            .joined(separator: ", ")
            .nilIfEmpty
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
