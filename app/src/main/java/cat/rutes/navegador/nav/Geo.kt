package cat.rutes.navegador.nav

import cat.rutes.navegador.data.LatLon
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS = 6_371_000.0

    /** Distància en metres entre dos punts (fórmula de l'haversinus). */
    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * Projecta el punt [p] sobre el segment [a]-[b].
     * Retorna (t, distància en metres), on t és la posició relativa dins el segment (0..1).
     * Fa servir una aproximació plana local, prou precisa per a segments curts de carretera.
     */
    fun projectOnSegment(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val ky = EARTH_RADIUS * PI / 180
        val kx = ky * cos(Math.toRadians(p.lat))
        val ax = (a.lon - p.lon) * kx
        val ay = (a.lat - p.lat) * ky
        val dx = (b.lon - a.lon) * kx
        val dy = (b.lat - a.lat) * ky
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        val x = ax + t * dx
        val y = ay + t * dy
        return t to sqrt(x * x + y * y)
    }

    fun interpolate(a: LatLon, b: LatLon, t: Double) =
        LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
}
