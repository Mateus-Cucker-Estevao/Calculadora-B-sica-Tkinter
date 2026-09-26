package com.mateus.avaliadorcorridas.servico

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.mateus.avaliadorcorridas.dados.Configuracao
import com.mateus.avaliadorcorridas.dados.LogDiagnostico
import com.mateus.avaliadorcorridas.dados.Preferencias
import com.mateus.avaliadorcorridas.regras.Avaliador
import com.mateus.avaliadorcorridas.regras.ExtratorOferta
import com.mateus.avaliadorcorridas.regras.Oferta
import com.mateus.avaliadorcorridas.regras.RegrasExtracao
import com.mateus.avaliadorcorridas.regras.Resultado

/**
 * Serviço que roda em segundo plano e LÊ a tela do Uber Driver.
 *
 * Duas formas de leitura chegam aqui e passam pela MESMA avaliação:
 *   - "tela":   texto que o Android entrega pela acessibilidade (padrão);
 *   - "imagem": texto reconhecido na imagem da tela (CapturaTelaService, plano B).
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

        /** Quando chegou o último evento do Uber (o Uber está na tela se foi há pouco tempo). */
        @Volatile
        var ultimoEventoUberEm = 0L
            private set

        private const val ESPERA_ANTES_DE_LER_MS = 350L
        private const val ESPERA_NOVA_TENTATIVA_MS = 500L
        private const val NAO_REPETIR_MESMA_OFERTA_MS = 30_000L
        private const val INTERVALO_LOG_IMAGEM_MS = 10_000L
        private const val MAX_NOS = 2000
        private const val MAX_PROFUNDIDADE = 50
    }

    /** Resultado de uma leitura da árvore de acessibilidade. */
    private class Coleta(val linhas: List<String>, val resumo: String)

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var falador: Falador
    private lateinit var banner: BannerSobreposto

    private var leituraAgendada = false
    private var tentativasSemViagem = 0
    private var ultimoTextoNoLog = ""
    private var ultimaImagemNoLog = ""
    private var ultimaImagemNoLogEm = 0L
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
        val cfg = Preferencias.carregar(this)
        if (cfg.modoDiagnostico) {
            LogDiagnostico.registrarNota(this, "Serviço conectado (monitoramento ${if (cfg.monitorando) "LIGADO" else "DESLIGADO"})")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pacote = event.packageName?.toString()
        // Eventos de "janelas mudaram" às vezes chegam sem nome de pacote; nesse caso
        // lemos mesmo assim (coletarTextos só pega janelas do Uber).
        val semPacote = pacote == null && event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (pacote != RegrasExtracao.PACOTE_UBER && !semPacote) return
        if (pacote != null) ultimoEventoUberEm = SystemClock.elapsedRealtime()

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

    // ---------------------------------------------------------------------
    // Leitura 1: texto da acessibilidade
    // ---------------------------------------------------------------------

    private fun lerTela() {
        val cfg = Preferencias.carregar(this)
        val coleta = coletarTextos()
        val oferta = ExtratorOferta.extrair(coleta.linhas)

        if (cfg.modoDiagnostico) {
            // O resumo das janelas entra na comparação: se aparecer uma janela nova
            // (ex.: o cartão da oferta) sem texto legível, isso também fica registrado.
            val chave = coleta.resumo + "\n" + coleta.linhas.joinToString("\n")
            if (chave != ultimoTextoNoLog) {
                ultimoTextoNoLog = chave
                LogDiagnostico.registrar(this, coleta.linhas, descrever(oferta), "TELA  " + coleta.resumo)
            }
        }

        if (coleta.linhas.isEmpty()) return
        avaliarEAvisar(oferta, cfg, podeReagendar = true)
    }

    // ---------------------------------------------------------------------
    // Leitura 2: texto reconhecido na imagem (chamado pelo CapturaTelaService)
    // ---------------------------------------------------------------------

    fun processarImagem(linhas: List<String>) {
        handler.post {
            val cfg = Preferencias.carregar(this)
            if (!cfg.monitorando && !cfg.modoDiagnostico) return@post
            val oferta = ExtratorOferta.extrair(linhas)

            if (cfg.modoDiagnostico) {
                val junto = linhas.joinToString("\n")
                val agora = SystemClock.elapsedRealtime()
                // A imagem é lida a cada segundo; para o log não crescer demais,
                // registramos toda oferta, mas outras telas no máximo a cada 10 s.
                if (junto != ultimaImagemNoLog && (oferta.ehOferta || agora - ultimaImagemNoLogEm > INTERVALO_LOG_IMAGEM_MS)) {
                    ultimaImagemNoLog = junto
                    ultimaImagemNoLogEm = agora
                    LogDiagnostico.registrar(this, linhas, descrever(oferta), "IMAGEM")
                }
            }

            avaliarEAvisar(oferta, cfg, podeReagendar = false)
        }
    }

    // ---------------------------------------------------------------------
    // Avaliação comum às duas leituras
    // ---------------------------------------------------------------------

    private fun avaliarEAvisar(oferta: Oferta, cfg: Configuracao, podeReagendar: Boolean) {
        if (!cfg.monitorando || !oferta.ehOferta) {
            tentativasSemViagem = 0
            return
        }

        // A oferta pode aparecer "aos pedaços". Se ainda não tem a distância da viagem,
        // esperamos um pouco e lemos de novo antes de avisar.
        if (oferta.kmViagem == null && tentativasSemViagem < RegrasExtracao.TENTATIVAS_ESPERANDO_VIAGEM) {
            tentativasSemViagem++
            if (podeReagendar) agendarLeitura(ESPERA_NOVA_TENTATIVA_MS)
            return
        }
        tentativasSemViagem = 0

        val agora = SystemClock.elapsedRealtime()
        if (oferta.chave == ultimaChaveOferta && agora - ultimaOfertaEm < NAO_REPETIR_MESMA_OFERTA_MS) return
        ultimaChaveOferta = oferta.chave
        ultimaOfertaEm = agora

        anunciar(Avaliador.avaliar(oferta, cfg), cfg)
    }

    private fun descrever(oferta: Oferta): String =
        (if (oferta.ehOferta) "OFERTA  " else "(não é oferta)  ") + oferta.resumo()

    private fun anunciar(r: Resultado, cfg: Configuracao) {
        if (cfg.modoDiagnostico) LogDiagnostico.registrarNota(this, "AVISO ${r.cor}: \"${r.fala}\"  (${r.detalhes})")
        banner.mostrar(r, cfg.segundosBanner, aguardarVoz = cfg.vozAtiva)
        if (cfg.vozAtiva) falador.falar(r.fala) { banner.vozTerminou() }
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

    /**
     * Junta todos os textos das janelas do Uber, de cima para baixo.
     * O resumo lista TODAS as janelas vistas (tipo:pacote) e quantos elementos
     * o Uber expôs — útil no log para saber se o cartão da oferta está escondido.
     */
    private fun coletarTextos(): Coleta {
        val raizes = mutableListOf<AccessibilityNodeInfo>()
        val janelas = mutableListOf<String>()
        try {
            windows.forEach { janela ->
                val raiz = janela.root
                val pacote = raiz?.packageName?.toString()
                janelas += "${tipoJanela(janela.type)}:${pacote?.substringAfterLast('.') ?: "?"}"
                if (raiz != null && pacote == RegrasExtracao.PACOTE_UBER) raizes += raiz
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
        val resumo = "janelas=[${janelas.joinToString(" ")}] janelasUber=${raizes.size} elementosUber=${contador[0]}"
        return Coleta(saida, resumo)
    }

    private fun tipoJanela(tipo: Int): String = when (tipo) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "app"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "sistema"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "teclado"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "sobreposicao"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "divisor"
        else -> "tipo$tipo"
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
