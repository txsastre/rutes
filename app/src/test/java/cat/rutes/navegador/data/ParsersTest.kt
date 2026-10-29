package cat.rutes.navegador.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ParsersTest {
    @Test
    fun parsesOsrm() {
        val json = JSONObject("""
            {"code":"Ok","routes":[{"distance":1200.5,"duration":130.2,
              "geometry":{"type":"LineString","coordinates":[[2.1,41.3],[2.11,41.31]]},
              "legs":[{"steps":[
                {"distance":1200.5,"duration":130.2,"name":"Carrer Major",
                 "maneuver":{"type":"depart","location":[2.1,41.3],"bearing_after":0}},
                {"distance":0,"duration":0,"name":"",
                 "maneuver":{"type":"roundabout","modifier":"right","exit":3,"location":[2.11,41.31]}}
              ]}]}]}
        """.trimIndent())
        val r = Router.parseOsrm(json, Profile.CAR)
        assertEquals(2, r.points.size)
        assertEquals(41.3, r.points[0].lat, 1e-9)
        assertEquals(2.1, r.points[0].lon, 1e-9)
        assertEquals(2, r.steps.size)
        assertEquals(null, r.steps[0].modifier)
        assertEquals(3, r.steps[1].exit)
    }

    @Test(expected = java.io.IOException::class)
    fun osrmErrorThrows() {
        Router.parseOsrm(JSONObject("""{"code":"NoRoute","message":"Impossible"}"""), Profile.FOOT)
    }

    @Test
    fun parsesPhoton() {
        val json = JSONObject("""
            {"features":[{"geometry":{"coordinates":[2.17,41.38]},
              "properties":{"name":"Sagrada Família","city":"Barcelona","country":"Espanya"}},
             {"geometry":{"coordinates":[2.0,41.0]},
              "properties":{"street":"Carrer Nou","housenumber":"5","city":"Reus"}}]}
        """.trimIndent())
        val p = Geocoder.parsePhoton(json)
        assertEquals("Sagrada Família", p[0].name)
        assertEquals("Barcelona, Espanya", p[0].detail)
        assertEquals("Carrer Nou 5", p[1].name)
        assertEquals("Reus", p[1].detail)
        assertEquals(41.38, p[0].pos.lat, 1e-9)
    }
}
