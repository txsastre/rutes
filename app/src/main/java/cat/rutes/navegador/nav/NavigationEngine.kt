package cat.rutes.navegador.nav

import cat.rutes.navegador.data.LatLon
import cat.rutes.navegador.data.Profile
import cat.rutes.navegador.data.Route
import cat.rutes.navegador.data.RouteStep

/** Estat de la navegació després de cada posició GPS. */
data class NavUpdate(
    val snapped: LatLon,
    val distanceFromRoute: Double,
    val remainingDistance: Double,
    val remainingDuration: Double,
    val nextStepIndex: Int?,
    val nextStep: RouteStep?,
    val distanceToNextStep: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
)

/**
 * Segueix la posició de l'usuari sobre una ruta: troba on és, quina és la propera
 * maniobra, detecta si s'ha desviat i decideix quan cal avisar per veu.
 * És Kotlin pur (sense Android), per poder-ho provar amb tests unitaris.
 */
class NavigationEngine(val route: Route) {
    private val pts = route.points
    private val cumulative = DoubleArray(pts.size)
    private val stepAlong: DoubleArray
    val totalDistance: Double

    private var lastSegment = -1
    private var offRouteCount = 0
    private val announced = mutableSetOf<Pair<Int, Int>>()

    /** Llindars d'avís en metres (lluny, a prop, ara) segons el mitjà de transport. */
    private val thresholds = when (route.profile) {
        Profile.CAR -> doubleArrayOf(800.0, 200.0, 40.0)
        Profile.BIKE -> doubleArrayOf(300.0, 80.0, 20.0)
        Profile.FOOT -> doubleArrayOf(150.0, 40.0, 12.0)
    }

    init {
        require(pts.size >= 2) { "La ruta necessita com a mínim 2 punts" }
        for (i in 1 until pts.size) cumulative[i] = cumulative[i - 1] + Geo.distance(pts[i - 1], pts[i])
        totalDistance = cumulative.last()

        // Posició (distància recorreguda) de cada maniobra sobre la línia de la ruta.
        var from = 0
        stepAlong = DoubleArray(route.steps.size) { k ->
            val loc = route.steps[k].location
            var best = from
            var bestD = Double.MAX_VALUE
            for (i in from until pts.size) {
                val d = Geo.distance(loc, pts[i])
                if (d < bestD) { bestD = d; best = i }
            }
            from = best
            cumulative[best]
        }
    }

    fun update(pos: LatLon, accuracy: Float = 10f): NavUpdate {
        // Busca el segment més proper. Després de la primera posició, només a prop de l'últim,
        // per no saltar a un altre tram de la ruta que passi pel mateix lloc.
        val range = if (lastSegment < 0) 0 until pts.size - 1
        else (lastSegment - 10).coerceAtLeast(0) until (lastSegment + 300).coerceAtMost(pts.size - 1)

        var bestSeg = range.first
        var bestT = 0.0
        var bestD = Double.MAX_VALUE
        for (i in range) {
            val (t, d) = Geo.projectOnSegment(pos, pts[i], pts[i + 1])
            if (d < bestD) { bestD = d; bestSeg = i; bestT = t }
        }

        val threshold = maxOf(35.0, accuracy * 1.5)
        if (bestD > threshold) offRouteCount++ else { offRouteCount = 0; lastSegment = bestSeg }
        if (lastSegment < 0) lastSegment = bestSeg

        val segLen = cumulative[bestSeg + 1] - cumulative[bestSeg]
        val along = cumulative[bestSeg] + bestT * segLen
        val remaining = (totalDistance - along).coerceAtLeast(0.0)

        val next = (1 until stepAlong.size).firstOrNull { stepAlong[it] > along + 3 }
        val arrived = remaining < 25 && bestD < 50

        return NavUpdate(
            snapped = Geo.interpolate(pts[bestSeg], pts[bestSeg + 1], bestT),
            distanceFromRoute = bestD,
            remainingDistance = remaining,
            remainingDuration = if (totalDistance > 0) route.duration * remaining / totalDistance else 0.0,
            nextStepIndex = next,
            nextStep = next?.let { route.steps[it] },
            distanceToNextStep = next?.let { stepAlong[it] - along } ?: remaining,
            offRoute = offRouteCount >= 3,
            arrived = arrived,
        )
    }

    /** Retorna la frase a dir en veu alta ara mateix, o null si no toca dir res. */
    fun announcement(u: NavUpdate): String? {
        val k = u.nextStepIndex ?: return null
        val step = u.nextStep ?: return null
        if (u.offRoute) return null
        val d = u.distanceToNextStep
        // Mira primer la fase més propera, perquè si entrem tard al tram no digui l'avís llunyà.
        for (phase in thresholds.indices.reversed()) {
            if (d <= thresholds[phase] && (k to phase) !in announced) {
                for (p in 0..phase) announced += k to p
                val isLast = phase == thresholds.lastIndex
                return Instructions.spoken(step, if (isLast) null else d)
            }
        }
        return null
    }
}
