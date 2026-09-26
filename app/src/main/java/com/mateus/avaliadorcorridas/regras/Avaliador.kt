package com.mateus.avaliadorcorridas.regras

import com.mateus.avaliadorcorridas.dados.Configuracao
import java.util.Locale
import kotlin.math.roundToLong

/** VERDE = ponto forte / corrida boa; AMARELO = no limite; VERMELHO = ponto fraco / corrida ruim. */
enum class Cor { VERDE, AMARELO, VERMELHO }

/** Um item do diagnóstico, ex.: "passageiro longe, 11,4 quilômetros". */
data class Ponto(
    val cor: Cor,
    /** Como o ponto é falado (no meio de uma frase, em minúsculas). */
    val fala: String,
    /** Como o ponto aparece no banner. */
    val texto: String,
)

/** O que o app vai falar e mostrar no banner. */
data class Resultado(
    val cor: Cor,
    val fala: String,
    val titulo: String,
    val detalhes: String,
    val pontos: List<Ponto> = emptyList(),
)

/**
 * Compara a oferta com TODOS os seus critérios e monta um diagnóstico:
 * cada critério vira um ponto forte (verde), no limite (amarelo) ou fraco (vermelho).
 * A cor final da corrida é a do pior ponto.
 */
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
        }.joinToString("  ·  ")

        val pontos = mutableListOf<Ponto>()

        // 1) Destino
        val bloqueado = destinoBloqueado(o, c)
        if (bloqueado != null) {
            pontos += Ponto(Cor.VERMELHO, "atenção, corrida para $bloqueado", "Destino bloqueado: $bloqueado")
        } else if (c.destinosBloqueados.isNotEmpty()) {
            pontos += Ponto(Cor.VERDE, "destino liberado", "Destino liberado")
        }

        // 2) Distância até o passageiro
        pontos += when {
            kmBusca == null ->
                Ponto(Cor.AMARELO, "distância até o passageiro não lida", "Busca: não lida")
            kmBusca > c.maxKmAtePassageiro ->
                Ponto(Cor.VERMELHO, "passageiro longe, ${km(kmBusca)} quilômetros",
                    "Passageiro longe: ${km(kmBusca)} km (máx. ${km(c.maxKmAtePassageiro)})")
            kmBusca > c.maxKmAtePassageiro * (1 - margem) ->
                Ponto(Cor.AMARELO, "passageiro um pouco longe, ${km(kmBusca)} quilômetros",
                    "Busca no limite: ${km(kmBusca)} km")
            else ->
                Ponto(Cor.VERDE, "passageiro perto, ${km(kmBusca)} quilômetros", "Passageiro perto: ${km(kmBusca)} km")
        }

        // 3) Valor mínimo da corrida
        pontos += when {
            valor < c.minimoCorrida ->
                Ponto(Cor.VERMELHO, "abaixo do valor mínimo, ${falaReais(valor)}",
                    "Abaixo do mínimo: ${reais(valor)} (mín. ${reais(c.minimoCorrida)})")
            valor < c.minimoCorrida * (1 + margem) ->
                Ponto(Cor.AMARELO, "valor perto do mínimo, ${falaReais(valor)}", "Valor no limite: ${reais(valor)}")
            else ->
                Ponto(Cor.VERDE, "valor acima do mínimo, ${falaReais(valor)}", "Valor acima do mínimo: ${reais(valor)}")
        }

        // 4) Valor por km
        pontos += when {
            porKm == null ->
                Ponto(Cor.AMARELO, "distância da viagem não lida", "R$/km: não lido")
            porKm < c.minimoPorKm ->
                Ponto(Cor.VERMELHO, "barata demais, ${falaReais(porKm)} por quilômetro",
                    "Barata demais: ${reais(porKm)}/km (mín. ${reais(c.minimoPorKm)})")
            porKm < c.minimoPorKm * (1 + margem) ->
                Ponto(Cor.AMARELO, "no limite, ${falaReais(porKm)} por quilômetro", "R$/km no limite: ${reais(porKm)}")
            else ->
                Ponto(Cor.VERDE, "bom valor por quilômetro, ${falaReais(porKm)}", "Bom R$/km: ${reais(porKm)}")
        }

        val cor = pontos.maxOf { it.cor } // VERMELHO > AMARELO > VERDE
        val titulo = when {
            bloqueado != null -> "BLOQUEADA: $bloqueado"
            cor == Cor.VERMELHO -> "CORRIDA RUIM"
            cor == Cor.AMARELO -> "NO LIMITE"
            else -> "CORRIDA BOA"
        }
        // No banner: primeiro os fracos, depois os do limite, por último os fortes.
        val ordenados = pontos.sortedByDescending { it.cor }
        return Resultado(cor, montarFala(cor, ordenados, c.falarPontosFortes), titulo, detalhes, ordenados)
    }

    /** Ex.: "Corrida ruim. Pontos fracos: ...; .... No limite: .... Pontos fortes: ...." */
    private fun montarFala(cor: Cor, pontos: List<Ponto>, falarFortes: Boolean): String = buildString {
        append(
            when (cor) {
                Cor.VERMELHO -> "Corrida ruim."
                Cor.AMARELO -> "Corrida no limite."
                Cor.VERDE -> "Corrida boa."
            },
        )
        val grupos = listOf(
            "Pontos fracos" to pontos.filter { it.cor == Cor.VERMELHO },
            "No limite" to pontos.filter { it.cor == Cor.AMARELO },
            "Pontos fortes" to if (falarFortes) pontos.filter { it.cor == Cor.VERDE } else emptyList(),
        )
        for ((rotulo, itens) in grupos) {
            if (itens.isEmpty()) continue
            append(' ').append(rotulo).append(": ")
            append(itens.joinToString("; ") { it.fala }).append('.')
        }
    }

    private fun destinoBloqueado(o: Oferta, c: Configuracao): String? =
        c.destinosBloqueados.firstOrNull { nome ->
            val alvo = ExtratorOferta.normalizar(nome)
            alvo.isNotEmpty() && (
                o.destino?.let { ExtratorOferta.normalizar(it).contains(alvo) } == true ||
                    ExtratorOferta.normalizar(o.trechoDestino).contains(alvo)
                )
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
