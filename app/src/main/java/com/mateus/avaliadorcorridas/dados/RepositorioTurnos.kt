package com.mateus.avaliadorcorridas.dados

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Guarda os turnos num arquivo local (turnos.json, na memória interna do app).
 * Nada sai do celular. A tela observa [turnos] e se atualiza sozinha quando algo muda
 * (por exemplo, quando o serviço registra uma corrida).
 */
object RepositorioTurnos {

    private const val ARQUIVO = "turnos.json"
    private const val TAG = "RepositorioTurnos"

    private val _turnos = MutableStateFlow<List<Turno>>(emptyList())
    val turnos: StateFlow<List<Turno>> = _turnos

    private var carregado = false
    private var ultimoId = 0L

    @Synchronized
    fun iniciar(ctx: Context) {
        if (carregado) return
        carregado = true
        val f = arquivo(ctx)
        if (!f.exists()) return
        try {
            val lista = JSONArray(f.readText())
            _turnos.value = (0 until lista.length()).map { turnoDeJson(lista.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível ler $ARQUIVO", e)
        }
    }

    fun turnoAberto(): Turno? = _turnos.value.firstOrNull { it.aberto }

    fun buscar(id: Long): Turno? = _turnos.value.firstOrNull { it.id == id }

    /** Id único baseado no relógio. */
    @Synchronized
    fun novoId(): Long {
        val agora = System.currentTimeMillis()
        ultimoId = if (agora > ultimoId) agora else ultimoId + 1
        return ultimoId
    }

    /** Adiciona ou substitui (pelo id) e grava no arquivo. */
    @Synchronized
    fun salvar(ctx: Context, t: Turno) {
        val lista = _turnos.value.toMutableList()
        val i = lista.indexOfFirst { it.id == t.id }
        if (i >= 0) lista[i] = t else lista += t
        _turnos.value = lista
        gravar(ctx)
    }

    @Synchronized
    fun excluir(ctx: Context, id: Long) {
        _turnos.value = _turnos.value.filterNot { it.id == id }
        gravar(ctx)
    }

    /** Aplica uma mudança no turno aberto (se houver) e grava. */
    @Synchronized
    fun alterarTurnoAberto(ctx: Context, mudanca: (Turno) -> Turno) {
        val aberto = turnoAberto() ?: return
        salvar(ctx, mudanca(aberto))
    }

    private fun arquivo(ctx: Context) = File(ctx.applicationContext.filesDir, ARQUIVO)

    /** Grava num arquivo temporário e troca, para não corromper se o celular desligar no meio. */
    private fun gravar(ctx: Context) {
        try {
            val json = JSONArray().apply { _turnos.value.forEach { put(turnoParaJson(it)) } }
            val destino = arquivo(ctx)
            val temp = File(destino.parentFile, "$ARQUIVO.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(destino)) {
                destino.writeText(json.toString())
                temp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível gravar $ARQUIVO", e)
        }
    }

    // ---- JSON ----

    private fun turnoParaJson(t: Turno) = JSONObject().apply {
        put("id", t.id)
        put("inicio", t.inicio)
        t.fim?.let { put("fim", it) }
        put("odometroInicial", t.odometroInicial)
        t.odometroFinal?.let { put("odometroFinal", it) }
        put("consumoKmL", t.consumoKmL)
        put("precoLitro", t.precoLitro)
        put("manutencaoPorKm", t.manutencaoPorKm)
        put("ofertasVistas", t.ofertasVistas)
        put("corridas", JSONArray().apply { t.corridas.forEach { put(corridaParaJson(it)) } })
    }

    private fun corridaParaJson(c: Corrida) = JSONObject().apply {
        put("id", c.id)
        put("hora", c.hora)
        put("valor", c.valor)
        put("kmBusca", c.kmBusca)
        put("kmViagem", c.kmViagem)
        put("minBusca", c.minBusca)
        put("minViagem", c.minViagem)
        c.nota?.let { put("nota", it) }
        put("manual", c.manual)
    }

    private fun turnoDeJson(o: JSONObject): Turno {
        val corridas = o.optJSONArray("corridas") ?: JSONArray()
        return Turno(
            id = o.getLong("id"),
            inicio = o.getLong("inicio"),
            fim = if (o.has("fim")) o.getLong("fim") else null,
            odometroInicial = o.getDouble("odometroInicial"),
            odometroFinal = if (o.has("odometroFinal")) o.getDouble("odometroFinal") else null,
            consumoKmL = o.optDouble("consumoKmL", 11.0),
            precoLitro = o.optDouble("precoLitro", 6.29),
            // Turnos antigos (antes da manutenção existir) ficam com 0, para não mudar o passado.
            manutencaoPorKm = o.optDouble("manutencaoPorKm", 0.0),
            ofertasVistas = o.optInt("ofertasVistas", 0),
            corridas = (0 until corridas.length()).map { corridaDeJson(corridas.getJSONObject(it)) },
        )
    }

    private fun corridaDeJson(o: JSONObject) = Corrida(
        id = o.getLong("id"),
        hora = o.getLong("hora"),
        valor = o.getDouble("valor"),
        kmBusca = o.optDouble("kmBusca", 0.0),
        kmViagem = o.optDouble("kmViagem", 0.0),
        minBusca = o.optInt("minBusca", 0),
        minViagem = o.optInt("minViagem", 0),
        nota = if (o.has("nota")) o.getDouble("nota") else null,
        manual = o.optBoolean("manual", false),
    )
}
