package cat.rutes.navegador.nav

import cat.rutes.navegador.data.LatLon
import cat.rutes.navegador.data.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Test

class InstructionsTest {
    private fun step(type: String, mod: String? = null, name: String = "", exit: Int? = null) =
        RouteStep(type, mod, exit, name, LatLon(0.0, 0.0), 0.0, 0.0)

    @Test
    fun texts() {
        assertEquals("Gira a l'esquerra", Instructions.text(step("turn", "left")))
        assertEquals("Gira lleugerament a la dreta cap a Gran Via", Instructions.text(step("turn", "slight right", "Gran Via")))
        assertEquals("A la rotonda, agafa la segona sortida", Instructions.text(step("roundabout", "right", exit = 2)))
        assertEquals("A la bifurcació, mantén-te a l'esquerra", Instructions.text(step("fork", "slight left")))
        assertEquals("Fes mitja volta", Instructions.text(step("turn", "uturn")))
        assertEquals("Has arribat. La destinació és a la dreta", Instructions.text(step("arrive", "right", "X")))
        assertEquals("Comença per Carrer Major", Instructions.text(step("depart", "straight", "Carrer Major")))
    }

    @Test
    fun spoken() {
        assertEquals("D'aquí a 1,5 quilòmetres, gira a la dreta", Instructions.spoken(step("turn", "right"), 1520.0))
        assertEquals("D'aquí a 2 quilòmetres, continua recte", Instructions.spoken(step("continue"), 2010.0))
    }

    @Test
    fun formats() {
        assertEquals("350 m", Format.distance(347.0))
        assertEquals("1,2 km", Format.distance(1234.0))
        assertEquals("15 km", Format.distance(15_200.0))
        assertEquals("8 min", Format.duration(480.0))
        assertEquals("1 h 05 min", Format.duration(3900.0))
    }
}
