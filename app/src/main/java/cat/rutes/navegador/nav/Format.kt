package cat.rutes.navegador.nav

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    private val CA = Locale.forLanguageTag("ca-ES")

    /** "350 m", "1,2 km", "15 km" */
    fun distance(meters: Double): String = when {
        meters < 1000 -> "${roundMeters(meters)} m"
        meters < 10_000 -> String.format(CA, "%.1f km", meters / 1000)
        else -> "${(meters / 1000).roundToInt()} km"
    }

    /** "8 min", "1 h 05 min" */
    fun duration(seconds: Double): String {
        val totalMin = (seconds / 60).roundToInt().coerceAtLeast(1)
        return if (totalMin < 60) "$totalMin min"
        else String.format(CA, "%d h %02d min", totalMin / 60, totalMin % 60)
    }

    /** Hora estimada d'arribada, "17:42". */
    fun arrivalTime(seconds: Double, now: LocalTime = LocalTime.now()): String =
        now.plusSeconds(seconds.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))

    /** Distància per dir en veu alta: "300 metres", "1,5 quilòmetres". */
    fun spokenDistance(meters: Double): String = when {
        meters < 1000 -> "${roundMeters(meters)} metres"
        else -> {
            val km = (meters / 100).roundToInt() / 10.0
            if (km % 1.0 == 0.0) "${km.toInt()} quilòmetre${if (km.toInt() == 1) "" else "s"}"
            else String.format(CA, "%.1f quilòmetres", km)
        }
    }

    /** Arrodoneix a 10 m (o a 50 m per sobre de 300 m). */
    private fun roundMeters(m: Double): Int {
        val step = if (m > 300) 50 else 10
        return ((m / step).roundToInt() * step).coerceAtLeast(10)
    }
}
