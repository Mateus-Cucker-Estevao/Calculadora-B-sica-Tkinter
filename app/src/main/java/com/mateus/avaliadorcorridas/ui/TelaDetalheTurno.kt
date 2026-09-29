package com.mateus.avaliadorcorridas.ui

import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateus.avaliadorcorridas.dados.Corrida
import com.mateus.avaliadorcorridas.dados.RepositorioTurnos
import com.mateus.avaliadorcorridas.dados.Turno
import com.mateus.avaliadorcorridas.regras.Calculos

/** Detalhes de um turno: números, dados editáveis, corridas (editar/excluir/adicionar) e excluir turno. */
@Composable
fun TelaDetalheTurno(id: Long, onVoltar: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val turnos by RepositorioTurnos.turnos.collectAsState()
    val turno = turnos.firstOrNull { it.id == id }
    LaunchedEffect(turno == null) { if (turno == null) onVoltar() }
    if (turno == null) return

    val r = Calculos.resumo(turno, System.currentTimeMillis())
    var editandoCorrida by remember { mutableStateOf<Corrida?>(null) }
    var adicionandoCorrida by remember { mutableStateOf(false) }
    var excluindoCorrida by remember { mutableStateOf<Corrida?>(null) }
    var excluindoTurno by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Cores.texto) }
            Column {
                Text(nomeDia(dia(turno.inicio)), color = Cores.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${hora(turno.inicio)} – ${turno.fim?.let { hora(it) } ?: "em andamento"} · ${horas(r.horas)}",
                    color = Cores.contorno, fontSize = 13.sp,
                )
            }
        }

        // ---- Números do turno ----
        Cartao {
            Rotulo("Lucro real líquido")
            Text(reais(r.lucro), color = corLucro(r.lucro), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            LinhaMetricas(
                "Faturamento" to reais(r.faturamento),
                "Combustível" to reais(r.custoCombustivel),
                "Manutenção" to reais(r.custoManutencao),
            )
            LinhaMetricas(
                "Km total" to (r.kmTotal?.let { km(it) } ?: "–"),
                "Km corridas" to km(r.kmCorridas),
                "Km deslocamento" to (r.kmDeslocamento?.let { km(it) } ?: "–"),
            )
            LinhaMetricas(
                "Lucro/h" to (r.lucroPorHora?.let { reais(it) } ?: "–"),
                "Aproveitamento" to (r.aproveitamento?.let { porcentagem(it) } ?: "–"),
                "Corridas" to "${r.corridas} de ${turno.ofertasVistas}",
            )
            if (turno.aberto) {
                Text(
                    "Turno aberto: combustível e manutenção estão estimados só pelos km das corridas. " +
                        "O valor real sai quando você finalizar com o km do odômetro.",
                    color = Cores.contorno, fontSize = 12.sp,
                )
            }
        }

        // ---- Dados editáveis ----
        CartaoDadosTurno(turno)

        // ---- Corridas ----
        Cartao {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TituloSecao("Corridas")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { adicionandoCorrida = true }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Adicionar")
                }
            }
            if (turno.corridas.isEmpty()) {
                Text(
                    "Nenhuma corrida registrada. As corridas aceitas entram aqui sozinhas; " +
                        "se alguma não entrar, adicione à mão.",
                    color = Cores.contorno, fontSize = 13.sp,
                )
            }
            turno.corridas.sortedBy { it.hora }.forEach { c ->
                LinhaCorrida(
                    c, turno,
                    onEditar = { editandoCorrida = c },
                    onExcluir = { excluindoCorrida = c },
                )
            }
        }

        OutlinedButton(onClick = { excluindoTurno = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Delete, null, tint = Cores.vermelho)
            Spacer(Modifier.width(6.dp))
            Text("Excluir turno", color = Cores.vermelho)
        }
        Spacer(Modifier.padding(8.dp))
    }

    // ---- Janelas ----

    if (adicionandoCorrida || editandoCorrida != null) {
        val original = editandoCorrida
        DialogoCorrida(
            corrida = original,
            onCancelar = { adicionandoCorrida = false; editandoCorrida = null },
        ) { valor, kmBusca, kmViagem ->
            val nova = original?.copy(valor = valor, kmBusca = kmBusca, kmViagem = kmViagem, manual = true)
                ?: Corrida(
                    id = RepositorioTurnos.novoId(),
                    hora = turno.fim ?: System.currentTimeMillis(),
                    valor = valor, kmBusca = kmBusca, kmViagem = kmViagem, manual = true,
                )
            val lista = if (original == null) turno.corridas + nova else turno.corridas.map { if (it.id == nova.id) nova else it }
            RepositorioTurnos.salvar(ctx, turno.copy(corridas = lista))
            adicionandoCorrida = false
            editandoCorrida = null
        }
    }

    excluindoCorrida?.let { c ->
        DialogoConfirmar(
            titulo = "Excluir corrida",
            mensagem = "Excluir a corrida de ${reais(c.valor)} (${hora(c.hora)})?",
            textoConfirmar = "Excluir",
            onCancelar = { excluindoCorrida = null },
        ) {
            RepositorioTurnos.salvar(ctx, turno.copy(corridas = turno.corridas.filterNot { it.id == c.id }))
            excluindoCorrida = null
        }
    }

    if (excluindoTurno) {
        DialogoConfirmar(
            titulo = "Excluir turno",
            mensagem = "Excluir este turno e todas as ${turno.corridas.size} corridas dele? Não dá para desfazer.",
            textoConfirmar = "Excluir",
            onCancelar = { excluindoTurno = false },
        ) {
            if (turno.aberto) pararMonitor(ctx)
            RepositorioTurnos.excluir(ctx, turno.id)
            excluindoTurno = false
            Toast.makeText(ctx, "Turno excluído", Toast.LENGTH_SHORT).show()
            onVoltar()
        }
    }
}

@Composable
private fun LinhaMetricas(vararg itens: Pair<String, String>) {
    Row(
        Modifier.fillMaxWidth().background(Cores.containerAlto, RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        itens.forEach { (rotulo, valor) -> Metrica(rotulo, valor, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun CartaoDadosTurno(turno: Turno) {
    val ctx = LocalContext.current
    var odoInicial by remember(turno.id) { mutableStateOf(numero(turno.odometroInicial, 1)) }
    var odoFinal by remember(turno.id) { mutableStateOf(turno.odometroFinal?.let { numero(it, 1) } ?: "") }
    var consumo by remember(turno.id) { mutableStateOf(numero(turno.consumoKmL, 1)) }
    var preco by remember(turno.id) { mutableStateOf(numero(turno.precoLitro, 2)) }
    var manutencao by remember(turno.id) { mutableStateOf(numero(turno.manutencaoPorKm, 2)) }

    Cartao {
        TituloSecao("Dados do turno")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumero("Odômetro inicial", odoInicial, Modifier.weight(1f)) { odoInicial = it }
            if (!turno.aberto) CampoNumero("Odômetro final", odoFinal, Modifier.weight(1f)) { odoFinal = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumero("Consumo (km/l)", consumo, Modifier.weight(1f)) { consumo = it }
            CampoNumero("Combustível (R$/L)", preco, Modifier.weight(1f)) { preco = it }
        }
        CampoNumero("Manutenção (R$ por km)", manutencao) { manutencao = it }
        Button(onClick = {
            val ini = lerNumero(odoInicial)
            val fim = if (turno.aberto) null else lerNumero(odoFinal)
            val c = lerNumero(consumo)
            val p = lerNumero(preco)
            val m = lerNumero(manutencao)
            val erro = when {
                ini == null || ini < 0 -> "Odômetro inicial inválido"
                !turno.aberto && (fim == null || fim < ini) -> "O odômetro final precisa ser maior ou igual ao inicial"
                c == null || c <= 0 -> "Consumo inválido"
                p == null || p < 0 -> "Preço do combustível inválido"
                m == null || m < 0 -> "Manutenção inválida"
                else -> null
            }
            if (erro != null) {
                Toast.makeText(ctx, erro, Toast.LENGTH_LONG).show()
            } else {
                RepositorioTurnos.salvar(
                    ctx,
                    turno.copy(
                        odometroInicial = ini!!, odometroFinal = fim ?: turno.odometroFinal,
                        consumoKmL = c!!, precoLitro = p!!, manutencaoPorKm = m!!,
                    ),
                )
                Toast.makeText(ctx, "Turno atualizado", Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Salvar alterações") }
    }
}

@Composable
private fun LinhaCorrida(c: Corrida, turno: Turno, onEditar: () -> Unit, onExcluir: () -> Unit) {
    val lucro = c.valor - Calculos.custoRodar(c.km, turno.consumoKmL, turno.precoLitro, turno.manutencaoPorKm)
    Row(
        Modifier.fillMaxWidth().background(Cores.containerAlto, RoundedCornerShape(12.dp)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                "${hora(c.hora)} · ${reais(c.valor)}${if (c.manual) " ✎" else ""}",
                color = Cores.texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${km(c.km)} (busca ${numero(c.kmBusca, 1)} + viagem ${numero(c.kmViagem, 1)}) · " +
                    "${reais(c.valor / c.km.coerceAtLeast(0.1))}/km",
                color = Cores.contorno, fontSize = 12.sp,
            )
            Text("lucro ${reais(lucro)}", color = corLucro(lucro), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onEditar) { Icon(Icons.Filled.Edit, "Editar", tint = Cores.textoVariante) }
        IconButton(onClick = onExcluir) { Icon(Icons.Filled.Delete, "Excluir", tint = Cores.vermelho) }
    }
}

@Composable
private fun DialogoCorrida(
    corrida: Corrida?,
    onCancelar: () -> Unit,
    onSalvar: (valor: Double, kmBusca: Double, kmViagem: Double) -> Unit,
) {
    var valor by remember { mutableStateOf(corrida?.let { numero(it.valor) } ?: "") }
    var busca by remember { mutableStateOf(corrida?.let { numero(it.kmBusca, 1) } ?: "") }
    var viagem by remember { mutableStateOf(corrida?.let { numero(it.kmViagem, 1) } ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Cores.containerAlto,
        title = { Text(if (corrida == null) "Adicionar corrida" else "Editar corrida", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoNumero("Valor da corrida (R$)", valor) { valor = it; erro = null }
                CampoNumero("Km até o passageiro", busca) { busca = it; erro = null }
                CampoNumero("Km da viagem", viagem) { viagem = it; erro = null }
                erro?.let { Text(it, color = Cores.vermelho, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = lerNumero(valor)
                val b = if (busca.isBlank()) 0.0 else lerNumero(busca)
                val vg = if (viagem.isBlank()) 0.0 else lerNumero(viagem)
                if (v == null || v < 0 || b == null || b < 0 || vg == null || vg < 0) erro = "Confira os números" else onSalvar(v, b, vg)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

