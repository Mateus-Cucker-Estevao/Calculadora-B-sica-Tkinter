package com.mateus.avaliadorcorridas.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mateus.avaliadorcorridas.dados.Configuracao
import com.mateus.avaliadorcorridas.dados.LogDiagnostico
import com.mateus.avaliadorcorridas.dados.Preferencias
import com.mateus.avaliadorcorridas.regras.Avaliador
import com.mateus.avaliadorcorridas.regras.Calculos
import com.mateus.avaliadorcorridas.regras.Cor
import com.mateus.avaliadorcorridas.regras.ExtratorOferta
import com.mateus.avaliadorcorridas.servico.BannerSobreposto
import com.mateus.avaliadorcorridas.servico.Falador

private val EXEMPLO_OFERTA = """
    UberX
    R$ 18,50
    ★ 4,95
    6 min (2,1 km) de distância
    Rua Henrique Lage, Centro, Criciúma
    Viagem de 18 min (8,4 km)
    Rua São João, Cocal do Sul - SC
    Aceitar
""".trimIndent()

@Composable
fun TelaParametros(onVerLog: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(Preferencias.carregar(ctx)) }
    val salvar: (Configuracao) -> Unit = { novo ->
        cfg = novo
        Preferencias.salvar(ctx, novo)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CartaoVeiculo(cfg, salvar)
        CartaoCriterios(cfg, salvar)
        CartaoDestinos(cfg.destinosBloqueados) { salvar(cfg.copy(destinosBloqueados = it)) }
        CartaoAvisoVoz(cfg, salvar)
        CartaoPermissao()
        CartaoTeste()
        CartaoDiagnostico(cfg.modoDiagnostico, onAlternar = { salvar(cfg.copy(modoDiagnostico = it)) }, onVerLog = onVerLog)
        Text(
            "O app só lê a tela e avisa. Nunca toca em nada. Nenhum dado sai do celular.",
            color = Cores.contorno, fontSize = 12.sp,
        )
        Spacer(Modifier.padding(8.dp))
    }
}

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

// ---------------------------------------------------------------------------

@Composable
private fun CartaoVeiculo(cfg: Configuracao, onSalvar: (Configuracao) -> Unit) {
    val ctx = LocalContext.current
    var consumo by remember { mutableStateOf(numero(cfg.consumoKmL, 1)) }
    var preco by remember { mutableStateOf(numero(cfg.precoLitro, 2)) }
    val custoKm = lerNumero(consumo)?.let { c -> lerNumero(preco)?.let { p -> Calculos.custoPorKm(c, p) } }

    Cartao {
        TituloSecao("⛽ Veículo e combustível")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumero("Consumo (km/l)", consumo, Modifier.weight(1f)) { consumo = it }
            CampoNumero("Combustível (R$/L)", preco, Modifier.weight(1f)) { preco = it }
        }
        Text(
            "Custo de combustível: ${custoKm?.let { reais(it) } ?: "–"} por km. É com ele que o app calcula o " +
                "lucro real de cada oferta e dos turnos.",
            color = Cores.textoVariante, fontSize = 13.sp,
        )
        Button(onClick = {
            val c = lerNumero(consumo)
            val p = lerNumero(preco)
            if (c == null || c <= 0 || p == null || p < 0) {
                toast(ctx, "Confira o consumo e o preço")
            } else {
                onSalvar(cfg.copy(consumoKmL = c, precoLitro = p))
                toast(ctx, "Veículo salvo. Vale para os próximos turnos.")
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Salvar veículo") }
    }
}

@Composable
private fun CartaoCriterios(cfg: Configuracao, onSalvar: (Configuracao) -> Unit) {
    val ctx = LocalContext.current
    var porKm by remember { mutableStateOf(numero(cfg.minimoPorKm)) }
    var minimo by remember { mutableStateOf(numero(cfg.minimoCorrida)) }
    var lucroMin by remember { mutableStateOf(numero(cfg.lucroMinimoCorrida)) }
    var maxBusca by remember { mutableStateOf(numero(cfg.maxKmAtePassageiro, 1)) }
    var porHora by remember { mutableStateOf(numero(cfg.minimoPorHora, 0)) }
    var nota by remember { mutableStateOf(numero(cfg.notaMinima)) }
    var margem by remember { mutableStateOf(cfg.margemAmareloPct.toString()) }

    Cartao {
        TituloSecao("🎯 Critérios das ofertas")
        CampoNumero("Valor mínimo por km (R$/km)", porKm) { porKm = it }
        CampoNumero("Valor mínimo da corrida (R$)", minimo) { minimo = it }
        CampoNumero("Lucro mínimo da corrida (R$, já sem combustível)", lucroMin) { lucroMin = it }
        CampoNumero("Distância máxima até o passageiro (km)", maxBusca) { maxBusca = it }
        CampoNumero("Ganho mínimo por hora (R$/h)", porHora) { porHora = it }
        CampoNumero("Nota mínima do passageiro (abaixo fica amarelo)", nota) { nota = it }
        CampoNumero("Margem do amarelo \"no limite\" (%)", margem) { margem = it }
        Button(onClick = {
            val novo = cfg.copy(
                minimoPorKm = lerNumero(porKm) ?: return@Button toast(ctx, "Valor por km inválido"),
                minimoCorrida = lerNumero(minimo) ?: return@Button toast(ctx, "Valor mínimo inválido"),
                lucroMinimoCorrida = lerNumero(lucroMin) ?: return@Button toast(ctx, "Lucro mínimo inválido"),
                maxKmAtePassageiro = lerNumero(maxBusca) ?: return@Button toast(ctx, "Distância inválida"),
                minimoPorHora = lerNumero(porHora) ?: return@Button toast(ctx, "Ganho por hora inválido"),
                notaMinima = lerNumero(nota)?.coerceIn(1.0, 5.0) ?: return@Button toast(ctx, "Nota inválida"),
                margemAmareloPct = margem.trim().toIntOrNull()?.coerceIn(0, 100) ?: return@Button toast(ctx, "Margem inválida"),
            )
            onSalvar(novo)
            toast(ctx, "Critérios salvos")
        }, modifier = Modifier.fillMaxWidth()) { Text("Salvar critérios") }
        LinhaSwitch("Contar a busca (até o passageiro) no R$/km e no R$/h", cfg.incluirBuscaNoCalculo) {
            onSalvar(cfg.copy(incluirBuscaNoCalculo = it))
        }
    }
}

@Composable
private fun CartaoDestinos(destinos: List<String>, onMudar: (List<String>) -> Unit) {
    var novo by remember { mutableStateOf("") }
    Cartao {
        TituloSecao("🚫 Destinos bloqueados")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = novo, onValueChange = { novo = it },
                label = { Text("Cidade ou bairro") }, singleLine = true, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val t = novo.trim()
                if (t.isNotEmpty() && destinos.none { it.equals(t, ignoreCase = true) }) onMudar(destinos + t)
                novo = ""
            }) { Text("Adicionar") }
        }
        if (destinos.isEmpty()) Text("Nenhum destino bloqueado.", color = Cores.contorno)
        destinos.forEach { d ->
            Row(
                Modifier.fillMaxWidth().background(Cores.containerAlto, RoundedCornerShape(10.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(d, Modifier.weight(1f), color = Cores.texto, fontSize = 15.sp)
                IconButton(onClick = { onMudar(destinos - d) }) { Icon(Icons.Filled.Delete, "Remover $d", tint = Cores.vermelho) }
            }
        }
        Text("Maiúsculas e acentos não importam.", color = Cores.contorno, fontSize = 12.sp)
    }
}

@Composable
private fun CartaoAvisoVoz(cfg: Configuracao, onSalvar: (Configuracao) -> Unit) {
    val ctx = LocalContext.current
    var segundos by remember { mutableStateOf(cfg.segundosBanner.toString()) }
    Cartao {
        TituloSecao("🔊 Aviso e voz")
        LinhaSwitch("Aviso por voz", cfg.vozAtiva) { onSalvar(cfg.copy(vozAtiva = it)) }
        LinhaSwitch("Falar também os pontos fortes", cfg.falarPontosFortes) { onSalvar(cfg.copy(falarPontosFortes = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CampoNumero("Tempo do aviso no botão Testar (s)", segundos, Modifier.weight(1f)) { segundos = it }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val s = segundos.trim().toIntOrNull()?.coerceIn(1, 30)
                if (s == null) toast(ctx, "Tempo inválido") else { onSalvar(cfg.copy(segundosBanner = s)); toast(ctx, "Salvo") }
            }) { Text("Salvar") }
        }
        Text(
            "Nas ofertas reais, o aviso fica na tela enquanto a oferta estiver aparecendo e some quando ela sai.",
            color = Cores.contorno, fontSize = 12.sp,
        )
    }
}

@Composable
private fun CartaoPermissao() {
    val ctx = LocalContext.current
    var permitido by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permitido = Settings.canDrawOverlays(ctx) }
    Cartao {
        TituloSecao("🪟 Aviso por cima do Uber")
        if (permitido) {
            Text("✅ Permissão \"Sobrepor a outros apps\" concedida", color = Cores.verde, fontSize = 14.sp)
        } else {
            Text("❌ Sem permissão: o aviso não aparece (a voz funciona)", color = Cores.vermelho, fontSize = 14.sp)
            Button(onClick = { abrirPermissaoSobreposicao(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Permitir aviso na tela") }
        }
        Text(
            "A leitura das ofertas começa quando você inicia um turno: o Android pergunta se o app pode " +
                "gravar/compartilhar a tela. Escolha \"Tela inteira\" e toque em Iniciar.",
            color = Cores.contorno, fontSize = 12.sp,
        )
    }
}

@Composable
private fun CartaoTeste() {
    val ctx = LocalContext.current
    var texto by remember { mutableStateOf(EXEMPLO_OFERTA) }
    var resultado by remember { mutableStateOf<String?>(null) }
    // Voz e aviso próprios desta tela (o turno pode não estar aberto).
    val falador = remember { Falador(ctx) }
    val banner = remember { BannerSobreposto(ctx) }
    DisposableEffect(Unit) {
        onDispose {
            banner.remover()
            falador.desligar()
        }
    }

    Cartao {
        TituloSecao("🧪 Testar leitura")
        Text(
            "Cole aqui um trecho do log (uma informação por linha) ou edite o exemplo e toque em Testar. " +
                "Você ouve a voz e vê o aviso, como numa oferta real.",
            color = Cores.textoVariante, fontSize = 13.sp,
        )
        OutlinedTextField(
            value = texto, onValueChange = { texto = it },
            label = { Text("Texto da oferta") }, minLines = 6, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val cfg = Preferencias.carregar(ctx)
                // Aceita linhas copiadas do log, que começam com "[03] ".
                val linhas = texto.lines().map { it.replace(Regex("""^\s*\[\d+]\s?"""), "") }
                val oferta = ExtratorOferta.extrair(linhas)
                if (!oferta.ehOferta) {
                    resultado = "Leitura: ${oferta.resumo()}\nNão reconhecido como oferta (precisa de um valor R$ e de uma distância)."
                    if (cfg.vozAtiva) falador.falar("Nenhuma oferta encontrada no texto")
                    return@Button
                }
                val r = Avaliador.avaliar(oferta, cfg)
                resultado = buildString {
                    appendLine("Leitura: ${oferta.resumo()}")
                    appendLine("Resultado: ${r.titulo}")
                    r.pontos.forEach { p ->
                        val marca = when (p.cor) { Cor.VERMELHO -> "❌"; Cor.AMARELO -> "⚠️"; Cor.VERDE -> "✅" }
                        appendLine("$marca ${p.texto}")
                    }
                    append("Voz: \"${r.fala}\"")
                }
                banner.mostrar(r, BannerSobreposto.Duracao.Fixa(cfg.segundosBanner))
                if (cfg.vozAtiva) falador.falar(r.fala)
                if (!Settings.canDrawOverlays(ctx)) toast(ctx, "Permita o aviso na tela para ver o aviso")
            }) { Text("Testar") }
            OutlinedButton(onClick = { texto = EXEMPLO_OFERTA; resultado = null }) { Text("Restaurar exemplo") }
        }
        resultado?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Cores.texto) }
    }
}

@Composable
private fun CartaoDiagnostico(ligado: Boolean, onAlternar: (Boolean) -> Unit, onVerLog: () -> Unit) {
    val ctx = LocalContext.current
    Cartao {
        TituloSecao("🩺 Modo diagnóstico (calibração)")
        LinhaSwitch("Gravar o texto lido da tela durante o turno", ligado, onAlternar)
        Text(
            "Grava num arquivo local o texto que o app lê nas ofertas e nas telas de corrida. Serve para ajustar " +
                "o arquivo RegrasExtracao.kt (por exemplo, as palavras que indicam corrida aceita).\n" +
                "⚠️ O log pode conter o nome do passageiro. Desligue e apague depois de calibrar.",
            color = Cores.textoVariante, fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onVerLog) { Text("Ver log") }
            OutlinedButton(onClick = { compartilharLog(ctx) }) { Text("Compartilhar") }
            OutlinedButton(onClick = { LogDiagnostico.apagar(ctx); toast(ctx, "Log apagado") }) { Text("Apagar") }
        }
    }
}

private fun compartilharLog(ctx: Context) {
    val arquivo = LogDiagnostico.arquivo(ctx)
    if (!arquivo.exists() || arquivo.length() == 0L) {
        toast(ctx, "O log está vazio")
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
fun TelaLog(onVoltar: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var conteudo by remember { mutableStateOf(LogDiagnostico.ler(ctx)) }
    val rolagem = rememberScrollState()
    BackHandler(onBack = onVoltar)
    LaunchedEffect(conteudo) { rolagem.scrollTo(Int.MAX_VALUE) }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onVoltar) { Text("← Voltar") }
            OutlinedButton(onClick = { conteudo = LogDiagnostico.ler(ctx) }) { Text("Atualizar") }
        }
        Text(
            "Tamanho: ${LogDiagnostico.tamanhoKb(ctx)} KB. Os registros mais novos ficam no final. " +
                "Toque e segure para selecionar e copiar.",
            fontSize = 12.sp, color = Cores.contorno, modifier = Modifier.padding(vertical = 8.dp),
        )
        SelectionContainer(Modifier.weight(1f).verticalScroll(rolagem)) {
            Text(conteudo.ifEmpty { "(log vazio)" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Cores.texto)
        }
    }
}
