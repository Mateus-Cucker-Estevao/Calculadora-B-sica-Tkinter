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
import com.mateus.avaliadorcorridas.regras.Indicador
import com.mateus.avaliadorcorridas.regras.Painel
import com.mateus.avaliadorcorridas.regras.Resultado

/**
 * Painel flutuante no topo da tela, no modelo do GigU:
 *
 *   ┌──────────── borda verde / amarela / vermelha ────────────┐
 *   │  ✅ R$ 19,59    11,4 km    24 min    ✅ Busca 2,9 km       │
 *   │ ─────────────────────────────────────────────────────── │
 *   │   R$/km   │    R$/h    │   R$/min   │    Nota            │
 *   │  ❌ 1,72  │  ⚠️ 49     │  ⚠️ 0,82   │  ✅ 4,87           │
 *   │  🚫 Destino bloqueado: Cocal do Sul   (só se bloqueado)   │
 *   └───────────────────────────────────────────────────────────┘
 *
 * A cor da BORDA é o resultado geral (a do pior critério).
 *
 * Quanto tempo fica na tela:
 *   - com voz: até a voz terminar de falar tudo (+1,5 s), respeitando o tempo mínimo;
 *   - sem voz: o tempo mínimo, mais 2 s para cada ponto do diagnóstico;
 *   - nunca mais que [MAXIMO_MS] (segurança, caso a voz falhe).
 *
 * Usa uma janela TYPE_ACCESSIBILITY_OVERLAY (não precisa da permissão "Sobrepor a outros
 * apps") e é "não tocável": seus toques passam direto para o Uber.
 */
class BannerSobreposto(private val servico: AccessibilityService) {

    companion object {
        private const val MAXIMO_MS = 30_000L
        private const val DEPOIS_DA_VOZ_MS = 1_500L
        private const val POR_PONTO_SEM_VOZ_MS = 2_000L

        private val VERDE = Color.rgb(46, 125, 50)
        private val AMARELO = Color.rgb(255, 214, 0)
        private val VERMELHO = Color.rgb(198, 40, 40)
        private val TEXTO = Color.rgb(33, 33, 33)
        private val TEXTO_CLARO = Color.rgb(117, 117, 117)
        private val DIVISORIA = Color.rgb(224, 224, 224)
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
        val painel = r.painel ?: return

        // Borda colorida (resultado geral) com o cartão branco dentro.
        val borda = LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = retangulo(corDe(r.cor), 18)
        }
        val cartao = LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            background = retangulo(Color.WHITE, 12)
        }
        borda.addView(cartao)

        cartao.addView(linhaDeCima(painel))
        cartao.addView(divisoriaHorizontal())
        cartao.addView(linhaDeMetricas(painel))
        painel.destinoBloqueado?.let { destino ->
            cartao.addView(divisoriaHorizontal())
            cartao.addView(texto("🚫 Destino bloqueado: $destino", 16f, VERMELHO, negrito = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(8))
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
            y = dp(36)
            horizontalMargin = 0.03f
        }

        try {
            wm.addView(borda, params)
            atual = borda
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

    // ---------------------------------------------------------------------
    // Montagem das linhas
    // ---------------------------------------------------------------------

    /** Valor · km total · minutos totais · busca. */
    private fun linhaDeCima(p: Painel) = LinearLayout(servico).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(10), dp(8), dp(10))
        addView(celulaDeCima("${marcador(p.valor.cor)} ${p.valor.texto}"), peso(1.3f))
        addView(celulaDeCima("🛣️ ${p.kmTotal}"), peso(1f))
        addView(celulaDeCima("🕒 ${p.minTotal}"), peso(1f))
        addView(celulaDeCima("${marcador(p.busca.cor)} Busca\n${p.busca.texto}"), peso(1.1f))
    }

    /** R$/km | R$/h | R$/min | Nota. */
    private fun linhaDeMetricas(p: Painel) = LinearLayout(servico).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, dp(10))
        val colunas = listOf("R$/km" to p.porKm, "R$/h" to p.porHora, "R$/min" to p.porMinuto, "Nota" to p.nota)
        colunas.forEachIndexed { i, (rotulo, indicador) ->
            if (i > 0) addView(divisoriaVertical())
            addView(coluna(rotulo, indicador), peso(1f))
        }
    }

    private fun celulaDeCima(conteudo: String) = texto(conteudo, 16f, TEXTO, negrito = true).apply {
        gravity = Gravity.CENTER
        maxLines = 2
    }

    private fun coluna(rotulo: String, indicador: Indicador) = LinearLayout(servico).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(texto(rotulo, 14f, TEXTO_CLARO).apply { gravity = Gravity.CENTER })
        addView(texto("${marcador(indicador.cor)} ${indicador.texto}", 18f, TEXTO, negrito = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        })
    }

    private fun texto(conteudo: String, tamanhoSp: Float, cor: Int, negrito: Boolean = false) =
        TextView(servico).apply {
            text = conteudo
            setTextColor(cor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanhoSp)
            if (negrito) typeface = Typeface.DEFAULT_BOLD
        }

    private fun divisoriaHorizontal() = View(servico).apply {
        setBackgroundColor(DIVISORIA)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun divisoriaVertical() = View(servico).apply {
        setBackgroundColor(DIVISORIA)
        layoutParams = LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT)
    }

    private fun peso(p: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, p)

    private fun retangulo(cor: Int, raioDp: Int) = GradientDrawable().apply {
        setColor(cor)
        cornerRadius = dp(raioDp).toFloat()
    }

    private fun corDe(cor: Cor): Int = when (cor) {
        Cor.VERDE -> VERDE
        Cor.AMARELO -> AMARELO
        Cor.VERMELHO -> VERMELHO
    }

    /** ✅ bom · ⚠️ no limite · ❌ ruim · (vazio) não deu para avaliar. */
    private fun marcador(cor: Cor?): String = when (cor) {
        Cor.VERDE -> "✅"
        Cor.AMARELO -> "⚠️"
        Cor.VERMELHO -> "❌"
        null -> ""
    }.trim()

    private fun dp(v: Int): Int = (v * servico.resources.displayMetrics.density).toInt()
}
