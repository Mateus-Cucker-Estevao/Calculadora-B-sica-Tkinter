package com.mateus.avaliadorcorridas.servico

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Fala textos em português usando o Text-to-Speech do próprio Android (funciona offline
 * se a voz em português estiver baixada). Abaixa o volume da música enquanto fala.
 */
class Falador(ctx: Context) : TextToSpeech.OnInitListener {

    companion object {
        /** Um pouco mais rápido que o normal, para o diagnóstico caber no tempo da oferta. */
        private const val VELOCIDADE = 1.1f
    }

    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val principal = Handler(Looper.getMainLooper())
    private val atributos = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val pedidoFoco = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(atributos)
        .build()

    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var pronto = false
    private var pendente: Pair<String, (() -> Unit)?>? = null
    private var contador = 0

    /** Fala atual e o que fazer quando ela terminar (ex.: esconder o banner). */
    private var idAtual: String? = null
    private var aoTerminarAtual: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.forLanguageTag("pt-BR")
        tts.setSpeechRate(VELOCIDADE)
        tts.setAudioAttributes(atributos)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = terminou(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = terminou(utteranceId)
        })
        pronto = true
        pendente?.let { (texto, aoTerminar) -> falar(texto, aoTerminar) }
        pendente = null
    }

    /** Estes avisos chegam de outra thread; voltamos para a principal antes de mexer na tela. */
    private fun terminou(id: String?) {
        principal.post {
            if (id != idAtual) return@post // uma fala antiga, já substituída por outra
            audio.abandonAudioFocusRequest(pedidoFoco)
            val acao = aoTerminarAtual
            idAtual = null
            aoTerminarAtual = null
            acao?.invoke()
        }
    }

    fun falar(texto: String, aoTerminar: (() -> Unit)? = null) {
        if (!pronto) {
            pendente = texto to aoTerminar
            return
        }
        val id = "aviso-${++contador}"
        idAtual = id
        aoTerminarAtual = aoTerminar
        audio.requestAudioFocus(pedidoFoco)
        if (tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) terminou(id)
    }

    fun parar() {
        tts.stop()
        audio.abandonAudioFocusRequest(pedidoFoco)
    }

    fun desligar() {
        parar()
        tts.shutdown()
    }
}
