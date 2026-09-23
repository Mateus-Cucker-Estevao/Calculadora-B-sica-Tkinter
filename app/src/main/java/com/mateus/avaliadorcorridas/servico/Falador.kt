package com.mateus.avaliadorcorridas.servico

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Fala textos em português usando o Text-to-Speech do próprio Android (funciona offline
 * se a voz em português estiver baixada). Abaixa o volume da música enquanto fala.
 */
class Falador(ctx: Context) : TextToSpeech.OnInitListener {

    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val atributos = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val pedidoFoco = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(atributos)
        .build()

    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var pronto = false
    private var pendente: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.forLanguageTag("pt-BR")
        tts.setAudioAttributes(atributos)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { audio.abandonAudioFocusRequest(pedidoFoco) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { audio.abandonAudioFocusRequest(pedidoFoco) }
        })
        pronto = true
        pendente?.let { falar(it) }
        pendente = null
    }

    fun falar(texto: String) {
        if (!pronto) {
            pendente = texto
            return
        }
        audio.requestAudioFocus(pedidoFoco)
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "aviso")
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
