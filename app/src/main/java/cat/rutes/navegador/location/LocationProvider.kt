package cat.rutes.navegador.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Ubicació amb el LocationManager estàndard d'Android (GPS del dispositiu).
 * No fa servir Google Play Services, així que funciona en Android sense Google.
 */
class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission") // el permís es comprova abans de cridar-ho
    fun updates(): Flow<Location> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        var lastGpsTime = 0L

        // Implementació explícita de tots els mètodes: en Android < 11 no tenen implementació per defecte.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val now = System.currentTimeMillis()
                if (location.provider == LocationManager.GPS_PROVIDER) {
                    lastGpsTime = now
                    trySend(location)
                } else if (now - lastGpsTime > 10_000) {
                    // Ubicació per xarxa només si fa estona que no hi ha senyal GPS.
                    trySend(location)
                }
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in lm.allProviders }

        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { trySend(it) }

        for (p in providers) {
            lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
        }
        awaitClose { lm.removeUpdates(listener) }
    }
}
