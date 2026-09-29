package com.mateus.avaliadorcorridas.ui

import android.graphics.Color as CorAndroid
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateus.avaliadorcorridas.dados.RepositorioTurnos
import com.mateus.avaliadorcorridas.servico.MonitorService
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(CorAndroid.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(CorAndroid.TRANSPARENT),
        )
        RepositorioTurnos.iniciar(this)
        setContent { TemaAvaliador { App() } }
    }

    // Enquanto a tela do app está aberta, a leitura da tela fica pausada
    // (para não confundir o texto do próprio app com uma oferta).
    override fun onResume() {
        super.onResume()
        MonitorService.appAberto = true
    }

    override fun onPause() {
        MonitorService.appAberto = false
        super.onPause()
    }
}

@Composable
private fun App() {
    var aba by rememberSaveable { mutableIntStateOf(0) }
    var mesSelecionado by remember { mutableStateOf(YearMonth.now()) }
    var turnoDetalhe by rememberSaveable { mutableStateOf<Long?>(null) }
    var mostrandoLog by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = turnoDetalhe != null) { turnoDetalhe = null }

    val titulo = when {
        mostrandoLog -> "Log de diagnóstico"
        turnoDetalhe != null -> "Detalhes do turno"
        aba == 0 -> "Turnos"
        else -> "Parâmetros"
    }

    Scaffold(
        containerColor = Cores.fundo,
        topBar = { BarraTopo(titulo) },
        bottomBar = {
            if (turnoDetalhe == null && !mostrandoLog) {
                NavigationBar(containerColor = Cores.containerBaixo) {
                    ItemNavegacao(aba == 0, "Turnos", { Icon(Icons.Filled.DateRange, null) }) { aba = 0 }
                    ItemNavegacao(aba == 1, "Parâmetros", { Icon(Icons.Filled.Settings, null) }) { aba = 1 }
                }
            }
        },
    ) { espaco ->
        val m = Modifier.padding(espaco)
        when {
            mostrandoLog -> TelaLog(onVoltar = { mostrandoLog = false }, modifier = m)
            turnoDetalhe != null -> TelaDetalheTurno(turnoDetalhe!!, onVoltar = { turnoDetalhe = null }, modifier = m)
            aba == 0 -> TelaTurnos(
                mesSelecionado = mesSelecionado,
                onMudarMes = { mesSelecionado = it },
                onAbrirTurno = { turnoDetalhe = it },
                modifier = m,
            )
            else -> TelaParametros(onVerLog = { mostrandoLog = true }, modifier = m)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ItemNavegacao(
    selecionado: Boolean,
    rotulo: String,
    icone: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selecionado,
        onClick = onClick,
        icon = icone,
        label = { Text(rotulo, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Cores.primaria,
            selectedTextColor = Cores.primaria,
            indicatorColor = Cores.primaria.copy(alpha = 0.15f),
            unselectedIconColor = Cores.textoVariante,
            unselectedTextColor = Cores.textoVariante,
        ),
    )
}

@Composable
private fun BarraTopo(titulo: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Cores.superficie)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(Cores.containerAlto, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("🚕", fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
        Text(titulo, color = Cores.texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
