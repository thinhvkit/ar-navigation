package com.ideals.arnav.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchResult(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double
)

class GeocodingService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        query: String,
        proximityLat: Double,
        proximityLng: Double
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json" +
                "?proximity=%.6f,%.6f".format(proximityLng, proximityLat) +
                "&limit=5" +
                "&access_token=${RouteRepository.MAPBOX_TOKEN}"

            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Geocoding API returned status ${response.code}")
                )
            }

            val body = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val json = JSONObject(body)
            val features = json.getJSONArray("features")
            val results = mutableListOf<SearchResult>()

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val name = feature.getString("text")
                val address = feature.optString("place_name", name)
                val center = feature.getJSONArray("center")
                val lng = center.getDouble(0)
                val lat = center.getDouble(1)
                results.add(SearchResult(name, address, lat, lng))
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
