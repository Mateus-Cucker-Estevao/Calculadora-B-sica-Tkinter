package com.mateus.avaliadorcorridas.servico

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
 * Banner colorido no topo da tela, por alguns segundos.
 *
 * Usa uma janela do tipo TYPE_ACCESSIBILITY_OVERLAY, que o serviço de acessibilidade pode
 * desenhar SEM precisar da permissão "Sobrepor a outros apps".
 * O banner é "não tocável" (FLAG_NOT_TOUCHABLE): seus toques passam direto para o Uber,
 * então ele nunca atrapalha nem aperta nada.
 */
class BannerSobreposto(private val servico: AccessibilityService) {

    private val wm = servico.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var atual: View? = null
    private val esconder = Runnable { remover() }

    fun mostrar(r: Resultado, segundos: Int) {
        remover()

        val (fundo, corTexto) = when (r.cor) {
            Cor.VERDE -> Color.rgb(46, 125, 50) to Color.WHITE
            Cor.AMARELO -> Color.rgb(255, 193, 7) to Color.BLACK
            Cor.VERMELHO -> Color.rgb(198, 40, 40) to Color.WHITE
        }

        val caixa = LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(fundo)
                cornerRadius = dp(16).toFloat()
            }
            addView(TextView(servico).apply {
                text = r.titulo
                setTextColor(corTexto)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            addView(TextView(servico).apply {
                text = r.detalhes
                setTextColor(corTexto)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
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
            y = dp(48)
            horizontalMargin = 0.03f
        }

        try {
            wm.addView(caixa, params)
            atual = caixa
            handler.postDelayed(esconder, segundos.coerceIn(1, 30) * 1000L)
        } catch (e: Exception) {
            Log.e("BannerSobreposto", "Não foi possível mostrar o banner", e)
        }
    }

    fun remover() {
        handler.removeCallbacks(esconder)
        atual?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        atual = null
    }

    private fun dp(v: Int): Int = (v * servico.resources.displayMetrics.density).toInt()
}
