package com.mateus.avaliadorcorridas.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mateus.avaliadorcorridas.dados.Configuracao
import com.mateus.avaliadorcorridas.dados.LogDiagnostico
import com.mateus.avaliadorcorridas.dados.Preferencias
import com.mateus.avaliadorcorridas.regras.Avaliador
import com.mateus.avaliadorcorridas.regras.Cor
import com.mateus.avaliadorcorridas.regras.ExtratorOferta
import com.mateus.avaliadorcorridas.servico.CapturaTelaService
import com.mateus.avaliadorcorridas.servico.OfertaAccessibilityService
import java.util.Locale

private val VERDE = Color(0xFF2E7D32)
private val VERMELHO = Color(0xFFC62828)
private val BR = Locale.forLanguageTag("pt-BR")

private val EXEMPLO_OFERTA = """
    UberX
    R$ 18,50
    4,95 ★
    6 min (2,1 km) de distância
    Rua Henrique Lage, Centro, Criciúma
    Viagem de 18 min (8,4 km)
    Rua São João, Cocal do Sul - SC
    Aceitar
""".trimIndent()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = VERDE)) {
                Surface(Modifier.fillMaxSize()) { TelaPrincipal() }
            }
        }
    }
}

@Composable
private fun TelaPrincipal() {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(Preferencias.carregar(ctx)) }
    var ativo by remember { mutableStateOf(servicoAtivo(ctx)) }
    var mostrandoLog by remember { mutableStateOf(false) }

    // Sempre que você volta para o app, relê o estado (ex.: depois de ativar a acessibilidade).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        cfg = Preferencias.carregar(ctx)
        ativo = servicoAtivo(ctx)
    }

    val salvar: (Configuracao) -> Unit = { novo ->
        cfg = novo
        Preferencias.salvar(ctx, novo)
    }

    if (mostrandoLog) {
        TelaLog(onVoltar = { mostrandoLog = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Avaliador de Corridas", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Só lê e avisa. Nunca toca em nada. Nenhum dado sai do celular.", fontSize = 13.sp)

        CartaoStatus(ativo, cfg.monitorando) { salvar(cfg.copy(monitorando = !cfg.monitorando)) }
        CartaoLeituraImagem()
        CartaoCriterios(cfg, salvar)
        CartaoDestinos(cfg.destinosBloqueados) { salvar(cfg.copy(destinosBloqueados = it)) }
        CartaoTeste(ativo)
        CartaoDiagnostico(
            ligado = cfg.modoDiagnostico,
            onAlternar = { salvar(cfg.copy(modoDiagnostico = !cfg.modoDiagnostico)) },
            onVerLog = { mostrandoLog = true },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Cartao(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            conteudo()
        }
    }
}

// ---------------------------------------------------------------------------
// 1. Permissões e botão liga/desliga
// ---------------------------------------------------------------------------
@Composable
private fun CartaoStatus(servicoAtivo: Boolean, monitorando: Boolean, onAlternar: () -> Unit) {
    val ctx = LocalContext.current
    Cartao("Monitoramento") {
        if (servicoAtivo) {
            Text("✅ Serviço de acessibilidade ATIVO", color = VERDE, fontWeight = FontWeight.Bold)
        } else {
            Text("❌ Serviço de acessibilidade DESATIVADO", color = VERMELHO, fontWeight = FontWeight.Bold)
            Text("Toque no botão abaixo, procure \"Avaliador de Corridas\" (pode estar em \"Apps instalados\" ou \"Serviços baixados\") e ative.")
            Button(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Abrir configurações de acessibilidade") }
            Text(
                "Aparece \"Configuração restrita\" ou a opção fica cinza? Toque em \"Abrir informações do app\", " +
                    "depois nos 3 pontinhos (⋮) no canto de cima e em \"Permitir configurações restritas\". Volte e tente de novo.",
                fontSize = 13.sp,
            )
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Abrir informações do app") }
        }

        Button(
            onClick = onAlternar,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (monitorando) VERDE else Color.Gray),
        ) {
            Text(
                if (monitorando) "LIGADO — toque para desligar" else "DESLIGADO — toque para ligar",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Dica: adicione o botão \"Avaliador\" nas Configurações rápidas (puxe a cortina de cima → lápis/editar) " +
                "para ligar e desligar sem abrir o app.",
            fontSize = 13.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// 1b. Plano B: leitura pela imagem da tela
// ---------------------------------------------------------------------------
@Composable
private fun CartaoLeituraImagem() {
    val ctx = LocalContext.current
    var ligada by remember { mutableStateOf(CapturaTelaService.ativo) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ligada = CapturaTelaService.ativo }

    // Abre a janela do Android "Iniciar gravação/transmissão?" e, se você aceitar,
    // liga o serviço de captura com a autorização recebida.
    val pedirCaptura = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resposta ->
        val dados = resposta.data
        if (resposta.resultCode == Activity.RESULT_OK && dados != null) {
            ctx.startForegroundService(
                Intent(ctx, CapturaTelaService::class.java)
                    .putExtra(CapturaTelaService.EXTRA_CODIGO, resposta.resultCode)
                    .putExtra(CapturaTelaService.EXTRA_DADOS, dados),
            )
            ligada = true
        } else {
            Toast.makeText(ctx, "Leitura por imagem não autorizada", Toast.LENGTH_SHORT).show()
        }
    }

    Cartao("Leitura por imagem (plano B)") {
        Text(
            "Use se o app não reconhecer as ofertas: ele passa a ler o texto da IMAGEM da tela, " +
                "só enquanto o Uber está aberto. Tudo acontece no celular; nenhuma imagem é salva ou enviada.\n" +
                "Ao ligar, o Android pergunta se pode gravar/compartilhar a tela: escolha \"Tela inteira\" e toque em Iniciar. " +
                "Precisa ligar de novo cada vez que o celular reiniciar.",
            fontSize = 13.sp,
        )
        LinhaSwitch("Ler as ofertas pela imagem da tela", ligada) { ligar ->
            if (ligar) {
                val gerenciador = ctx.getSystemService(MediaProjectionManager::class.java)
                pedirCaptura.launch(gerenciador.createScreenCaptureIntent())
            } else {
                ctx.stopService(Intent(ctx, CapturaTelaService::class.java))
                ligada = false
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Critérios
// ---------------------------------------------------------------------------
@Composable
private fun CartaoCriterios(cfg: Configuracao, onSalvar: (Configuracao) -> Unit) {
    val ctx = LocalContext.current
    var porKm by remember { mutableStateOf(String.format(BR, "%.2f", cfg.minimoPorKm)) }
    var minimo by remember { mutableStateOf(String.format(BR, "%.2f", cfg.minimoCorrida)) }
    var maxBusca by remember { mutableStateOf(String.format(BR, "%.1f", cfg.maxKmAtePassageiro)) }
    var porHora by remember { mutableStateOf(String.format(BR, "%.0f", cfg.minimoPorHora)) }
    var notaMinima by remember { mutableStateOf(String.format(BR, "%.2f", cfg.notaMinima)) }
    var margem by remember { mutableStateOf(cfg.margemAmareloPct.toString()) }
    var segundos by remember { mutableStateOf(cfg.segundosBanner.toString()) }

    Cartao("Critérios") {
        CampoNumero("Valor mínimo por km (R$/km)", porKm) { porKm = it }
        CampoNumero("Valor mínimo da corrida (R$)", minimo) { minimo = it }
        CampoNumero("Distância máxima até o passageiro (km)", maxBusca) { maxBusca = it }
        CampoNumero("Ganho mínimo por hora (R$/h)", porHora) { porHora = it }
        CampoNumero("Nota mínima do passageiro (abaixo fica amarelo)", notaMinima) { notaMinima = it }
        CampoNumero("Margem do amarelo \"no limite\" (%)", margem) { margem = it }
        CampoNumero("Tempo mínimo do banner (segundos)", segundos) { segundos = it }

        Button(
            onClick = {
                val novo = cfg.copy(
                    minimoPorKm = ExtratorOferta.numero(porKm) ?: return@Button erro(ctx, "Valor por km inválido"),
                    minimoCorrida = ExtratorOferta.numero(minimo) ?: return@Button erro(ctx, "Valor mínimo inválido"),
                    maxKmAtePassageiro = ExtratorOferta.numero(maxBusca) ?: return@Button erro(ctx, "Distância inválida"),
                    minimoPorHora = ExtratorOferta.numero(porHora) ?: return@Button erro(ctx, "Ganho por hora inválido"),
                    notaMinima = ExtratorOferta.numero(notaMinima)?.coerceIn(1.0, 5.0)
                        ?: return@Button erro(ctx, "Nota mínima inválida"),
                    margemAmareloPct = margem.trim().toIntOrNull()?.coerceIn(0, 100)
                        ?: return@Button erro(ctx, "Margem inválida"),
                    segundosBanner = segundos.trim().toIntOrNull()?.coerceIn(1, 30)
                        ?: return@Button erro(ctx, "Tempo do banner inválido"),
                )
                onSalvar(novo)
                Toast.makeText(ctx, "Critérios salvos", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salvar critérios") }

        LinhaSwitch("Contar a busca (até o passageiro) no R$/km e no R$/h", cfg.incluirBuscaNoCalculo) {
            onSalvar(cfg.copy(incluirBuscaNoCalculo = it))
        }
        LinhaSwitch("Aviso por voz", cfg.vozAtiva) { onSalvar(cfg.copy(vozAtiva = it)) }
        LinhaSwitch("Falar também os pontos fortes", cfg.falarPontosFortes) {
            onSalvar(cfg.copy(falarPontosFortes = it))
        }
        Text(
            "Com a voz ligada, o painel fica na tela até a voz terminar de falar o diagnóstico inteiro. " +
                "Desligue \"pontos fortes\" se quiser um aviso mais curto (só o que está ruim ou no limite).",
            fontSize = 13.sp,
        )
    }
}

private fun erro(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

@Composable
private fun CampoNumero(rotulo: String, valor: String, onMudar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onMudar,
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LinhaSwitch(texto: String, marcado: Boolean, onMudar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f))
        Switch(checked = marcado, onCheckedChange = onMudar)
    }
}

// ---------------------------------------------------------------------------
// 3. Destinos bloqueados
// ---------------------------------------------------------------------------
@Composable
private fun CartaoDestinos(destinos: List<String>, onMudar: (List<String>) -> Unit) {
    var novo by remember { mutableStateOf("") }
    Cartao("Destinos bloqueados") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = novo,
                onValueChange = { novo = it },
                label = { Text("Cidade ou bairro") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val t = novo.trim()
                if (t.isNotEmpty() && destinos.none { it.equals(t, ignoreCase = true) }) onMudar(destinos + t)
                novo = ""
            }) { Text("Adicionar") }
        }
        if (destinos.isEmpty()) Text("Nenhum destino bloqueado.")
        destinos.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚫  $d", Modifier.weight(1f), fontSize = 16.sp)
                IconButton(onClick = { onMudar(destinos - d) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover $d")
                }
            }
        }
        Text("Maiúsculas e acentos não importam: \"cocal do sul\" também bloqueia \"Cocal do Sul\".", fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------
// 4. Testar leitura (sem precisar de uma oferta real)
// ---------------------------------------------------------------------------
@Composable
private fun CartaoTeste(servicoAtivo: Boolean) {
    val ctx = LocalContext.current
    var texto by remember { mutableStateOf(EXEMPLO_OFERTA) }
    var resultado by remember { mutableStateOf<String?>(null) }

    Cartao("Testar leitura") {
        Text(
            "Cole aqui um trecho do log (uma informação por linha) ou edite o exemplo, e toque em Testar. " +
                "Com o serviço ativo, você também ouve a voz e vê o banner.",
            fontSize = 13.sp,
        )
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            label = { Text("Texto da oferta") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                // Aceita linhas copiadas do log, que começam com "[03] ".
                val linhas = texto.lines().map { it.replace(Regex("""^\s*\[\d+]\s?"""), "") }
                val oferta = ExtratorOferta.extrair(linhas)
                resultado = buildString {
                    appendLine("Leitura: ${oferta.resumo()}")
                    if (oferta.ehOferta) {
                        val r = Avaliador.avaliar(oferta, Preferencias.carregar(ctx))
                        appendLine("Resultado: ${r.titulo}")
                        r.pontos.forEach { p ->
                            val marca = when (p.cor) { Cor.VERMELHO -> "❌"; Cor.AMARELO -> "⚠️"; Cor.VERDE -> "✅" }
                            appendLine("$marca ${p.texto}")
                        }
                        appendLine("Detalhes: ${r.detalhes}")
                        append("Voz: \"${r.fala}\"")
                    } else {
                        append("Não reconhecido como oferta (precisa de um valor R$ e de uma distância).")
                    }
                }
                val servico = OfertaAccessibilityService.instancia
                if (servicoAtivo && servico != null) {
                    servico.testar(linhas)
                } else {
                    Toast.makeText(ctx, "Ative o serviço de acessibilidade para ouvir a voz e ver o banner", Toast.LENGTH_LONG).show()
                }
            }) { Text("Testar") }
            OutlinedButton(onClick = { texto = EXEMPLO_OFERTA; resultado = null }) { Text("Restaurar exemplo") }
        }
        resultado?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
    }
}

// ---------------------------------------------------------------------------
// 5. Modo diagnóstico (calibração)
// ---------------------------------------------------------------------------
@Composable
private fun CartaoDiagnostico(ligado: Boolean, onAlternar: () -> Unit, onVerLog: () -> Unit) {
    val ctx = LocalContext.current
    Cartao("Modo diagnóstico (calibração)") {
        LinhaSwitch("Gravar o texto lido da tela do Uber", ligado) { onAlternar() }
        Text(
            "Quando ligado, grava num arquivo local TODO o texto que o app lê na tela do Uber " +
                "(mesmo com o monitoramento desligado). Serve para você ver o formato real das ofertas " +
                "e ajustar o arquivo RegrasExtracao.kt.\n" +
                "⚠️ O log pode conter o nome do passageiro. Desligue e apague o log depois de calibrar.",
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onVerLog) { Text("Ver log") }
            OutlinedButton(onClick = { compartilharLog(ctx) }) { Text("Compartilhar") }
            OutlinedButton(onClick = {
                LogDiagnostico.apagar(ctx)
                Toast.makeText(ctx, "Log apagado", Toast.LENGTH_SHORT).show()
            }) { Text("Apagar") }
        }
    }
}

private fun compartilharLog(ctx: Context) {
    val arquivo = LogDiagnostico.arquivo(ctx)
    if (!arquivo.exists() || arquivo.length() == 0L) {
        Toast.makeText(ctx, "O log está vazio", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.arquivos", arquivo)
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(envio, "Compartilhar log"))
}

@Composable
private fun TelaLog(onVoltar: () -> Unit) {
    val ctx = LocalContext.current
    var conteudo by remember { mutableStateOf(LogDiagnostico.ler(ctx)) }
    val rolagem = rememberScrollState()
    BackHandler(onBack = onVoltar)
    LaunchedEffect(conteudo) { rolagem.scrollTo(Int.MAX_VALUE) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onVoltar) { Text("← Voltar") }
            OutlinedButton(onClick = { conteudo = LogDiagnostico.ler(ctx) }) { Text("Atualizar") }
        }
        Text(
            "Tamanho: ${LogDiagnostico.tamanhoKb(ctx)} KB. Os registros mais novos ficam no final. " +
                "Toque e segure para selecionar e copiar.",
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        SelectionContainer(Modifier.weight(1f).verticalScroll(rolagem)) {
            Text(conteudo.ifEmpty { "(log vazio)" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

/** Confere nas configurações do Android se o nosso serviço de acessibilidade está ligado. */
private fun servicoAtivo(ctx: Context): Boolean {
    val esperado = ComponentName(ctx, OfertaAccessibilityService::class.java)
    val lista = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return lista.split(':').any { ComponentName.unflattenFromString(it) == esperado }
}
