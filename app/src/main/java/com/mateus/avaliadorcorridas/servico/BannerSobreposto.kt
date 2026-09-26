package com.mateus.avaliadorcorridas.servico

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.mateus.avaliadorcorridas.regras.Cor
import com.mateus.avaliadorcorridas.regras.Resultado

/**
 * Banner colorido no topo da tela com o diagnóstico da corrida.
 *
 * Quanto tempo fica na tela:
 *   - com voz: até a voz terminar de falar tudo (+1,5 s), respeitando o tempo mínimo;
 *   - sem voz: o tempo mínimo, mais 2 s para cada ponto do diagnóstico.
 *   - nunca mais que [MAXIMO_MS] (segurança, caso a voz falhe).
 *
 * Usa uma janela do tipo TYPE_ACCESSIBILITY_OVERLAY, que o serviço de acessibilidade pode
 * desenhar SEM precisar da permissão "Sobrepor a outros apps".
 * O banner é "não tocável" (FLAG_NOT_TOUCHABLE): seus toques passam direto para o Uber,
 * então ele nunca atrapalha nem aperta nada.
 */
class BannerSobreposto(private val servico: AccessibilityService) {

    companion object {
        private const val MAXIMO_MS = 30_000L
        private const val DEPOIS_DA_VOZ_MS = 1_500L
        private const val POR_PONTO_SEM_VOZ_MS = 2_000L
    }

    private val wm = servico.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var atual: View? = null
    private var mostradoEm = 0L
    private var minimoMs = 0L
    private val esconder = Runnable { remover() }

    /** [aguardarVoz] = true: fica até alguém chamar [vozTerminou] (ou até o máximo). */
    fun mostrar(r: Resultado, segundosMinimos: Int, aguardarVoz: Boolean) {
        remover()

        val (fundo, corTexto) = coresDe(r.cor)
        val caixa = LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(fundo)
                cornerRadius = dp(16).toFloat()
            }
            addView(TextView(servico).apply {
                text = r.titulo
                setTextColor(corTexto)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            r.pontos.forEach { p ->
                addView(TextView(servico).apply {
                    text = "${marcador(p.cor)}  ${p.texto}"
                    setTextColor(corTexto)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                })
            }
            addView(TextView(servico).apply {
                text = r.detalhes
                setTextColor(corTexto)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(40)
            horizontalMargin = 0.03f
        }

        try {
            wm.addView(caixa, params)
            atual = caixa
            mostradoEm = SystemClock.uptimeMillis()
            minimoMs = segundosMinimos.coerceIn(1, 30) * 1000L
            val duracao = if (aguardarVoz) MAXIMO_MS else minimoMs + r.pontos.size * POR_PONTO_SEM_VOZ_MS
            handler.postDelayed(esconder, duracao.coerceAtMost(MAXIMO_MS))
        } catch (e: Exception) {
            Log.e("BannerSobreposto", "Não foi possível mostrar o banner", e)
        }
    }

    /** A voz acabou: esconde daqui a pouco, mas nunca antes do tempo mínimo. */
    fun vozTerminou() {
        if (atual == null) return
        val passou = SystemClock.uptimeMillis() - mostradoEm
        val resta = maxOf(minimoMs - passou, 0L) + DEPOIS_DA_VOZ_MS
        handler.removeCallbacks(esconder)
        handler.postDelayed(esconder, resta)
    }

    fun remover() {
        handler.removeCallbacks(esconder)
        atual?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        atual = null
    }

    private fun coresDe(cor: Cor): Pair<Int, Int> = when (cor) {
        Cor.VERDE -> Color.rgb(46, 125, 50) to Color.WHITE
        Cor.AMARELO -> Color.rgb(255, 193, 7) to Color.BLACK
        Cor.VERMELHO -> Color.rgb(198, 40, 40) to Color.WHITE
    }

    private fun marcador(cor: Cor): String = when (cor) {
        Cor.VERDE -> "✅"
        Cor.AMARELO -> "⚠️"
        Cor.VERMELHO -> "❌"
    }

    private fun dp(v: Int): Int = (v * servico.resources.displayMetrics.density).toInt()
}
