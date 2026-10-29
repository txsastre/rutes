package cat.rutes.navegador.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.rutes.navegador.data.LatLon
import cat.rutes.navegador.data.Profile
import cat.rutes.navegador.data.RouteStep
import cat.rutes.navegador.nav.Format
import cat.rutes.navegador.nav.Geo
import cat.rutes.navegador.nav.Instructions

@Composable
fun AppScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    // Pantalla sempre encesa mentre es navega.
    val view = LocalView.current
    DisposableEffect(state.navigating) {
        view.keepScreenOn = state.navigating
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize()) {
        NavMap(
            state = state,
            onLongClick = vm::selectPoint,
            onTrackingDismissed = { vm.setFollow(false) },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.navigating) {
            NavBanner(state, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp))
        } else {
            SearchPanel(state, vm, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (!state.follow && state.hasPermission) {
                FloatingActionButton(onClick = { vm.setFollow(true) }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Centra la meva posició")
                }
                Spacer(Modifier.size(12.dp))
            }
            when {
                state.navigating -> NavBottomBar(state, vm)
                state.destination != null -> RouteCard(state, vm)
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp)) {
            Snackbar(it)
        }
    }
}

@Composable
private fun NavMap(
    state: UiState,
    onLongClick: (LatLon) -> Unit,
    onTrackingDismissed: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val controller = remember { MapController(context) }
    val longClick by rememberUpdatedState(onLongClick)
    val dismissed by rememberUpdatedState(onTrackingDismissed)

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.mapView.onStart()
                Lifecycle.Event.ON_RESUME -> controller.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.mapView.onPause()
                Lifecycle.Event.ON_STOP -> controller.mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.onDestroy()
        }
    }

    AndroidView(factory = { controller.mapView }, modifier = modifier)

    SideEffect {
        controller.onLongClick = { longClick(it) }
        controller.onTrackingDismissed = { dismissed() }
        controller.render(
            route = state.route,
            destination = state.destination?.pos,
            navigating = state.navigating,
            follow = state.follow,
            hasPermission = state.hasPermission,
        )
    }
    LaunchedEffect(state.location) {
        state.location?.let(controller::updateLocation)
    }
}

@Composable
private fun SearchPanel(state: UiState, vm: MainViewModel, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(6.dp)) {
        TextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("On vols anar?") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                when {
                    state.searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.query.isNotEmpty() -> IconButton(onClick = vm::clearDestination) {
                        Icon(Icons.Filled.Close, contentDescription = "Esborra")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
        )
        if (state.suggestions.isNotEmpty()) {
            HorizontalDivider()
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(state.suggestions) { place ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.selectPlace(place) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(place.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (place.detail.isNotBlank()) {
                                Text(place.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        state.location?.let { loc ->
                            Text(
                                Format.distance(Geo.distance(loc.toLatLon(), place.pos)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        if (state.query.isEmpty() && state.destination == null) {
            Text(
                "Cerca un lloc o mantén premut el mapa per triar un destí",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun RouteCard(state: UiState, vm: MainViewModel) {
    val dest = state.destination ?: return
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dest.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (dest.detail.isNotBlank()) {
                        Text(dest.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = vm::clearDestination) { Icon(Icons.Filled.Close, contentDescription = "Tanca") }
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Profile.entries.forEach { p ->
                    FilterChip(
                        selected = state.profile == p,
                        onClick = { vm.setProfile(p) },
                        label = { Text(p.label) },
                        leadingIcon = { Icon(profileIcon(p), contentDescription = null, Modifier.size(18.dp)) },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val route = state.route
                Column(Modifier.weight(1f)) {
                    when {
                        state.loadingRoute -> CircularProgressIndicator(Modifier.size(24.dp))
                        route != null -> {
                            Text(
                                Format.duration(route.duration),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("${Format.distance(route.distance)} · arribada ${Format.arrivalTime(route.duration)}")
                        }
                        else -> Text("Sense ruta")
                    }
                }
                Button(onClick = vm::startNavigation, enabled = route != null && !state.loadingRoute) {
                    Icon(Icons.Filled.Navigation, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Inicia")
                }
            }
        }
    }
}

@Composable
private fun NavBanner(state: UiState, modifier: Modifier) {
    val u = state.nav
    val step = u?.nextStep
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B6E4F), contentColor = Color.White),
        elevation = CardDefaults.cardElevation(6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(maneuverIcon(step), contentDescription = null, Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                when {
                    u == null -> Text("Esperant senyal GPS…", style = MaterialTheme.typography.titleMedium)
                    u.offRoute -> Text("Recalculant la ruta…", style = MaterialTheme.typography.titleMedium)
                    else -> {
                        Text(
                            Format.distance(u.distanceToNextStep),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        step?.let {
                            Text(Instructions.text(it), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBottomBar(state: UiState, vm: MainViewModel) {
    val u = state.nav
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (u != null) {
                    Text(
                        Format.duration(u.remainingDuration),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("${Format.distance(u.remainingDistance)} · arribada ${Format.arrivalTime(u.remainingDuration)}")
                } else {
                    Text("Navegant…")
                }
            }
            IconButton(onClick = vm::toggleMute) {
                Icon(
                    if (state.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (state.muted) "Activa la veu" else "Silencia la veu",
                )
            }
            FilledTonalButton(onClick = vm::stopNavigation) { Text("Atura") }
        }
    }
}

private fun profileIcon(p: Profile): ImageVector = when (p) {
    Profile.CAR -> Icons.Filled.DirectionsCar
    Profile.BIKE -> Icons.Filled.DirectionsBike
    Profile.FOOT -> Icons.Filled.DirectionsWalk
}

private fun maneuverIcon(step: RouteStep?): ImageVector {
    if (step == null) return Icons.Filled.Navigation
    val mod = step.modifier
    return when (step.type) {
        "arrive" -> Icons.Filled.Flag
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> Icons.Filled.RoundaboutRight
        "fork" -> if (mod?.contains("left") == true) Icons.Filled.ForkLeft else Icons.Filled.ForkRight
        "merge" -> Icons.Filled.Merge
        "on ramp", "off ramp" -> if (mod?.contains("left") == true) Icons.Filled.RampLeft else Icons.Filled.RampRight
        else -> when (mod) {
            "right" -> Icons.Filled.TurnRight
            "left" -> Icons.Filled.TurnLeft
            "slight right" -> Icons.Filled.TurnSlightRight
            "slight left" -> Icons.Filled.TurnSlightLeft
            "sharp right" -> Icons.Filled.TurnSharpRight
            "sharp left" -> Icons.Filled.TurnSharpLeft
            "uturn" -> Icons.Filled.UTurnLeft
            else -> Icons.Filled.Straight
        }
    }
}
