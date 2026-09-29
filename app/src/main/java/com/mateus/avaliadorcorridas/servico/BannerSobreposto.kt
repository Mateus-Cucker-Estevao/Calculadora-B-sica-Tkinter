package com.mateus.avaliadorcorridas.servico

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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
 * Aviso flutuante no topo da tela, com 6 informações em 2 linhas:
 *
 *   ┌──────────── borda verde / amarela / vermelha ────────────┐
 *   │   ✅ Valor      │    ❌ R$/km     │     ✅ Nota           │
 *   │   R$ 19,59      │      1,72       │      4,87             │
 *   │ ✅ lucro 13,07   │                 │                       │
 *   │ ─────────────────────────────────────────────────────── │
 *   │ ✅ Até passageiro │ Distância total │ Tempo estimado       │
 *   │     2,9 km        │    11,4 km      │    24 min            │
 *   └───────────────────────────────────────────────────────────┘
 *
 * A cor da BORDA é o resultado geral (a do pior critério).
 *
 * Quanto tempo fica na tela ([Duracao]):
 *   - oferta real: enquanto a oferta estiver na tela; some [SEM_OFERTA_MS] depois que ela sair
 *     (aceita, recusada ou expirada);
 *   - botão "Testar": um tempo fixo.
 *
 * Precisa da permissão "Sobrepor a outros apps" (TYPE_APPLICATION_OVERLAY). O aviso é
 * "não tocável": seus toques passam direto para o Uber, então ele nunca aperta nada.
 */
class BannerSobreposto(private val servico: Context) {

    sealed class Duracao {
        /** Fica enquanto [ultimaVezVista] (elapsedRealtime da última leitura com oferta) for recente. */
        class EnquantoOferta(val ultimaVezVista: () -> Long) : Duracao()
        class Fixa(val segundos: Int) : Duracao()
    }

    companion object {
        /** Some este tempo depois da última vez que a oferta foi vista na tela. */
        private const val SEM_OFERTA_MS = 3_000L
        private const val VERIFICAR_A_CADA_MS = 500L
        /** Segurança: nunca fica mais que isso. */
        private const val MAXIMO_MS = 90_000L

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
    private var verificador: Runnable? = null
    private val esconder = Runnable { remover() }

    fun mostrar(r: Resultado, duracao: Duracao) {
        remover()
        val painel = r.painel ?: return
        if (!Settings.canDrawOverlays(servico)) {
            Log.w("BannerSobreposto", "Sem permissão para sobrepor a outros apps")
            return
        }

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
        cartao.addView(linha(celulasDeCima(painel)))
        cartao.addView(divisoriaHorizontal())
        cartao.addView(linha(celulasDeBaixo(painel)))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            mostradoEm = SystemClock.elapsedRealtime()
            agendarSaida(duracao)
        } catch (e: Exception) {
            Log.e("BannerSobreposto", "Não foi possível mostrar o aviso", e)
        }
    }

    private fun agendarSaida(duracao: Duracao) {
        when (duracao) {
            is Duracao.Fixa -> handler.postDelayed(esconder, duracao.segundos.coerceIn(1, 30) * 1000L)
            is Duracao.EnquantoOferta -> {
                val v = object : Runnable {
                    override fun run() {
                        val agora = SystemClock.elapsedRealtime()
                        val ofertaSumiu = agora - duracao.ultimaVezVista() > SEM_OFERTA_MS
                        if (ofertaSumiu || agora - mostradoEm > MAXIMO_MS) remover()
                        else handler.postDelayed(this, VERIFICAR_A_CADA_MS)
                    }
                }
                verificador = v
                handler.postDelayed(v, VERIFICAR_A_CADA_MS)
            }
        }
    }

    fun remover() {
        handler.removeCallbacks(esconder)
        verificador?.let { handler.removeCallbacks(it) }
        verificador = null
        atual?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        atual = null
    }

    // ---------------------------------------------------------------------
    // Montagem
    // ---------------------------------------------------------------------

    /** Valor · R$/km · Nota (os três com ✅/⚠️/❌ conforme seus critérios). */
    private fun celulasDeCima(p: Painel) = listOf(
        celula("Valor", p.valor.cor, p.valor.texto, subtitulo = p.lucro),
        celula("R$/km", p.porKm.cor, p.porKm.texto),
        celula("Nota", p.nota.cor, p.nota.texto),
    )

    /** Até o passageiro · Distância total · Tempo estimado. */
    private fun celulasDeBaixo(p: Painel) = listOf(
        celula("Até passageiro", p.busca.cor, p.busca.texto),
        celula("Distância total", null, p.kmTotal),
        celula("Tempo estimado", null, p.minTotal),
    )

    private fun linha(celulas: List<View>) = LinearLayout(servico).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, dp(8))
        celulas.forEachIndexed { i, c ->
            if (i > 0) addView(divisoriaVertical())
            addView(c, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Rótulo pequeno (com o ✅/⚠️/❌ na frente) e o valor grande embaixo. */
    private fun celula(rotulo: String, cor: Cor?, valor: String, subtitulo: Indicador? = null) = LinearLayout(servico).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val marca = marcador(cor)
        addView(texto(if (marca.isEmpty()) rotulo else "$marca $rotulo", 13f, TEXTO_CLARO).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        })
        addView(texto(valor, 21f, TEXTO, negrito = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        })
        // Lucro estimado (valor − combustível), embaixo do valor.
        subtitulo?.takeIf { it.texto != "–" }?.let { lucro ->
            val m = marcador(lucro.cor)
            addView(texto("${if (m.isEmpty()) "" else "$m "}lucro ${lucro.texto}", 12f, TEXTO_CLARO).apply {
                gravity = Gravity.CENTER
                maxLines = 1
            })
        }
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

    private fun retangulo(cor: Int, raioDp: Int) = GradientDrawable().apply {
        setColor(cor)
        cornerRadius = dp(raioDp).toFloat()
    }

    private fun corDe(cor: Cor): Int = when (cor) {
        Cor.VERDE -> VERDE
        Cor.AMARELO -> AMARELO
        Cor.VERMELHO -> VERMELHO
    }

    /** ✅ bom · ⚠️ no limite · ❌ ruim · (vazio) sem critério ou não lido. */
    private fun marcador(cor: Cor?): String = when (cor) {
        Cor.VERDE -> "✅"
        Cor.AMARELO -> "⚠️"
        Cor.VERMELHO -> "❌"
        null -> ""
    }

    private fun dp(v: Int): Int = (v * servico.resources.displayMetrics.density).toInt()
}
