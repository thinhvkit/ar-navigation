import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = NavigationViewModel()
    @State private var showDestinationPicker = false

    var body: some View {
        ZStack {
            // AR Camera View (full screen)
            ARViewContainer(viewModel: viewModel)
                .ignoresSafeArea()

            if isNavigating {
                // --- Navigating HUD ---
                VStack(spacing: 0) {
                    // Turn instruction card at top
                    if let instruction = viewModel.state.turnInstruction {
                        TurnInstructionCard(instruction: instruction)
                            .padding(.horizontal, 16)
                            .padding(.top, 60)
                            .id(instruction.turnPointIndex)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    Spacer()

                    // Bottom cluster
                    VStack(spacing: 8) {
                        // Off-route warning
                        if viewModel.state.offRouteWarningVisible {
                            OffRouteWarningBanner(distanceToRoute: viewModel.state.distanceToRoute)
                        }

                        // Progress bar
                        RouteProgressBar(
                            progress: viewModel.state.routeProgress,
                            distanceRemaining: viewModel.state.distanceRemaining
                        )

                        // Mini-map + destination button row
                        HStack(alignment: .bottom) {
                            MiniMapView(
                                route: viewModel.state.route,
                                userLat: viewModel.state.userLat,
                                userLng: viewModel.state.userLng,
                                heading: viewModel.state.heading,
                                hasPosition: true,
                                currentSegment: viewModel.state.currentSegment
                            )

                            Spacer()

                            // New destination button
                            Button {
                                showDestinationPicker = true
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: "arrow.triangle.turn.up.right.diamond")
                                        .font(.system(size: 14, weight: .semibold))
                                    Text("New dest.")
                                        .font(.system(size: 14, weight: .semibold))
                                }
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(Color.blue)
                                .cornerRadius(24)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 16)
                    }
                }
            } else {
                // --- Non-navigating: status bar, GPS accuracy, destination button ---
                VStack {
                    HStack(alignment: .top) {
                        // Status message
                        Text(viewModel.state.statusMessage)
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color.black.opacity(0.5))
                            .cornerRadius(8)

                        Spacer()

                        VStack(spacing: 8) {
                            // Camera button
                            Button(action: {
                                // Placeholder for screenshot
                            }) {
                                Image(systemName: "camera")
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundColor(.white)
                                    .frame(width: 44, height: 44)
                                    .background(Color.black.opacity(0.4))
                                    .clipShape(Circle())
                            }

                            // GPS accuracy
                            if viewModel.state.gpsAccuracy > 0 {
                                Text(String(format: "GPS ±%.0fm", viewModel.state.gpsAccuracy))
                                    .font(.system(size: 12))
                                    .foregroundColor(viewModel.state.gpsAccuracy < 10 ? .green : .yellow)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.black.opacity(0.5))
                                    .cornerRadius(8)
                            }
                        }
                    }
                    .padding(.top, 60)
                    .padding(.horizontal, 16)

                    Spacer()

                    // Destination button (bottom-right)
                    HStack {
                        Spacer()
                        Button {
                            showDestinationPicker = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "magnifyingglass")
                                    .font(.system(size: 14, weight: .semibold))
                                Text("Set destination")
                                    .font(.system(size: 14, weight: .semibold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.blue)
                            .cornerRadius(24)
                        }
                        .disabled(!hasGPS)
                        .opacity(hasGPS ? 1.0 : 0.5)
                        .padding(.trailing, 16)
                        .padding(.bottom, 40)
                    }
                }
            }

            // Recalculating overlay (centered, above everything)
            if viewModel.state.isRecalculating {
                RecalculatingOverlay()
            }

            // GPS signal lost banner (top, overlays everything)
            if viewModel.state.gpsSignalLost {
                VStack {
                    GPSLostBanner()
                        .padding(.top, 120)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }

            // Thermal warning (top-right, subtle)
            if viewModel.state.thermalState == .serious || viewModel.state.thermalState == .critical {
                VStack {
                    HStack {
                        Spacer()
                        ThermalWarningBadge(state: viewModel.state.thermalState)
                            .padding(.top, 60)
                            .padding(.trailing, 16)
                    }
                    Spacer()
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: isNavigating)
        .animation(.easeInOut(duration: 0.3), value: viewModel.state.offRouteWarningVisible)
        .animation(.easeInOut(duration: 0.3), value: viewModel.state.isRecalculating)
        .animation(.easeInOut(duration: 0.3), value: viewModel.state.gpsSignalLost)
        .onAppear {
            viewModel.start()
        }
        .onDisappear {
            viewModel.cleanup()
        }
        .sheet(isPresented: $showDestinationPicker) {
            DestinationPickerView(viewModel: viewModel)
        }
    }

    private var isNavigating: Bool {
        if case .navigating = viewModel.state.phase { return true }
        return false
    }

    private var hasGPS: Bool {
        if case .waitingForGPS = viewModel.state.phase { return false }
        return true
    }
}

// MARK: - GPS Lost Banner

private struct GPSLostBanner: View {
    @State private var pulse = false

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "location.slash.fill")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
                .opacity(pulse ? 0.5 : 1.0)

            Text("GPS signal lost")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color.red.opacity(0.85))
        .cornerRadius(12)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
    }
}

// MARK: - Thermal Warning Badge

private struct ThermalWarningBadge: View {
    let state: ProcessInfo.ThermalState

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "thermometer.sun.fill")
                .font(.system(size: 11))
            Text(state == .critical ? "Hot" : "Warm")
                .font(.system(size: 11, weight: .medium))
        }
        .foregroundColor(.white)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(state == .critical ? Color.red.opacity(0.8) : Color.orange.opacity(0.7))
        .cornerRadius(8)
    }
}
