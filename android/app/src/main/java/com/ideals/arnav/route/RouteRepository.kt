package com.ideals.arnav.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RouteRepository {

    companion object {
        const val MAPBOX_TOKEN = "YOUR_MAPBOX_PUBLIC_TOKEN"
        private const val BASE_URL =
            "https://api.mapbox.com/directions/v5/mapbox/walking"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch walking route from Mapbox Directions API.
     * Returns the list of waypoints from the route geometry.
     */
    suspend fun fetchRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<RouteInfo> = withContext(Dispatchers.IO) {
        try {
            // Mapbox expects coordinates as lng,lat
            val coords = "%.6f,%.6f;%.6f,%.6f".format(
                originLng, originLat, destLng, destLat
            )
            val url = "$BASE_URL/$coords?geometries=geojson&overview=full&steps=true&access_token=$MAPBOX_TOKEN"

            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Mapbox API returned status ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val json = JSONObject(body)
            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) {
                return@withContext Result.failure(Exception("No routes found"))
            }

            val route = routes.getJSONObject(0)
            val distance = route.getDouble("distance")
            val duration = route.getDouble("duration")
            val coordinates = route
                .getJSONObject("geometry")
                .getJSONArray("coordinates")

            val waypoints = mutableListOf<LatLng>()
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                // Mapbox returns [lng, lat], convert to our LatLng
                waypoints.add(LatLng(lat = coord.getDouble(1), lng = coord.getDouble(0)))
            }

            // Parse turn-by-turn steps from legs
            val steps = mutableListOf<TurnStep>()
            val legs = route.optJSONArray("legs")
            if (legs != null) {
                for (l in 0 until legs.length()) {
                    val leg = legs.getJSONObject(l)
                    val legSteps = leg.optJSONArray("steps") ?: continue
                    for (s in 0 until legSteps.length()) {
                        val step = legSteps.getJSONObject(s)
                        val maneuver = step.getJSONObject("maneuver")
                        val loc = maneuver.getJSONArray("location")
                        steps.add(
                            TurnStep(
                                maneuverType = maneuver.getString("type"),
                                maneuverModifier = if (maneuver.has("modifier")) maneuver.getString("modifier") else null,
                                instruction = maneuver.optString("instruction", ""),
                                distance = step.getDouble("distance"),
                                duration = step.getDouble("duration"),
                                location = LatLng(lat = loc.getDouble(1), lng = loc.getDouble(0))
                            )
                        )
                    }
                }
            }

            Result.success(RouteInfo(waypoints, distance, duration, steps))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
