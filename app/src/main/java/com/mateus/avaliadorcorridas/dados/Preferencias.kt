package com.mateus.avaliadorcorridas.dados

import android.content.Context
import android.content.SharedPreferences

/** Salva e carrega a [Configuracao] na memória interna do celular (SharedPreferences). */
object Preferencias {

    private const val ARQUIVO = "configuracao"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    private fun SharedPreferences.double(chave: String, padrao: Double): Double =
        getFloat(chave, padrao.toFloat()).toDouble()

    fun carregar(ctx: Context): Configuracao {
        val p = prefs(ctx)
        val padrao = Configuracao()
        val destinos = p.getString("destinos", null)
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: padrao.destinosBloqueados
        return Configuracao(
            modoDiagnostico = p.getBoolean("diagnostico", padrao.modoDiagnostico),
            destinosBloqueados = destinos,
            consumoKmL = p.double("consumoKmL", padrao.consumoKmL),
            precoLitro = p.double("precoLitro", padrao.precoLitro),
            manutencaoPorKm = p.double("manutencaoPorKm", padrao.manutencaoPorKm),
            minimoPorKm = p.double("minimoPorKm", padrao.minimoPorKm),
            minimoCorrida = p.double("minimoCorrida", padrao.minimoCorrida),
            lucroMinimoCorrida = p.double("lucroMinimo", padrao.lucroMinimoCorrida),
            maxKmAtePassageiro = p.double("maxKmBusca", padrao.maxKmAtePassageiro),
            minimoPorHora = p.double("minimoPorHora", padrao.minimoPorHora),
            notaMinima = p.double("notaMinima", padrao.notaMinima),
            margemAmareloPct = p.getInt("margemAmarelo", padrao.margemAmareloPct),
            incluirBuscaNoCalculo = p.getBoolean("incluirBusca", padrao.incluirBuscaNoCalculo),
            segundosBanner = p.getInt("segundosBanner", padrao.segundosBanner),
            vozAtiva = p.getBoolean("voz", padrao.vozAtiva),
            falarPontosFortes = p.getBoolean("falarFortes", padrao.falarPontosFortes),
        )
    }

    fun salvar(ctx: Context, c: Configuracao) {
        prefs(ctx).edit()
            .putBoolean("diagnostico", c.modoDiagnostico)
            .putString("destinos", c.destinosBloqueados.joinToString("\n"))
            .putFloat("consumoKmL", c.consumoKmL.toFloat())
            .putFloat("precoLitro", c.precoLitro.toFloat())
            .putFloat("manutencaoPorKm", c.manutencaoPorKm.toFloat())
            .putFloat("minimoPorKm", c.minimoPorKm.toFloat())
            .putFloat("minimoCorrida", c.minimoCorrida.toFloat())
            .putFloat("lucroMinimo", c.lucroMinimoCorrida.toFloat())
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
}
