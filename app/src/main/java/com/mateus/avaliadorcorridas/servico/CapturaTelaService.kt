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
import com.mateus.avaliadorcorridas.dados.Preferencias
import com.mateus.avaliadorcorridas.ui.MainActivity

/**
 * PLANO B: lê as ofertas pela IMAGEM da tela.
 *
 * Usado quando o Uber esconde o texto da oferta do serviço de acessibilidade.
 * Uma vez por segundo, SÓ enquanto o Uber está aberto na tela, tira uma "foto"
 * da parte de baixo da tela (onde fica o cartão da oferta) e reconhece o texto
 * com o ML Kit, que roda 100% no celular (o app nem tem permissão de internet).
 * As imagens ficam só na memória e são descartadas logo depois; nada é salvo.
 *
 * O texto reconhecido vai para o OfertaAccessibilityService, que avalia e avisa
 * exatamente como na leitura normal.
 */
class CapturaTelaService : Service() {

    companion object {
        const val EXTRA_CODIGO = "codigo"
        const val EXTRA_DADOS = "dados"

        /** true enquanto a captura está rodando (a tela principal usa para mostrar o botão). */
        @Volatile
        var ativo = false
            private set

        private const val TAG = "CapturaTela"
        private const val CANAL = "leitura_imagem"
        private const val ID_NOTIFICACAO = 1
        private const val INTERVALO_MS = 1000L

        /** Só lê a imagem se o Uber mandou algum evento nos últimos 3 segundos (está na tela). */
        private const val UBER_NA_TELA_MS = 3000L

        /** Ignora os 40% de cima da tela (mapa); o cartão da oferta fica embaixo. */
        private const val CORTE_TOPO = 0.40f

        /** Largura máxima da imagem analisada (menor = mais rápido). */
        private const val LARGURA_MAXIMA = 1080
    }

    private val handler = Handler(Looper.getMainLooper())
    private val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var projecao: MediaProjection? = null
    private var telaVirtual: VirtualDisplay? = null
    private var leitor: ImageReader? = null
    private var processando = false

    private val ciclo = object : Runnable {
        override fun run() {
            capturar()
            handler.postDelayed(this, INTERVALO_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível iniciar a captura", e)
            encerrar()
        }
        return START_NOT_STICKY
    }

    private fun iniciarPrimeiroPlano() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Leitura por imagem", NotificationManager.IMPORTANCE_LOW),
        )
        val abrirApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacao = Notification.Builder(this, CANAL)
            .setContentTitle("Avaliador: leitura por imagem ligada")
            .setContentText("Lê as ofertas do Uber pela imagem da tela. Nada sai do celular.")
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(abrirApp)
            .setOngoing(true)
            .build()
        startForeground(ID_NOTIFICACAO, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun iniciarCaptura(codigo: Int, dados: Intent) {
        val gerenciador = getSystemService(MediaProjectionManager::class.java)
        val p = gerenciador.getMediaProjection(codigo, dados) ?: run { encerrar(); return }
        projecao = p

        // Se você tocar em "Parar compartilhamento" na barra do Android, desligamos tudo.
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
            "avaliador-leitura",
            largura, altura, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, handler,
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
        // Sempre pegamos a imagem mais recente (para a fila não encher), mas só analisamos
        // quando o Uber está na tela e o monitoramento ou o diagnóstico estão ligados.
        val imagem = try { r.acquireLatestImage() } catch (e: Exception) { null } ?: return
        val cfg = Preferencias.carregar(this)
        val uberNaTela = SystemClock.elapsedRealtime() - OfertaAccessibilityService.ultimoEventoUberEm < UBER_NA_TELA_MS
        val servico = OfertaAccessibilityService.instancia
        if (processando || servico == null || !uberNaTela || (!cfg.monitorando && !cfg.modoDiagnostico)) {
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
                servico.processarImagem(linhas)
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pararCaptura()
        reconhecedor.close()
        super.onDestroy()
    }
}
