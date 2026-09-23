package com.mateus.avaliadorcorridas.regras

import com.mateus.avaliadorcorridas.dados.Configuracao
import java.util.Locale
import kotlin.math.roundToLong

enum class Cor { VERDE, AMARELO, VERMELHO }

/** O que o app vai falar e mostrar no banner. */
data class Resultado(
    val cor: Cor,
    val fala: String,
    val titulo: String,
    val detalhes: String,
)

/** Compara a oferta com os seus critérios. */
object Avaliador {

    private val BR = Locale.forLanguageTag("pt-BR")

    fun avaliar(o: Oferta, c: Configuracao): Resultado {
        val valor = o.valor ?: 0.0
        val kmBusca = o.kmAtePassageiro
        val kmConsiderado =
            if (c.incluirBuscaNoCalculo) (kmBusca ?: 0.0) + (o.kmViagem ?: 0.0)
            else o.kmViagem ?: 0.0
        val porKm = if (o.kmViagem != null && kmConsiderado > 0) valor / kmConsiderado else null
        val margem = c.margemAmareloPct / 100.0

        val detalhes = buildList {
            add(reais(valor))
            if (kmConsiderado > 0) add("${km(kmConsiderado)} km")
            porKm?.let { add("${reais(it)}/km") }
            kmBusca?.let { add("busca ${km(it)} km") }
        }.joinToString("  ·  ")

        // 1) Destino bloqueado
        val bloqueado = c.destinosBloqueados.firstOrNull { nome ->
            val alvo = ExtratorOferta.normalizar(nome)
            alvo.isNotEmpty() && (
                o.destino?.let { ExtratorOferta.normalizar(it).contains(alvo) } == true ||
                    ExtratorOferta.normalizar(o.trechoDestino).contains(alvo)
                )
        }
        if (bloqueado != null) {
            return Resultado(Cor.VERMELHO, "Atenção: corrida para $bloqueado", "BLOQUEADA: $bloqueado", detalhes)
        }

        // 2) Passageiro longe demais
        if (kmBusca != null && kmBusca > c.maxKmAtePassageiro) {
            return Resultado(
                Cor.VERMELHO,
                "Passageiro longe: ${km(kmBusca)} quilômetros",
                "PASSAGEIRO LONGE",
                detalhes,
            )
        }

        // 3) Valor total abaixo do mínimo
        if (valor < c.minimoCorrida) {
            return Resultado(Cor.VERMELHO, "Abaixo do mínimo: ${falaReais(valor)}", "ABAIXO DO MÍNIMO", detalhes)
        }

        // 4) Sem distância da viagem não dá para calcular R$/km
        if (porKm == null) {
            return Resultado(
                Cor.AMARELO,
                "Distância não lida. Valor ${falaReais(valor)}",
                "DISTÂNCIA NÃO LIDA",
                detalhes,
            )
        }

        // 5) R$/km abaixo do mínimo
        if (porKm < c.minimoPorKm) {
            return Resultado(
                Cor.VERMELHO,
                "Abaixo do mínimo: ${falaReais(porKm)} por quilômetro",
                "ABAIXO DO MÍNIMO",
                detalhes,
            )
        }

        // 6) No limite (dentro da margem do amarelo)
        val noLimite = porKm < c.minimoPorKm * (1 + margem) ||
            valor < c.minimoCorrida * (1 + margem) ||
            (kmBusca != null && kmBusca > c.maxKmAtePassageiro * (1 - margem))
        if (noLimite) {
            return Resultado(Cor.AMARELO, "No limite: ${falaReais(porKm)} por quilômetro", "NO LIMITE", detalhes)
        }

        // 7) Vale a pena
        return Resultado(Cor.VERDE, "Corrida boa: ${falaReais(porKm)} por quilômetro", "CORRIDA BOA", detalhes)
    }

    /** 2.1 → "R$ 2,10" */
    fun reais(v: Double): String = String.format(BR, "R$ %.2f", v)

    /** 2.3 → "2,3" */
    fun km(v: Double): String = String.format(BR, "%.1f", v)

    /** 2.1 → "2 reais e 10 centavos" (soa melhor na voz do que "R$ 2,10"). */
    fun falaReais(v: Double): String {
        val totalCentavos = (v * 100).roundToLong()
        val r = totalCentavos / 100
        val c = totalCentavos % 100
        val parteReais = if (r == 1L) "1 real" else "$r reais"
        val parteCentavos = if (c == 1L) "1 centavo" else "$c centavos"
        return when {
            r == 0L -> parteCentavos
            c == 0L -> parteReais
            else -> "$parteReais e $parteCentavos"
        }
    }
}
