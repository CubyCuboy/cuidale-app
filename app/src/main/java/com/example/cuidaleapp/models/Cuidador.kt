package com.example.cuidaleapp.models

import androidx.compose.runtime.Immutable
import com.google.firebase.firestore.IgnoreExtraProperties
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point

@Immutable
@IgnoreExtraProperties
data class Cuidador(
    val id: String = "",
    val nombre: String = "",
    val especialidad: String = "",
    val comuna: String = "",
    val disponible: Boolean = true,
    val latitud: Double = -33.4489,
    val longitud: Double = -70.6693,
    val fotoUrl: String = "",
    val telefono: String = "",
    val region: String = "Región Metropolitana"
)

fun Cuidador.toFeature(): Feature {
    val point = Point.fromLngLat(longitud, latitud)
    val feature = Feature.fromGeometry(point, null, id)
    feature.addStringProperty("id", id)
    feature.addStringProperty("nombre", nombre)
    feature.addStringProperty("especialidad", especialidad)
    feature.addStringProperty("comuna", comuna)
    feature.addBooleanProperty("disponible", disponible)
    feature.addNumberProperty("latitud", latitud)
    feature.addNumberProperty("longitud", longitud)
    if (fotoUrl.isNotBlank()) {
        feature.addStringProperty("fotoUrl", fotoUrl)
    }
    return feature
}

fun List<Cuidador>.toFeatureCollection(): FeatureCollection {
    val features = this.map { it.toFeature() }
    return FeatureCollection.fromFeatures(features)
}
