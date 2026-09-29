package com.mateus.avaliadorcorridas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateus.avaliadorcorridas.regras.Calculos
import com.mateus.avaliadorcorridas.regras.ItemManutencao

/** Uma linha editável da calculadora (os campos são texto enquanto a pessoa digita). */
private data class LinhaItem(val nome: String, val custo: String, val km: String) {
    fun paraItem(): ItemManutencao? {
        val c = lerNumero(custo) ?: return null
        val k = lerNumero(km) ?: return null
        return ItemManutencao(nome, c, k)
    }
}

/**
 * Janela "Calcular manutenção": a pessoa lista os gastos do carro (custo e quantos km cada um dura),
 * pode editar, apagar e adicionar itens, e o app mostra o total por km. Vem com valores de exemplo.
 */
@Composable
fun DialogoManutencao(onCancelar: () -> Unit, onUsar: (Double) -> Unit) {
    val linhas = remember {
        mutableStateListOf<LinhaItem>().apply {
            Calculos.ITENS_MANUTENCAO_EXEMPLO.forEach {
                add(LinhaItem(it.nome, numero(it.custo, 0), numero(it.kmDuracao, 0)))
            }
        }
    }
    val total = Calculos.manutencaoPorKm(linhas.mapNotNull { it.paraItem() })

    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Cores.containerAlto,
        title = { Text("Calcular manutenção", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Para cada gasto, informe quanto custa e quantos km dura. Os valores já preenchidos são " +
                        "exemplos para um carro 1.0: troque pelos seus. Gasto mensal (lavagem, seguro...)? " +
                        "Use os km que você roda por mês.",
                    color = Cores.textoVariante, fontSize = 13.sp,
                )
                linhas.forEachIndexed { i, linha ->
                    Column(
                        Modifier.fillMaxWidth().background(Cores.container, RoundedCornerShape(12.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = linha.nome,
                                onValueChange = { linhas[i] = linha.copy(nome = it) },
                                label = { Text("Item") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { linhas.removeAt(i) }) {
                                Icon(Icons.Filled.Delete, "Remover item", tint = Cores.vermelho)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CampoNumero("Custo (R$)", linha.custo, Modifier.weight(1f)) { linhas[i] = linha.copy(custo = it) }
                            CampoNumero("Dura (km)", linha.km, Modifier.weight(1f)) { linhas[i] = linha.copy(km = it) }
                        }
                        val porKm = linha.paraItem()?.porKm
                        Text(
                            porKm?.let { "= ${String.format(BR, "R$ %.3f", it)} por km" } ?: "Preencha custo e km",
                            color = if (porKm != null) Cores.verde else Cores.contorno, fontSize = 12.sp,
                        )
                    }
                }
                TextButton(onClick = { linhas.add(LinhaItem("", "", "")) }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Adicionar item")
                }
                Row(
                    Modifier.fillMaxWidth().background(Cores.container, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Total", color = Cores.texto, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${reais(total)} por km", color = Cores.amarelo, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                val faixa = when {
                    total < 0.15 -> "Abaixo do comum (R$ 0,20 a 0,30): confira se não esqueceu algum gasto."
                    total > 0.40 -> "Acima do comum (R$ 0,20 a 0,30): confira os valores."
                    else -> "Dentro do comum para carro 1.0 (R$ 0,20 a 0,30 por km)."
                }
                Text(faixa, color = Cores.textoVariante, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onUsar(total) }, enabled = total > 0) { Text("Usar ${reais(total)}/km") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
