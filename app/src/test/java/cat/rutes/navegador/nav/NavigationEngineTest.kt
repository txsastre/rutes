package cat.rutes.navegador.nav

import cat.rutes.navegador.data.LatLon
import cat.rutes.navegador.data.Profile
import cat.rutes.navegador.data.Route
import cat.rutes.navegador.data.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationEngineTest {
    // Ruta en "L": 1 km cap al nord i després 500 m cap a l'est (gir a la dreta).
    private val a = LatLon(41.0, 2.0)
    private val corner = LatLon(41.0 + 1000.0 / 111_195, 2.0)
    private val end = LatLon(corner.lat, 2.0 + 500.0 / (111_195 * Math.cos(Math.toRadians(corner.lat))))

    private val route = Route(
        points = listOf(a, corner, end),
        distance = 1500.0,
        duration = 150.0,
        steps = listOf(
            RouteStep("depart", null, null, "Carrer Major", a, 1000.0, 100.0),
            RouteStep("turn", "right", null, "Carrer Nou", corner, 500.0, 50.0),
            RouteStep("arrive", null, null, "", end, 0.0, 0.0),
        ),
        profile = Profile.CAR,
    )

    private fun along(meters: Double) = LatLon(a.lat + meters / 111_195, a.lon)

    @Test
    fun totalDistanceIsComputed() {
        assertEquals(1500.0, NavigationEngine(route).totalDistance, 5.0)
    }

    @Test
    fun findsNextManeuverAndDistances() {
        val u = NavigationEngine(route).update(along(300.0))
        assertEquals(1, u.nextStepIndex)
        assertEquals(700.0, u.distanceToNextStep, 5.0)
        assertEquals(1200.0, u.remainingDistance, 5.0)
        assertEquals(120.0, u.remainingDuration, 1.0)
        assertFalse(u.offRoute)
        assertFalse(u.arrived)
    }

    @Test
    fun afterTurnNextIsArrival() {
        val e = NavigationEngine(route)
        e.update(along(900.0))
        val past = LatLon(corner.lat, (corner.lon + end.lon) / 2)
        val u = e.update(past)
        assertEquals(2, u.nextStepIndex)
        assertEquals(250.0, u.distanceToNextStep, 5.0)
    }

    @Test
    fun detectsArrival() {
        val e = NavigationEngine(route)
        e.update(along(900.0))
        assertTrue(e.update(end).arrived)
    }

    @Test
    fun offRouteNeedsThreeConsecutiveFixes() {
        val e = NavigationEngine(route)
        e.update(along(100.0))
        val away = LatLon(along(200.0).lat, a.lon + 0.002) // ~170 m a l'est
        assertFalse(e.update(away).offRoute)
        assertFalse(e.update(away).offRoute)
        assertTrue(e.update(away).offRoute)
        assertFalse(e.update(along(250.0)).offRoute) // torna a la ruta
    }

    @Test
    fun announcesEachPhaseOnce() {
        val e = NavigationEngine(route)
        assertNull(e.announcement(e.update(along(100.0)))) // 900 m: encara massa lluny
        val far = e.announcement(e.update(along(250.0)))   // 750 m
        assertNotNull(far)
        assertTrue(far!!, far.startsWith("D'aquí a 750 metres, gira a la dreta"))
        assertNull(e.announcement(e.update(along(260.0)))) // no repeteix
        val near = e.announcement(e.update(along(820.0)))  // 180 m
        assertEquals("D'aquí a 180 metres, gira a la dreta cap a Carrer Nou", near)
        assertEquals("Gira a la dreta cap a Carrer Nou", e.announcement(e.update(along(970.0))))
    }

    @Test
    fun lateEntrySkipsFarAnnouncement() {
        val e = NavigationEngine(route)
        // Primera posició ja a 30 m del gir: només diu l'avís final.
        assertEquals("Gira a la dreta cap a Carrer Nou", e.announcement(e.update(along(970.0))))
        assertNull(e.announcement(e.update(along(975.0))))
    }
}
