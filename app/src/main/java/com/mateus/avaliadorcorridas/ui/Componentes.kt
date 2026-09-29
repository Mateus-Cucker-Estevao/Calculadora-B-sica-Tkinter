package com.mateus.avaliadorcorridas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateus.avaliadorcorridas.regras.ExtratorOferta
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val BR: Locale = Locale.forLanguageTag("pt-BR")
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm", BR)

// ---------------------------------------------------------------------------
// Formatação
// ---------------------------------------------------------------------------

fun reais(v: Double): String = String.format(BR, "R$ %,.2f", v)
fun km(v: Double): String = String.format(BR, "%,.1f km", v)
fun numero(v: Double, casas: Int = 2): String = String.format(BR, "%.${casas}f", v)
fun porcentagem(v: Double): String = String.format(BR, "%.0f%%", v * 100)

/** 4.13 h → "4h08" */
fun horas(h: Double): String {
    val minutos = (h * 60).toLong()
    return if (minutos < 60) "${minutos}min" else String.format(BR, "%dh%02d", minutos / 60, minutos % 60)
}

fun hora(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatoHora)
fun dia(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
fun mes(epochMs: Long): YearMonth = YearMonth.from(dia(epochMs))

/** "Setembro 2026" */
fun nomeMes(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL, BR).replaceFirstChar { it.titlecase(BR) } + " " + m.year

/** "Dom, 28 de set." */
fun nomeDia(d: LocalDate): String {
    val semana = d.dayOfWeek.getDisplayName(TextStyle.SHORT, BR).replaceFirstChar { it.titlecase(BR) }.trimEnd('.')
    val mesCurto = d.month.getDisplayName(TextStyle.SHORT, BR).trimEnd('.')
    return "$semana, ${d.dayOfMonth} de $mesCurto."
}

fun lerNumero(s: String): Double? = ExtratorOferta.numero(s.trim())

// ---------------------------------------------------------------------------
// Componentes
// ---------------------------------------------------------------------------

/** Cartão escuro arredondado (surface-container do modelo). */
@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    cor: Color = Cores.container,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(cor, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = conteudo,
    )
}

/** Título pequeno em caixa alta, como "LUCRO REAL LÍQUIDO". */
@Composable
fun Rotulo(texto: String, cor: Color = Cores.contorno) {
    Text(texto.uppercase(BR), color = cor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
}

@Composable
fun TituloSecao(texto: String) {
    Text(texto, color = Cores.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun CampoNumero(rotulo: String, valor: String, modifier: Modifier = Modifier, onMudar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = onMudar,
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun LinhaSwitch(texto: String, marcado: Boolean, onMudar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(texto, Modifier.weight(1f), color = Cores.texto)
        Switch(checked = marcado, onCheckedChange = onMudar)
    }
}

/** Janela pedindo um número (ex.: km do odômetro). [validar] devolve uma mensagem de erro ou null. */
@Composable
fun DialogoNumero(
    titulo: String,
    rotulo: String,
    textoConfirmar: String,
    explicacao: String? = null,
    valorInicial: String = "",
    validar: (Double) -> String? = { null },
    onCancelar: () -> Unit,
    onConfirmar: (Double) -> Unit,
) {
    var texto by remember { mutableStateOf(valorInicial) }
    var erro by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Cores.containerAlto,
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                explicacao?.let { Text(it, color = Cores.textoVariante, fontSize = 14.sp) }
                CampoNumero(rotulo, texto) { texto = it; erro = null }
                erro?.let { Text(it, color = Cores.vermelho, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = lerNumero(texto)
                val problema = if (v == null) "Digite um número válido" else validar(v)
                if (problema != null) erro = problema else onConfirmar(v!!)
            }) { Text(textoConfirmar) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoConfirmar(titulo: String, mensagem: String, textoConfirmar: String, onCancelar: () -> Unit, onConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Cores.containerAlto,
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = { Text(mensagem, color = Cores.textoVariante) },
        confirmButton = { TextButton(onClick = onConfirmar) { Text(textoConfirmar, color = Cores.vermelho) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
