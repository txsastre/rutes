package cat.rutes.navegador.nav

import cat.rutes.navegador.data.RouteStep

/** Converteix les maniobres d'OSRM en text en català. */
object Instructions {

    fun text(step: RouteStep): String {
        val mod = step.modifier
        val base = when (step.type) {
            "depart" -> return if (step.name.isNotBlank()) "Comença per ${step.name}" else "Comença la ruta"
            "arrive" -> return when {
                mod?.contains("right") == true -> "Has arribat. La destinació és a la dreta"
                mod?.contains("left") == true -> "Has arribat. La destinació és a l'esquerra"
                else -> "Has arribat a la destinació"
            }
            "roundabout", "rotary" -> step.exit?.let { "A la rotonda, agafa la ${ordinal(it)} sortida" }
                ?: "Entra a la rotonda"
            "roundabout turn" -> "A la rotonda, gira ${direction(mod)}"
            "exit roundabout", "exit rotary" -> "Surt de la rotonda"
            "fork" -> "A la bifurcació, mantén-te ${side(mod)}"
            "merge" -> "Incorpora't"
            "on ramp" -> "Agafa l'accés ${side(mod)}"
            "off ramp" -> "Agafa la sortida ${side(mod)}"
            "end of road" -> "Al final de la via, gira ${direction(mod)}"
            "continue", "new name" -> if (mod == null || mod == "straight") "Continua recte" else "Continua ${direction(mod)}"
            else -> when (mod) { // "turn" i altres
                null, "straight" -> "Continua recte"
                "uturn" -> "Fes mitja volta"
                else -> "Gira ${direction(mod)}"
            }
        }
        return if (step.name.isNotBlank()) "$base cap a ${step.name}" else base
    }

    /** Frase per la veu, amb la distància si cal: "D'aquí a 300 metres, gira a la dreta". */
    fun spoken(step: RouteStep, distance: Double?): String {
        val t = text(step)
        return if (distance == null) t
        else "D'aquí a ${Format.spokenDistance(distance)}, ${t.replaceFirstChar { it.lowercase() }}"
    }

    private fun direction(mod: String?) = when (mod) {
        "right" -> "a la dreta"
        "left" -> "a l'esquerra"
        "slight right" -> "lleugerament a la dreta"
        "slight left" -> "lleugerament a l'esquerra"
        "sharp right" -> "tancat a la dreta"
        "sharp left" -> "tancat a l'esquerra"
        "uturn" -> "en sentit contrari"
        else -> "recte"
    }

    private fun side(mod: String?) = when {
        mod?.contains("right") == true -> "a la dreta"
        mod?.contains("left") == true -> "a l'esquerra"
        else -> "recte"
    }

    private fun ordinal(n: Int) = when (n) {
        1 -> "primera"; 2 -> "segona"; 3 -> "tercera"; 4 -> "quarta"
        5 -> "cinquena"; 6 -> "sisena"; 7 -> "setena"; 8 -> "vuitena"
        else -> "${n}a"
    }
}
