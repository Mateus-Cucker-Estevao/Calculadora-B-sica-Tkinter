package com.mateus.avaliadorcorridas.dados

import android.content.Context
import android.content.SharedPreferences

/** Salva e carrega a [Configuracao] na memória interna do celular (SharedPreferences). */
object Preferencias {

    private const val ARQUIVO = "configuracao"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    fun carregar(ctx: Context): Configuracao {
        val p = prefs(ctx)
        val padrao = Configuracao()
        val destinos = p.getString("destinos", null)
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: padrao.destinosBloqueados
        return Configuracao(
            monitorando = p.getBoolean("monitorando", padrao.monitorando),
            modoDiagnostico = p.getBoolean("diagnostico", padrao.modoDiagnostico),
            destinosBloqueados = destinos,
            minimoPorKm = p.getFloat("minimoPorKm", padrao.minimoPorKm.toFloat()).toDouble(),
            minimoCorrida = p.getFloat("minimoCorrida", padrao.minimoCorrida.toFloat()).toDouble(),
            maxKmAtePassageiro = p.getFloat("maxKmBusca", padrao.maxKmAtePassageiro.toFloat()).toDouble(),
            minimoPorHora = p.getFloat("minimoPorHora", padrao.minimoPorHora.toFloat()).toDouble(),
            notaMinima = p.getFloat("notaMinima", padrao.notaMinima.toFloat()).toDouble(),
            margemAmareloPct = p.getInt("margemAmarelo", padrao.margemAmareloPct),
            incluirBuscaNoCalculo = p.getBoolean("incluirBusca", padrao.incluirBuscaNoCalculo),
            segundosBanner = p.getInt("segundosBanner", padrao.segundosBanner),
            vozAtiva = p.getBoolean("voz", padrao.vozAtiva),
            falarPontosFortes = p.getBoolean("falarFortes", padrao.falarPontosFortes),
        )
    }

    fun salvar(ctx: Context, c: Configuracao) {
        prefs(ctx).edit()
            .putBoolean("monitorando", c.monitorando)
            .putBoolean("diagnostico", c.modoDiagnostico)
            .putString("destinos", c.destinosBloqueados.joinToString("\n"))
            .putFloat("minimoPorKm", c.minimoPorKm.toFloat())
            .putFloat("minimoCorrida", c.minimoCorrida.toFloat())
            .putFloat("maxKmBusca", c.maxKmAtePassageiro.toFloat())
            .putFloat("minimoPorHora", c.minimoPorHora.toFloat())
            .putFloat("notaMinima", c.notaMinima.toFloat())
            .putInt("margemAmarelo", c.margemAmareloPct)
            .putBoolean("incluirBusca", c.incluirBuscaNoCalculo)
            .putInt("segundosBanner", c.segundosBanner)
            .putBoolean("voz", c.vozAtiva)
            .putBoolean("falarFortes", c.falarPontosFortes)
            .apply()
    }

    /** Liga/desliga o monitoramento e devolve o novo estado. */
    fun alternarMonitoramento(ctx: Context): Boolean {
        val novo = !carregar(ctx).monitorando
        prefs(ctx).edit().putBoolean("monitorando", novo).apply()
        return novo
    }
}
