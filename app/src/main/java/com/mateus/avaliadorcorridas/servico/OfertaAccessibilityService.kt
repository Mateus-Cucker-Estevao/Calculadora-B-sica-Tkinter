package com.mateus.avaliadorcorridas.servico

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mateus.avaliadorcorridas.dados.Configuracao
import com.mateus.avaliadorcorridas.dados.LogDiagnostico
import com.mateus.avaliadorcorridas.dados.Preferencias
import com.mateus.avaliadorcorridas.regras.Avaliador
import com.mateus.avaliadorcorridas.regras.ExtratorOferta
import com.mateus.avaliadorcorridas.regras.RegrasExtracao
import com.mateus.avaliadorcorridas.regras.Resultado

/**
 * Serviço que roda em segundo plano e LÊ a tela do Uber Driver.
 *
 * IMPORTANTE: este serviço APENAS LÊ. Ele nunca chama performAction(),
 * dispatchGesture() nem nada que toque na tela. Aceitar ou recusar é sempre com você.
 */
class OfertaAccessibilityService : AccessibilityService() {

    companion object {
        /** Referência ao serviço quando ele está ativo (usada pelo botão "Testar"). */
        @Volatile
        var instancia: OfertaAccessibilityService? = null
            private set

        private const val ESPERA_ANTES_DE_LER_MS = 350L
        private const val ESPERA_NOVA_TENTATIVA_MS = 500L
        private const val NAO_REPETIR_MESMA_OFERTA_MS = 30_000L
        private const val MAX_NOS = 2000
        private const val MAX_PROFUNDIDADE = 50
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var falador: Falador
    private lateinit var banner: BannerSobreposto

    private var leituraAgendada = false
    private var tentativasSemViagem = 0
    private var ultimoTextoNoLog = ""
    private var ultimaChaveOferta = ""
    private var ultimaOfertaEm = 0L

    private val lerAgora = Runnable {
        leituraAgendada = false
        lerTela()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        falador = Falador(this)
        banner = BannerSobreposto(this)
        instancia = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != RegrasExtracao.PACOTE_UBER) return
        val cfg = Preferencias.carregar(this)
        if (!cfg.monitorando && !cfg.modoDiagnostico) return

        // A tela do Uber gera MUITOS eventos seguidos (o círculo da oferta anima).
        // Em vez de ler a cada evento, lemos no máximo uma vez a cada 350 ms.
        agendarLeitura(ESPERA_ANTES_DE_LER_MS)
    }

    private fun agendarLeitura(atraso: Long) {
        if (leituraAgendada) return
        leituraAgendada = true
        handler.postDelayed(lerAgora, atraso)
    }

    private fun lerTela() {
        val cfg = Preferencias.carregar(this)
        val linhas = coletarTextos()
        if (linhas.isEmpty()) return

        val oferta = ExtratorOferta.extrair(linhas)

        if (cfg.modoDiagnostico) {
            val junto = linhas.joinToString("\n")
            if (junto != ultimoTextoNoLog) {
                ultimoTextoNoLog = junto
                val leitura = (if (oferta.ehOferta) "OFERTA  " else "(não é oferta)  ") + oferta.resumo()
                LogDiagnostico.registrar(this, linhas, leitura)
            }
        }

        if (!cfg.monitorando || !oferta.ehOferta) {
            tentativasSemViagem = 0
            return
        }

        // A oferta pode aparecer "aos pedaços". Se ainda não tem a distância da viagem,
        // esperamos um pouco e lemos de novo antes de avisar.
        if (oferta.kmViagem == null && tentativasSemViagem < RegrasExtracao.TENTATIVAS_ESPERANDO_VIAGEM) {
            tentativasSemViagem++
            agendarLeitura(ESPERA_NOVA_TENTATIVA_MS)
            return
        }
        tentativasSemViagem = 0

        val agora = SystemClock.elapsedRealtime()
        if (oferta.chave == ultimaChaveOferta && agora - ultimaOfertaEm < NAO_REPETIR_MESMA_OFERTA_MS) return
        ultimaChaveOferta = oferta.chave
        ultimaOfertaEm = agora

        anunciar(Avaliador.avaliar(oferta, cfg), cfg)
    }

    private fun anunciar(r: Resultado, cfg: Configuracao) {
        if (cfg.vozAtiva) falador.falar(r.fala)
        banner.mostrar(r, cfg.segundosBanner)
    }

    /** Usado pela seção "Testar leitura" da tela principal. */
    fun testar(linhas: List<String>): Resultado? {
        val cfg = Preferencias.carregar(this)
        val oferta = ExtratorOferta.extrair(linhas)
        if (!oferta.ehOferta) {
            falador.falar("Nenhuma oferta encontrada no texto")
            return null
        }
        return Avaliador.avaliar(oferta, cfg).also { anunciar(it, cfg) }
    }

    fun falar(texto: String) = falador.falar(texto)

    /** Junta todos os textos visíveis das janelas do Uber, de cima para baixo. */
    private fun coletarTextos(): List<String> {
        val raizes = mutableListOf<AccessibilityNodeInfo>()
        try {
            windows.forEach { janela ->
                janela.root?.let { if (it.packageName?.toString() == RegrasExtracao.PACOTE_UBER) raizes += it }
            }
        } catch (_: Exception) {
            // Alguns celulares não permitem listar janelas; usamos só a janela ativa.
        }
        if (raizes.isEmpty()) {
            rootInActiveWindow?.let { if (it.packageName?.toString() == RegrasExtracao.PACOTE_UBER) raizes += it }
        }

        val saida = mutableListOf<String>()
        val contador = intArrayOf(0)
        raizes.forEach { percorrer(it, saida, 0, contador) }
        return saida
    }

    private fun percorrer(no: AccessibilityNodeInfo, saida: MutableList<String>, profundidade: Int, contador: IntArray) {
        if (profundidade > MAX_PROFUNDIDADE || contador[0] > MAX_NOS) return
        contador[0]++

        val texto = no.text?.toString()?.trim().orEmpty()
        val descricao = no.contentDescription?.toString()?.trim().orEmpty()
        if (texto.isNotEmpty()) saida += texto
        if (descricao.isNotEmpty() && descricao != texto) saida += descricao

        for (i in 0 until no.childCount) {
            no.getChild(i)?.let { percorrer(it, saida, profundidade + 1, contador) }
        }
    }

    override fun onInterrupt() {
        // Chamado quando o sistema pede para parar de falar.
        if (::falador.isInitialized) falador.parar()
    }

    override fun onDestroy() {
        instancia = null
        handler.removeCallbacksAndMessages(null)
        if (::banner.isInitialized) banner.remover()
        if (::falador.isInitialized) falador.desligar()
        super.onDestroy()
    }
}
