package com.mateus.avaliadorcorridas.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mateus.avaliadorcorridas.dados.Configuracao
import com.mateus.avaliadorcorridas.dados.Preferencias
import com.mateus.avaliadorcorridas.dados.RepositorioTurnos
import com.mateus.avaliadorcorridas.dados.Turno
import com.mateus.avaliadorcorridas.regras.Calculos
import com.mateus.avaliadorcorridas.regras.ResumoMes
import com.mateus.avaliadorcorridas.regras.ResumoTurno
import com.mateus.avaliadorcorridas.servico.MonitorService
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

/** Cor do lucro: verde se positivo, vermelho se negativo, cinza se zero. */
fun corLucro(v: Double): Color = when {
    v > 0.005 -> Cores.verde
    v < -0.005 -> Cores.vermelho
    else -> Cores.contorno
}

/** Liga a leitura da tela (o Android pergunta se pode gravar/compartilhar a tela). */
fun iniciarMonitor(ctx: Context, codigo: Int, dados: Intent) {
    ctx.startForegroundService(
        Intent(ctx, MonitorService::class.java)
            .putExtra(MonitorService.EXTRA_CODIGO, codigo)
            .putExtra(MonitorService.EXTRA_DADOS, dados),
    )
}

fun pararMonitor(ctx: Context) {
    ctx.stopService(Intent(ctx, MonitorService::class.java))
}

// ===========================================================================
// Tela do mês
// ===========================================================================

@Composable
fun TelaTurnos(
    mesSelecionado: YearMonth,
    onMudarMes: (YearMonth) -> Unit,
    onAbrirTurno: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val turnos by RepositorioTurnos.turnos.collectAsState()
    var cfg by remember { mutableStateOf(Preferencias.carregar(ctx)) }
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lendo by remember { mutableStateOf(MonitorService.ativo) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        cfg = Preferencias.carregar(ctx)
        agora = System.currentTimeMillis()
        lendo = MonitorService.ativo
    }
    // Atualiza o relógio do turno e o estado da leitura de vez em quando.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            agora = System.currentTimeMillis()
            lendo = MonitorService.ativo
        }
    }

    val aberto = turnos.firstOrNull { it.aberto }
    val doMes = turnos.filter { mes(it.inicio) == mesSelecionado }
    val resumoMes = Calculos.resumoMes(doMes, agora)
    val fechadosPorDia = doMes.filter { !it.aberto }
        .sortedByDescending { it.inicio }
        .groupBy { dia(it.inicio) }

    var pedirOdometroInicial by remember { mutableStateOf(false) }
    var pedirOdometroFinal by remember { mutableStateOf(false) }
    var odometroAguardandoPermissao by remember { mutableStateOf<Double?>(null) }

    val pedirCaptura = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resposta ->
        val dados = resposta.data
        if (resposta.resultCode == Activity.RESULT_OK && dados != null) {
            iniciarMonitor(ctx, resposta.resultCode, dados)
            lendo = true
        } else {
            Toast.makeText(ctx, "Sem autorização, as ofertas não são lidas. Toque em \"Retomar leitura\".", Toast.LENGTH_LONG).show()
        }
    }
    fun pedirLeitura() {
        pedirCaptura.launch(ctx.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
    fun criarTurno(odometro: Double) {
        val c = Preferencias.carregar(ctx)
        val agoraMs = System.currentTimeMillis()
        RepositorioTurnos.salvar(
            ctx,
            Turno(
                id = RepositorioTurnos.novoId(), inicio = agoraMs, odometroInicial = odometro,
                consumoKmL = c.consumoKmL, precoLitro = c.precoLitro, manutencaoPorKm = c.manutencaoPorKm,
            ),
        )
        onMudarMes(mes(agoraMs))
        pedirLeitura()
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SeletorMes(mesSelecionado, onMudarMes, estado = estadoMonitor(aberto, lendo)) }
        item { CartaoResumoMes(resumoMes, mesAtual = mesSelecionado == YearMonth.now()) }

        if (aberto != null) {
            item {
                CartaoTurnoAberto(
                    turno = aberto, agora = agora, lendo = lendo,
                    onRetomar = { pedirLeitura() },
                    onFinalizar = { pedirOdometroFinal = true },
                    onDetalhes = { onAbrirTurno(aberto.id) },
                )
            }
        } else {
            item {
                BotaoNovoTurno(if (turnos.isEmpty()) "Iniciar primeiro turno" else "Novo turno") {
                    pedirOdometroInicial = true
                }
            }
        }

        if (fechadosPorDia.isEmpty() && aberto == null) item { EstadoVazio() }

        fechadosPorDia.forEach { (d, lista) ->
            item(key = "dia-$d") { CabecalhoDia(d, lista.map { Calculos.resumo(it, agora) }) }
            items(lista, key = { it.id }) { t -> CartaoTurno(t, Calculos.resumo(t, agora)) { onAbrirTurno(t.id) } }
        }

        item { CartoesRapidos(cfg) }
    }

    // ---- Janelas ----

    if (pedirOdometroInicial) {
        DialogoNumero(
            titulo = "Novo turno",
            rotulo = "KM do odômetro",
            textoConfirmar = "Iniciar turno",
            explicacao = "Digite o km que aparece no painel do carro agora. No fim do turno, o app pede o km final " +
                "para calcular o quanto você rodou sem passageiro.",
            validar = { if (it < 0) "O km não pode ser negativo" else null },
            onCancelar = { pedirOdometroInicial = false },
        ) { odometro ->
            pedirOdometroInicial = false
            if (Settings.canDrawOverlays(ctx)) criarTurno(odometro) else odometroAguardandoPermissao = odometro
        }
    }

    odometroAguardandoPermissao?.let { odometro ->
        AlertDialog(
            onDismissRequest = { odometroAguardandoPermissao = null },
            containerColor = Cores.containerAlto,
            title = { Text("Permitir o aviso na tela", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Para o aviso aparecer por cima do Uber, ative \"Sobrepor a outros apps\" (ou \"Exibir sobre outros apps\") " +
                        "para o Avaliador de Corridas. Depois volte aqui e toque em Novo turno de novo.",
                    color = Cores.textoVariante,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    odometroAguardandoPermissao = null
                    abrirPermissaoSobreposicao(ctx)
                }) { Text("Abrir permissão") }
            },
            dismissButton = {
                TextButton(onClick = {
                    odometroAguardandoPermissao = null
                    criarTurno(odometro)
                }) { Text("Continuar só com voz") }
            },
        )
    }

    if (pedirOdometroFinal && aberto != null) {
        DialogoNumero(
            titulo = "Finalizar turno",
            rotulo = "Km do odômetro final",
            textoConfirmar = "Finalizar",
            explicacao = "Odômetro no início: ${numero(aberto.odometroInicial, 1)} km.",
            validar = { if (it < aberto.odometroInicial) "O km final não pode ser menor que o inicial" else null },
            onCancelar = { pedirOdometroFinal = false },
        ) { final ->
            pedirOdometroFinal = false
            RepositorioTurnos.salvar(ctx, aberto.copy(fim = System.currentTimeMillis(), odometroFinal = final))
            pararMonitor(ctx)
            lendo = false
            onMudarMes(mes(aberto.inicio))
            Toast.makeText(ctx, "Turno finalizado", Toast.LENGTH_SHORT).show()
        }
    }
}

fun abrirPermissaoSobreposicao(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private enum class EstadoMonitor { MONITORANDO, LEITURA_PARADA, PARADO }

private fun estadoMonitor(aberto: Turno?, lendo: Boolean) = when {
    aberto == null -> EstadoMonitor.PARADO
    lendo -> EstadoMonitor.MONITORANDO
    else -> EstadoMonitor.LEITURA_PARADA
}

// ---------------------------------------------------------------------------
// Partes da tela do mês
// ---------------------------------------------------------------------------

@Composable
private fun SeletorMes(mesAtual: YearMonth, onMudar: (YearMonth) -> Unit, estado: EstadoMonitor) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.background(Cores.container, RoundedCornerShape(12.dp)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMudar(mesAtual.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior", tint = Cores.textoVariante)
            }
            Text("📅 ${nomeMes(mesAtual)}", color = Cores.texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            IconButton(onClick = { onMudar(mesAtual.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês", tint = Cores.textoVariante)
            }
        }
        Spacer(Modifier.weight(1f))
        val (texto, cor) = when (estado) {
            EstadoMonitor.MONITORANDO -> "Monitorando" to Cores.verde
            EstadoMonitor.LEITURA_PARADA -> "Leitura parada" to Cores.amarelo
            EstadoMonitor.PARADO -> "Parado" to Cores.contorno
        }
        Row(
            Modifier.background(cor.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(cor, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(texto.uppercase(BR), color = cor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CartaoResumoMes(r: ResumoMes, mesAtual: Boolean) {
    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rotulo("Lucro real líquido")
            Spacer(Modifier.weight(1f))
            Text(
                if (mesAtual) "Ciclo aberto" else "Mês fechado",
                color = Cores.textoVariante, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(Cores.containerAlto, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(reais(r.lucro), color = corLucro(r.lucro), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(6.dp))
            Text("líquido", color = Cores.contorno, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(
            Modifier.fillMaxWidth().background(Cores.containerBaixo, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemPonto("Faturamento", reais(r.faturamento), Cores.verde, Modifier.weight(1f))
            ItemPonto("Custos", reais(r.custos), Cores.vermelho, Modifier.weight(1f))
        }
        LinearProgressIndicator(
            progress = { (r.aproveitamento ?: 0.0).toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Cores.primariaContainer,
            trackColor = Cores.containerMaisAlto,
            drawStopIndicator = {},
        )
        Row {
            Text("Total ${km(r.kmTotal)} rodados", color = Cores.contorno, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "${r.aproveitamento?.let { porcentagem(it) } ?: "0%"} aproveitamento pago",
                color = Cores.contorno, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            Modifier.fillMaxWidth().background(Cores.containerAlto, RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Metrica("Turnos", r.turnos.toString())
            Metrica("Horas totais", horas(r.horas))
            Metrica("Média lucro", r.lucroPorHora?.let { reais(it) + "/h" } ?: "R$ 0,00/h")
        }
    }
}

@Composable
private fun ItemPonto(rotulo: String, valor: String, cor: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(cor, CircleShape))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(rotulo, color = Cores.textoVariante, fontSize = 12.sp)
            Text(valor, color = Cores.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun Metrica(rotulo: String, valor: String, corValor: Color = Cores.texto, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(rotulo, color = Cores.contorno, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(valor, color = corValor, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CartaoTurnoAberto(
    turno: Turno,
    agora: Long,
    lendo: Boolean,
    onRetomar: () -> Unit,
    onFinalizar: () -> Unit,
    onDetalhes: () -> Unit,
) {
    val r = Calculos.resumo(turno, agora)
    Cartao(Modifier.border(1.dp, Cores.verde.copy(alpha = 0.5f), RoundedCornerShape(16.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Cores.verde, CircleShape))
            Spacer(Modifier.width(8.dp))
            Rotulo("Turno em andamento", Cores.verde)
            Spacer(Modifier.weight(1f))
            Text("desde ${hora(turno.inicio)} · ${horas(r.horas)}", color = Cores.textoVariante, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Metrica("Corridas", r.corridas.toString())
            Metrica("Faturamento", reais(r.faturamento))
            Metrica("Lucro est.", reais(r.lucro), corLucro(r.lucro))
        }
        Text(
            "Ofertas vistas: ${turno.ofertasVistas} · Odômetro inicial: ${numero(turno.odometroInicial, 1)} km",
            color = Cores.contorno, fontSize = 12.sp,
        )
        val revisar = turno.corridas.count { it.precisaRevisar }
        if (revisar > 0) {
            Text(
                "⚠️ $revisar corrida(s) sem oferta lida: revise em Detalhes",
                color = Cores.amarelo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        if (lendo) {
            Text("✅ Lendo as ofertas pela imagem da tela", color = Cores.verde, fontSize = 13.sp)
        } else {
            Text("⚠️ Leitura das ofertas parada", color = Cores.amarelo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onRetomar, modifier = Modifier.fillMaxWidth()) { Text("Retomar leitura") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDetalhes, modifier = Modifier.weight(1f)) { Text("Detalhes") }
            Button(
                onClick = onFinalizar,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Cores.vermelhoForte, contentColor = Color.White),
            ) { Text("Finalizar turno", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BotaoNovoTurno(texto: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Cores.primariaContainer, contentColor = Cores.naPrimariaContainer),
    ) {
        Box(
            Modifier.size(28.dp).background(Cores.naPrimariaContainer.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, null, tint = Cores.naPrimariaContainer) }
        Spacer(Modifier.width(8.dp))
        Text(texto.uppercase(BR), fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun EstadoVazio() {
    Column(
        Modifier.fillMaxWidth().background(Cores.containerBaixo, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(80.dp).background(Cores.containerAlto, CircleShape), contentAlignment = Alignment.Center) {
            Text("🏁", fontSize = 36.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("Nenhum turno neste mês", color = Cores.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Seus turnos e estatísticas de corridas aparecerão aqui assim que você iniciar o trabalho.",
            color = Cores.contorno, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().background(Cores.container, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).background(Cores.amarelo.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("💡", fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Dica de cockpit", color = Cores.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Configure o consumo e o preço do combustível em Parâmetros antes de partir.",
                    color = Cores.textoVariante, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun CabecalhoDia(d: LocalDate, resumos: List<ResumoTurno>) {
    val lucro = resumos.sumOf { it.lucro }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(nomeDia(d), color = Cores.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(reais(lucro), color = corLucro(lucro), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CartaoTurno(t: Turno, r: ResumoTurno, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Cores.container, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${hora(t.inicio)} – ${t.fim?.let { hora(it) } ?: "..."} · ${horas(r.horas)}",
                color = Cores.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            val vazio = r.aproveitamento?.let { " · ${porcentagem(1 - it)} sem passageiro" } ?: ""
            Text(
                "${r.corridas} corridas · ${km(r.kmTotal ?: r.kmCorridas)}$vazio",
                color = Cores.contorno, fontSize = 12.sp,
            )
            val revisar = t.corridas.count { it.precisaRevisar }
            if (revisar > 0) {
                Text("⚠️ $revisar corrida(s) para revisar", color = Cores.amarelo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(reais(r.lucro), color = corLucro(r.lucro), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("fat. ${reais(r.faturamento)}", color = Cores.contorno, fontSize = 12.sp)
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Cores.contorno)
    }
}

@Composable
private fun CartoesRapidos(cfg: Configuracao) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CartaoRapido("⛽", "Combustível", "${reais(cfg.precoLitro)} / L", "${numero(cfg.consumoKmL, 1)} km/l", Modifier.weight(1f))
        CartaoRapido(
            "🚗", "Custo por km", "${reais(Calculos.custoPorKm(cfg.consumoKmL, cfg.precoLitro, cfg.manutencaoPorKm))} / km",
            "com manutenção de ${reais(cfg.manutencaoPorKm)}/km", Modifier.weight(1f),
        )
    }
}

@Composable
private fun CartaoRapido(icone: String, rotulo: String, valor: String, detalhe: String, modifier: Modifier) {
    Column(
        modifier.background(Cores.container, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            Text(icone, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text(rotulo, color = Cores.contorno, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(valor, color = Cores.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(detalhe, color = Cores.verde, fontSize = 12.sp)
    }
}
