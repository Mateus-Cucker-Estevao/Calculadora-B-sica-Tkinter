package com.mateus.avaliadorcorridas.servico

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mateus.avaliadorcorridas.R
import com.mateus.avaliadorcorridas.dados.Configuracao
import com.mateus.avaliadorcorridas.dados.Corrida
import com.mateus.avaliadorcorridas.dados.LogDiagnostico
import com.mateus.avaliadorcorridas.dados.Preferencias
import com.mateus.avaliadorcorridas.dados.RepositorioTurnos
import com.mateus.avaliadorcorridas.regras.Avaliador
import com.mateus.avaliadorcorridas.regras.ExtratorOferta
import com.mateus.avaliadorcorridas.regras.Oferta
import com.mateus.avaliadorcorridas.regras.RegrasExtracao
import com.mateus.avaliadorcorridas.ui.MainActivity

/**
 * Serviço que roda DURANTE O TURNO e lê as ofertas pela IMAGEM da tela.
 *
 * Uma vez por segundo tira uma "foto" da parte de baixo da tela (onde fica o cartão
 * da oferta) e reconhece o texto com o ML Kit, 100% no celular (o app nem tem
 * permissão de internet). As imagens ficam só na memória e são descartadas na hora.
 *
 * Com o texto reconhecido ele:
 *   1. identifica a oferta (RegrasExtracao / ExtratorOferta);
 *   2. avalia com seus parâmetros, fala e mostra o aviso flutuante;
 *   3. quando a oferta some e aparece a tela de corrida em andamento, registra a
 *      corrida no turno aberto.
 *
 * IMPORTANTE: o app APENAS LÊ. Ele não toca na tela, não aceita nem recusa nada.
 */
class MonitorService : Service() {

    companion object {
        const val EXTRA_CODIGO = "codigo"
        const val EXTRA_DADOS = "dados"

        /** true enquanto a leitura da tela está rodando. */
        @Volatile
        var ativo = false
            private set

        /** A tela do nosso próprio app está aberta? (não lemos a tela nesse caso) */
        @Volatile
        var appAberto = false

        private const val TAG = "MonitorService"
        private const val CANAL = "turno"
        private const val ID_NOTIFICACAO = 1
        private const val INTERVALO_MS = 1000L

        /** Ignora os 40% de cima da tela (mapa e o nosso aviso); o cartão da oferta fica embaixo. */
        private const val CORTE_TOPO = 0.40f
        private const val LARGURA_MAXIMA = 1080

        private const val NAO_REPETIR_MESMA_OFERTA_MS = 30_000L
        private const val INTERVALO_LOG_MS = 10_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var falador: Falador
    private lateinit var banner: BannerSobreposto

    private var projecao: MediaProjection? = null
    private var telaVirtual: VirtualDisplay? = null
    private var leitor: ImageReader? = null
    private var processando = false

    // Estado da oferta atual
    private var ofertaVistaEm = 0L
    private var ultimaLeituraTinhaOferta = false
    private var tentativasSemViagem = 0
    private var ultimaChaveOferta = ""
    private var ultimaOfertaEm = 0L

    // Para detectar corrida aceita
    private var ofertaPendente: Oferta? = null
    private var ofertaPendenteEm = 0L

    // Diagnóstico
    private var ultimoTextoNoLog = ""
    private var ultimoLogEm = 0L

    private val ciclo = object : Runnable {
        override fun run() {
            capturar()
            handler.postDelayed(this, INTERVALO_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RepositorioTurnos.iniciar(this)
        falador = Falador(this)
        banner = BannerSobreposto(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // O Android exige que o aviso fixo (notificação) apareça ANTES de começar a captura.
        iniciarPrimeiroPlano()

        val codigo = intent?.getIntExtra(EXTRA_CODIGO, 0) ?: 0
        val dados: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DADOS, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DADOS)
        }
        if (codigo != Activity.RESULT_OK || dados == null) {
            encerrar()
            return START_NOT_STICKY
        }

        pararCaptura()
        try {
            iniciarCaptura(codigo, dados)
            if (Preferencias.carregar(this).modoDiagnostico) LogDiagnostico.registrarNota(this, "Leitura da tela iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível iniciar a captura", e)
            encerrar()
        }
        return START_NOT_STICKY
    }

    private fun iniciarPrimeiroPlano() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Turno em andamento", NotificationManager.IMPORTANCE_LOW))
        val abrirApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacao = Notification.Builder(this, CANAL)
            .setContentTitle("Turno em andamento")
            .setContentText("Lendo as ofertas do Uber pela imagem da tela. Nada sai do celular.")
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(abrirApp)
            .setOngoing(true)
            .build()
        startForeground(ID_NOTIFICACAO, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    // ---------------------------------------------------------------------
    // Captura da tela
    // ---------------------------------------------------------------------

    private fun iniciarCaptura(codigo: Int, dados: Intent) {
        val gerenciador = getSystemService(MediaProjectionManager::class.java)
        val p = gerenciador.getMediaProjection(codigo, dados) ?: run { encerrar(); return }
        projecao = p

        // Se você tocar em "Parar compartilhamento" na barra do Android, a leitura para
        // (o turno continua aberto; dá para retomar pela tela do app).
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { encerrar() }
            }
        }, handler)

        val (larguraTela, alturaTela) = tamanhoTela()
        val escala = if (larguraTela > LARGURA_MAXIMA) LARGURA_MAXIMA.toFloat() / larguraTela else 1f
        val largura = (larguraTela * escala).toInt()
        val altura = (alturaTela * escala).toInt()

        val r = ImageReader.newInstance(largura, altura, PixelFormat.RGBA_8888, 2)
        leitor = r
        telaVirtual = p.createVirtualDisplay(
            "avaliador-leitura", largura, altura, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler,
        )
        ativo = true
        handler.postDelayed(ciclo, INTERVALO_MS)
    }

    private fun tamanhoTela(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val limites = wm.maximumWindowMetrics.bounds
            limites.width() to limites.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    private fun capturar() {
        val r = leitor ?: return
        val imagem = try { r.acquireLatestImage() } catch (e: Exception) { null }
        if (imagem == null) {
            // Tela não mudou desde a última foto: se tinha oferta, ela continua lá.
            if (ultimaLeituraTinhaOferta) ofertaVistaEm = SystemClock.elapsedRealtime()
            return
        }
        if (processando || appAberto) {
            imagem.close()
            return
        }

        val recorte = try { recortarParteDeBaixo(imagem) } catch (e: Exception) { null } finally { imagem.close() }
        if (recorte == null) return

        processando = true
        reconhecedor.process(InputImage.fromBitmap(recorte, 0))
            .addOnSuccessListener { resultado ->
                // Linhas na ordem de leitura: de cima para baixo, da esquerda para a direita.
                val linhas = resultado.textBlocks
                    .flatMap { it.lines }
                    .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                    .map { it.text }
                processarLinhas(linhas)
            }
            .addOnFailureListener { Log.w(TAG, "Falha no reconhecimento de texto", it) }
            .addOnCompleteListener {
                processando = false
                recorte.recycle()
            }
    }

    private fun recortarParteDeBaixo(imagem: Image): Bitmap {
        val plano = imagem.planes[0]
        // Cada linha da imagem pode ter "sobra" no fim (rowStride); a largura do buffer considera isso.
        val larguraBuffer = plano.rowStride / plano.pixelStride
        val completo = Bitmap.createBitmap(larguraBuffer, imagem.height, Bitmap.Config.ARGB_8888)
        completo.copyPixelsFromBuffer(plano.buffer)
        val topo = (imagem.height * CORTE_TOPO).toInt()
        val recorte = Bitmap.createBitmap(completo, 0, topo, imagem.width, imagem.height - topo)
        if (recorte !== completo) completo.recycle()
        return recorte
    }

    // ---------------------------------------------------------------------
    // O que fazer com o texto lido
    // ---------------------------------------------------------------------

    private fun processarLinhas(linhas: List<String>) {
        val cfg = Preferencias.carregar(this)
        val oferta = ExtratorOferta.extrair(linhas)
        val agora = SystemClock.elapsedRealtime()
        ultimaLeituraTinhaOferta = oferta.ehOferta

        if (cfg.modoDiagnostico) registrarNoLog(linhas, oferta, agora)

        if (oferta.ehOferta) {
            ofertaVistaEm = agora
            ofertaPendente = oferta
            ofertaPendenteEm = agora
            avaliarEAvisar(oferta, cfg)
        } else {
            tentativasSemViagem = 0
            verificarCorridaAceita(linhas, cfg, agora)
        }
    }

    private fun avaliarEAvisar(oferta: Oferta, cfg: Configuracao) {
        // A oferta pode aparecer "aos pedaços": sem a distância da viagem, espera a próxima foto.
        if (oferta.kmViagem == null && tentativasSemViagem < RegrasExtracao.TENTATIVAS_ESPERANDO_VIAGEM) {
            tentativasSemViagem++
            return
        }
        tentativasSemViagem = 0

        val agora = SystemClock.elapsedRealtime()
        if (oferta.chave == ultimaChaveOferta && agora - ultimaOfertaEm < NAO_REPETIR_MESMA_OFERTA_MS) return
        ultimaChaveOferta = oferta.chave
        ultimaOfertaEm = agora

        RepositorioTurnos.alterarTurnoAberto(this) { it.copy(ofertasVistas = it.ofertasVistas + 1) }

        val r = Avaliador.avaliar(oferta, cfg)
        if (cfg.modoDiagnostico) LogDiagnostico.registrarNota(this, "AVISO ${r.cor}: \"${r.fala}\"  (${r.detalhes})")
        banner.mostrar(r, BannerSobreposto.Duracao.EnquantoOferta { ofertaVistaEm })
        if (cfg.vozAtiva) falador.falar(r.fala)
    }

    /** Depois que a oferta some: se aparecer a tela de corrida em andamento, a oferta foi aceita. */
    private fun verificarCorridaAceita(linhas: List<String>, cfg: Configuracao, agora: Long) {
        val o = ofertaPendente ?: return
        if (agora - ofertaPendenteEm > RegrasExtracao.JANELA_CORRIDA_ACEITA_MS) {
            ofertaPendente = null
            return
        }
        if (!ExtratorOferta.pareceCorridaAceita(linhas)) return
        ofertaPendente = null

        val corrida = Corrida(
            id = RepositorioTurnos.novoId(),
            hora = System.currentTimeMillis(),
            valor = o.valor ?: 0.0,
            kmBusca = o.kmAtePassageiro ?: 0.0,
            kmViagem = o.kmViagem ?: 0.0,
            minBusca = o.minutosAtePassageiro ?: 0,
            minViagem = o.minutosViagem ?: 0,
            nota = o.nota,
        )
        RepositorioTurnos.alterarTurnoAberto(this) { it.copy(corridas = it.corridas + corrida) }
        if (cfg.modoDiagnostico) {
            LogDiagnostico.registrarNota(this, "CORRIDA ACEITA registrada no turno: ${Avaliador.reais(corrida.valor)}, ${Avaliador.km(corrida.km)} km")
        }
    }

    private fun registrarNoLog(linhas: List<String>, oferta: Oferta, agora: Long) {
        val junto = linhas.joinToString("\n")
        // A tela é lida a cada segundo; para o log não crescer demais, registramos toda
        // oferta e toda tela de corrida, e as outras telas no máximo a cada 10 s.
        val importante = oferta.ehOferta || ExtratorOferta.pareceCorridaAceita(linhas)
        if (junto == ultimoTextoNoLog || (!importante && agora - ultimoLogEm < INTERVALO_LOG_MS)) return
        ultimoTextoNoLog = junto
        ultimoLogEm = agora
        val leitura = (if (oferta.ehOferta) "OFERTA  " else "(não é oferta)  ") + oferta.resumo()
        LogDiagnostico.registrar(this, linhas, leitura, "IMAGEM")
    }

    // ---------------------------------------------------------------------
    // Encerrar
    // ---------------------------------------------------------------------

    private fun pararCaptura() {
        handler.removeCallbacks(ciclo)
        telaVirtual?.release()
        telaVirtual = null
        leitor?.close()
        leitor = null
        projecao?.let { p ->
            projecao = null
            try { p.stop() } catch (_: Exception) {}
        }
        ativo = false
    }

    private fun encerrar() {
        pararCaptura()
        banner.remover()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pararCaptura()
        handler.removeCallbacksAndMessages(null)
        banner.remover()
        falador.desligar()
        reconhecedor.close()
        super.onDestroy()
    }
}
