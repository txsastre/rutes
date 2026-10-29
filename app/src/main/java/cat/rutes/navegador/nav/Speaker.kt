package cat.rutes.navegador.nav

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Veu de les indicacions amb el motor TTS del sistema.
 * En mòbils sense Google, instal·la un motor lliure com RHVoice o eSpeak NG (F-Droid).
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null
    var muted = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val langs = listOf("ca-ES", "es-ES").map(Locale::forLanguageTag) + Locale.getDefault()
        langs.firstOrNull { tts.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
        ready = true
        pending?.let { speak(it) }
        pending = null
    }

    fun speak(text: String) {
        if (muted) return
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
