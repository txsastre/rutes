package cat.rutes.navegador.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import cat.rutes.navegador.data.LatLon
import cat.rutes.navegador.data.Route
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Embolcalla el MapView de MapLibre: dibuixa la ruta i la destinació,
 * mostra la posició i mou la càmera. Rep l'estat desitjat i només aplica els canvis.
 */
class MapController(context: Context) {
    val mapView = MapView(context)

    var onLongClick: (LatLon) -> Unit = {}
    var onTrackingDismissed: () -> Unit = {}

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var locationActive = false
    private var centeredOnce = false

    // Estat desitjat (s'aplica quan el mapa està llest).
    private var route: Route? = null
    private var destination: LatLon? = null
    private var navigating = false
    private var follow = true
    private var hasPermission = false

    // Estat aplicat
    private var shownRoute: Route? = null
    private var shownDestination: LatLon? = null
    private var shownMode: Pair<Boolean, Boolean>? = null

    init {
        mapView.onCreate(null)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.isAttributionEnabled = true
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(41.3874, 2.1686)) // Barcelona, fins que hi hagi GPS
                .zoom(12.0)
                .build()
            m.addOnMapLongClickListener { p ->
                onLongClick(LatLon(p.latitude, p.longitude))
                true
            }
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                style = s
                addLayers(s)
                apply()
            }
        }
    }

    fun render(route: Route?, destination: LatLon?, navigating: Boolean, follow: Boolean, hasPermission: Boolean) {
        this.route = route
        this.destination = destination
        this.navigating = navigating
        this.follow = follow
        this.hasPermission = hasPermission
        apply()
    }

    fun updateLocation(loc: Location) {
        val m = map ?: return
        if (!locationActive) return
        m.locationComponent.forceLocationUpdate(loc)
        if (!centeredOnce && route == null) {
            centeredOnce = true
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0))
        }
    }

    private fun addLayers(s: Style) {
        s.addSource(GeoJsonSource(ROUTE_SRC))
        s.addLayer(
            LineLayer("route-casing", ROUTE_SRC).withProperties(
                PropertyFactory.lineColor("#0B47A1"),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        s.addLayer(
            LineLayer("route-line", ROUTE_SRC).withProperties(
                PropertyFactory.lineColor("#1A73E8"),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        s.addSource(GeoJsonSource(DEST_SRC))
        s.addLayer(
            CircleLayer("dest", DEST_SRC).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor("#D93025"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation(m: MapLibreMap, s: Style) {
        val lc = m.locationComponent
        // Les posicions les hi passem nosaltres (forceLocationUpdate): cap motor de Google.
        lc.activateLocationComponent(
            LocationComponentActivationOptions.builder(mapView.context, s)
                .useDefaultLocationEngine(false)
                .build()
        )
        lc.isLocationComponentEnabled = true
        lc.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() = onTrackingDismissed()
            override fun onCameraTrackingChanged(currentMode: Int) {}
        })
        locationActive = true
        shownMode = null // torna a aplicar el mode de càmera ara que hi ha ubicació
    }

    private fun apply() {
        val m = map ?: return
        val s = style ?: return
        if (hasPermission && !locationActive) enableLocation(m, s)

        if (destination != shownDestination) {
            shownDestination = destination
            val fc = destination?.let {
                FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)))
            } ?: FeatureCollection.fromFeatures(emptyList())
            s.getSourceAs<GeoJsonSource>(DEST_SRC)?.setGeoJson(fc)
        }

        val modeChanged = shownMode != (navigating to follow)
        if (modeChanged) {
            shownMode = navigating to follow
            applyCameraMode(m)
        }

        if (route !== shownRoute) {
            shownRoute = route
            val r = route
            val fc = if (r == null) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeature(
                Feature.fromGeometry(LineString.fromLngLats(r.points.map { Point.fromLngLat(it.lon, it.lat) }))
            )
            s.getSourceAs<GeoJsonSource>(ROUTE_SRC)?.setGeoJson(fc)
            if (r != null && !navigating) fitRoute(m, r)
        }
    }

    private fun applyCameraMode(m: MapLibreMap) {
        if (!locationActive) return
        val lc = m.locationComponent
        when {
            navigating && follow -> {
                lc.cameraMode = CameraMode.TRACKING_GPS
                lc.renderMode = RenderMode.GPS
                lc.zoomWhileTracking(17.0)
                lc.tiltWhileTracking(50.0)
            }
            follow -> {
                lc.cameraMode = CameraMode.TRACKING
                lc.renderMode = RenderMode.NORMAL
                lc.tiltWhileTracking(0.0)
            }
            else -> {
                lc.cameraMode = CameraMode.NONE
                lc.renderMode = if (navigating) RenderMode.GPS else RenderMode.NORMAL
            }
        }
    }

    private fun fitRoute(m: MapLibreMap, r: Route) {
        val bounds = LatLngBounds.Builder()
            .includes(r.points.map { LatLng(it.lat, it.lon) })
            .build()
        val pad = (mapView.resources.displayMetrics.density * 60).toInt()
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad, pad * 3, pad, pad * 5))
    }

    fun onDestroy() {
        mapView.onDestroy()
    }

    companion object {
        /** Estil vectorial lliure d'OpenFreeMap (dades d'OpenStreetMap, sense clau). */
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val ROUTE_SRC = "route-src"
        private const val DEST_SRC = "dest-src"
    }
}
