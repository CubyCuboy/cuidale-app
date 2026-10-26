package com.example.cuidaleapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.cuidaleapp.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mapbox.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class SugerenciaUbicacion(
    val titulo: String,
    val subtitulo: String,
    val punto: Point
)

object LocationUtils {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /**
     * Captura la ubicación actual "One-Shot" del dispositivo usando FusedLocationProviderClient.
     * Revisa primero el caché lastLocation por velocidad instantánea y si no es reciente solicita
     * getCurrentLocation con alta precisión.
     */
    suspend fun obtenerUbicacionActualOneShot(context: Context): Point? {
        val hasFinePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFinePermission && !hasCoarsePermission) {
            return null
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)

            // 1. Probar primero lastLocation por respuesta instantánea
            @Suppress("MissingPermission")
            val lastLoc = try { fusedClient.lastLocation.await() } catch (_: Exception) { null }
            if (lastLoc != null && (System.currentTimeMillis() - lastLoc.time) < 30_000) {
                return Point.fromLngLat(lastLoc.longitude, lastLoc.latitude)
            }

            // 2. Si lastLocation no es reciente o no existe, obtener ubicación actual
            val cts = CancellationTokenSource()
            @Suppress("MissingPermission")
            val currentLoc = fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).await()

            if (currentLoc != null) {
                Point.fromLngLat(currentLoc.longitude, currentLoc.latitude)
            } else if (lastLoc != null) {
                Point.fromLngLat(lastLoc.longitude, lastLoc.latitude)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Consulta la API Geocoding Places de Mapbox para obtener sugerencias en vivo de calles,
     * avenidas, pasajes, direcciones y comunas de todo Chile con sus coordenadas exactas.
     */
    suspend fun buscarSugerenciasMapbox(context: Context, query: String): List<SugerenciaUbicacion> {
        val q = query.trim()
        if (q.length < 2) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val token = context.getString(R.string.mapbox_access_token)
                val encodedQuery = URLEncoder.encode(q, "UTF-8")
                val url = "https://api.mapbox.com/geocoding/v5/mapbox.places/$encodedQuery.json" +
                        "?country=cl&proximity=-70.6693,-33.4489&autocomplete=true" +
                        "&types=address,poi,place,neighborhood,locality" +
                        "&language=es&limit=6&access_token=$token"

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@withContext emptyList()
                    val json = JSONObject(bodyString)
                    val features = json.optJSONArray("features") ?: return@withContext emptyList()
                    val lista = mutableListOf<SugerenciaUbicacion>()

                    for (i in 0 until features.length()) {
                        val feat = features.optJSONObject(i) ?: continue
                        val placeName = feat.optString("place_name", "")
                        val text = feat.optString("text", "")
                        val center = feat.optJSONArray("center")

                        if (center != null && center.length() >= 2) {
                            val lng = center.optDouble(0)
                            val lat = center.optDouble(1)
                            val punto = Point.fromLngLat(lng, lat)

                            val partes = placeName.split(", ")
                            val titulo = text.ifBlank { partes.firstOrNull() ?: placeName }
                            val subtitulo = if (partes.size > 1) {
                                partes.drop(1).take(2).joinToString(", ")
                            } else "Chile"

                            lista.add(SugerenciaUbicacion(titulo, subtitulo, punto))
                        }
                    }
                    lista
                } else emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Geocodifica una cadena de texto (ej. "Plaza de Maipú, Santiago") en un objeto Point (longitud, latitud)
     * utilizando android.location.Geocoder nativo con manejo seguro de excepciones.
     */
    suspend fun geocodificarDireccion(context: Context, direccionTexto: String): Point? {
        if (direccionTexto.isBlank()) return null

        val geocoder = Geocoder(context, Locale.getDefault())

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        geocoder.getFromLocationName(
                            direccionTexto,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    val first = addresses.firstOrNull()
                                    if (first != null && continuation.isActive) {
                                        continuation.resume(Point.fromLngLat(first.longitude, first.latitude))
                                    } else if (continuation.isActive) {
                                        continuation.resume(null)
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            }
                        )
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(direccionTexto, 1)
                val first = addresses?.firstOrNull()
                if (first != null) {
                    Point.fromLngLat(first.longitude, first.latitude)
                } else null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Calcula la distancia en metros entre dos coordenadas geográficas.
     */
    fun calcularDistanciaMetros(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
