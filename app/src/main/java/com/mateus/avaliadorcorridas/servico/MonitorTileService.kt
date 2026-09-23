package com.mateus.avaliadorcorridas.servico

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mateus.avaliadorcorridas.dados.Preferencias

/**
 * Botão "Avaliador" nas Configurações rápidas (puxe a cortina de notificações para baixo).
 * Um toque liga ou desliga o monitoramento, sem abrir o app.
 */
class MonitorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        atualizar()
    }

    override fun onClick() {
        super.onClick()
        val ligado = Preferencias.alternarMonitoramento(this)
        OfertaAccessibilityService.instancia?.falar(
            if (ligado) "Monitoramento ligado" else "Monitoramento desligado",
        )
        atualizar()
    }

    private fun atualizar() {
        val tile = qsTile ?: return
        val ligado = Preferencias.carregar(this).monitorando
        tile.state = if (ligado) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (ligado) "Ligado" else "Desligado"
        tile.updateTile()
    }
}
