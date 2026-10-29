package cat.rutes.navegador.data

/** Coordenada geogràfica en graus. */
data class LatLon(val lat: Double, val lon: Double)

/** Un lloc trobat a la cerca o seleccionat al mapa. */
data class Place(val name: String, val detail: String, val pos: LatLon)

/** Mitjà de transport. `osrmPath` és el perfil del servidor routing.openstreetmap.de. */
enum class Profile(val label: String, val osrmPath: String) {
    CAR("Cotxe", "routed-car"),
    BIKE("Bici", "routed-bike"),
    FOOT("A peu", "routed-foot"),
}

/** Una maniobra de la ruta (format OSRM). */
data class RouteStep(
    val type: String,
    val modifier: String?,
    val exit: Int?,
    val name: String,
    val location: LatLon,
    val distance: Double,
    val duration: Double,
)

data class Route(
    val points: List<LatLon>,
    val distance: Double,
    val duration: Double,
    val steps: List<RouteStep>,
    val profile: Profile,
)
