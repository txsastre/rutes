package cat.rutes.navegador.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Serveis oberts basats en OpenStreetMap (sense Google ni claus d'API):
 *  - Cerca: Photon (photon.komoot.io)
 *  - Rutes: OSRM a routing.openstreetmap.de
 * Són servidors públics gratuïts amb límits d'ús raonable; per a ús intensiu
 * convé muntar-ne un de propi i canviar les URL d'aquí.
 */
private object Http {
    private const val USER_AGENT = "Rutes/0.1 (Android; https://openstreetmap.org)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    suspend fun getJson(url: HttpUrl): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            JSONObject(body)
        }
    }
}

class Geocoder(private val baseUrl: String = "https://photon.komoot.io/api/") {

    suspend fun search(query: String, near: LatLon?): List<Place> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "8")
            .apply {
                if (near != null) {
                    addQueryParameter("lat", near.lat.toString())
                    addQueryParameter("lon", near.lon.toString())
                }
            }
            .build()
        return parsePhoton(Http.getJson(url))
    }

    companion object {
        fun parsePhoton(json: JSONObject): List<Place> {
            val features = json.optJSONArray("features") ?: return emptyList()
            return (0 until features.length()).mapNotNull { i ->
                val f = features.getJSONObject(i)
                val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                val p = f.optJSONObject("properties") ?: JSONObject()
                val street = listOf(p.optString("street"), p.optString("housenumber"))
                    .filter { it.isNotBlank() }.joinToString(" ")
                val name = p.optString("name").ifBlank { street }.ifBlank { "Sense nom" }
                val detail = listOf(
                    if (street != name) street else "",
                    p.optString("city"),
                    p.optString("state"),
                    p.optString("country"),
                ).filter { it.isNotBlank() }.distinct().joinToString(", ")
                Place(name, detail, LatLon(coords.getDouble(1), coords.getDouble(0)))
            }
        }
    }
}

class Router(private val baseUrl: String = "https://routing.openstreetmap.de") {

    suspend fun route(from: LatLon, to: LatLon, profile: Profile): Route {
        val url = ("$baseUrl/${profile.osrmPath}/route/v1/driving/" +
                "${from.lon},${from.lat};${to.lon},${to.lat}").toHttpUrl().newBuilder()
            .addQueryParameter("overview", "full")
            .addQueryParameter("geometries", "geojson")
            .addQueryParameter("steps", "true")
            .build()
        return parseOsrm(Http.getJson(url), profile)
    }

    companion object {
        fun parseOsrm(json: JSONObject, profile: Profile): Route {
            if (json.optString("code") != "Ok") {
                throw IOException(json.optString("message").ifBlank { "No s'ha trobat cap ruta" })
            }
            val r = json.getJSONArray("routes").getJSONObject(0)
            val points = r.getJSONObject("geometry").getJSONArray("coordinates").toLatLons()
            val steps = mutableListOf<RouteStep>()
            val legs = r.getJSONArray("legs")
            for (l in 0 until legs.length()) {
                val legSteps = legs.getJSONObject(l).getJSONArray("steps")
                for (s in 0 until legSteps.length()) {
                    val st = legSteps.getJSONObject(s)
                    val m = st.getJSONObject("maneuver")
                    val loc = m.getJSONArray("location")
                    steps += RouteStep(
                        type = m.optString("type"),
                        modifier = m.optString("modifier").ifBlank { null },
                        exit = if (m.has("exit")) m.getInt("exit") else null,
                        name = st.optString("name"),
                        location = LatLon(loc.getDouble(1), loc.getDouble(0)),
                        distance = st.optDouble("distance", 0.0),
                        duration = st.optDouble("duration", 0.0),
                    )
                }
            }
            return Route(points, r.getDouble("distance"), r.getDouble("duration"), steps, profile)
        }

        private fun JSONArray.toLatLons(): List<LatLon> = (0 until length()).map { i ->
            val c = getJSONArray(i)
            LatLon(c.getDouble(1), c.getDouble(0))
        }
    }
}
