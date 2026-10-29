package cat.rutes.navegador.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cat.rutes.navegador.data.Geocoder
import cat.rutes.navegador.data.LatLon
import cat.rutes.navegador.data.Place
import cat.rutes.navegador.data.Profile
import cat.rutes.navegador.data.Route
import cat.rutes.navegador.data.Router
import cat.rutes.navegador.location.LocationProvider
import cat.rutes.navegador.nav.Instructions
import cat.rutes.navegador.nav.NavUpdate
import cat.rutes.navegador.nav.NavigationEngine
import cat.rutes.navegador.nav.Speaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class UiState(
    val hasPermission: Boolean = false,
    val location: Location? = null,
    val query: String = "",
    val suggestions: List<Place> = emptyList(),
    val searching: Boolean = false,
    val destination: Place? = null,
    val profile: Profile = Profile.CAR,
    val route: Route? = null,
    val loadingRoute: Boolean = false,
    val navigating: Boolean = false,
    val nav: NavUpdate? = null,
    val muted: Boolean = false,
    /** Si la càmera segueix la posició de l'usuari. */
    val follow: Boolean = true,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val geocoder = Geocoder()
    private val router = Router()
    private val locationProvider = LocationProvider(app)
    private val speaker = Speaker(app)

    private var engine: NavigationEngine? = null
    private var locationJob: Job? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var lastReroute = 0L

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (!granted) {
            _state.update { it.copy(message = "Cal el permís d'ubicació per navegar") }
            return
        }
        if (locationJob == null) {
            locationJob = viewModelScope.launch {
                locationProvider.updates().collect(::onLocation)
            }
        }
    }

    private fun onLocation(loc: Location) {
        _state.update { it.copy(location = loc) }
        if (_state.value.navigating) handleNavigation(loc)
    }

    // ---------- Cerca ----------

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.trim().length < 3) {
            _state.update { it.copy(suggestions = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // espera que l'usuari deixi d'escriure
            _state.update { it.copy(searching = true) }
            val near = _state.value.location?.toLatLon()
            runCatching { geocoder.search(q.trim(), near) }
                .onSuccess { list -> _state.update { it.copy(suggestions = list) } }
                .onFailure { e -> _state.update { it.copy(message = "Error de cerca: ${e.message}") } }
            _state.update { it.copy(searching = false) }
        }
    }

    fun selectPlace(place: Place) {
        searchJob?.cancel()
        _state.update {
            it.copy(destination = place, query = place.name, suggestions = emptyList(), searching = false)
        }
        computeRoute()
    }

    fun selectPoint(pos: LatLon) {
        if (_state.value.navigating) return
        val coords = String.format(Locale.US, "%.5f, %.5f", pos.lat, pos.lon)
        selectPlace(Place("Punt seleccionat", coords, pos))
    }

    fun setProfile(p: Profile) {
        if (p == _state.value.profile) return
        _state.update { it.copy(profile = p) }
        if (_state.value.destination != null) computeRoute()
    }

    fun clearDestination() {
        stopNavigation()
        routeJob?.cancel()
        _state.update {
            it.copy(destination = null, route = null, query = "", suggestions = emptyList(),
                loadingRoute = false, follow = true)
        }
    }

    // ---------- Rutes ----------

    private fun computeRoute(isReroute: Boolean = false) {
        val s = _state.value
        val dest = s.destination ?: return
        val from = s.location?.toLatLon()
        if (from == null) {
            _state.update { it.copy(message = "Esperant la ubicació GPS…") }
            return
        }
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _state.update { it.copy(loadingRoute = true) }
            runCatching { router.route(from, dest.pos, s.profile) }
                .onSuccess { route ->
                    if (_state.value.navigating) {
                        engine = NavigationEngine(route)
                        _state.update { it.copy(route = route) }
                        if (isReroute) speaker.speak("Ruta recalculada")
                    } else {
                        // En vista prèvia, la càmera s'ajusta a tota la ruta.
                        _state.update { it.copy(route = route, follow = false) }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(message = "No s'ha pogut calcular la ruta: ${e.message}") }
                }
            _state.update { it.copy(loadingRoute = false) }
        }
    }

    // ---------- Navegació ----------

    fun startNavigation() {
        val route = _state.value.route ?: return
        engine = NavigationEngine(route)
        _state.update { it.copy(navigating = true, follow = true) }
        route.steps.firstOrNull()?.let { speaker.speak(Instructions.text(it)) }
        _state.value.location?.let(::handleNavigation)
    }

    fun stopNavigation() {
        engine = null
        _state.update { it.copy(navigating = false, nav = null) }
    }

    private fun handleNavigation(loc: Location) {
        val e = engine ?: return
        val u = e.update(loc.toLatLon(), loc.accuracy)
        _state.update { it.copy(nav = u) }

        when {
            u.arrived -> {
                speaker.speak(e.route.steps.lastOrNull()?.let(Instructions::text) ?: "Has arribat")
                stopNavigation()
                _state.update { it.copy(message = "Has arribat a la destinació") }
            }
            u.offRoute -> {
                val now = System.currentTimeMillis()
                if (now - lastReroute > 10_000) {
                    lastReroute = now
                    speaker.speak("Recalculant la ruta")
                    computeRoute(isReroute = true)
                }
            }
            else -> e.announcement(u)?.let(speaker::speak)
        }
    }

    fun toggleMute() {
        val m = !_state.value.muted
        speaker.muted = m
        _state.update { it.copy(muted = m) }
    }

    fun setFollow(follow: Boolean) = _state.update { it.copy(follow = follow) }

    fun messageShown() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        speaker.shutdown()
    }
}

fun Location.toLatLon() = LatLon(latitude, longitude)
