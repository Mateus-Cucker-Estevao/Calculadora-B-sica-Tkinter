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
    /** Como o ponto aparece em texto (tela de teste). */
    val texto: String,
)

/** Um número do painel flutuante e a cor dele (null = não deu para avaliar). */
data class Indicador(val texto: String, val cor: Cor?)

/**
 * Tudo que o painel flutuante mostra, já formatado:
 *
 *   [ R$ 19,59 ] [ 11,4 km ] [ 24 min ] [ Busca 2,9 km ]
 *   [  R$/km   ] [  R$/h   ] [ R$/min ] [    Nota      ]
 */
data class Painel(
    val valor: Indicador,
    /** Lucro estimado: valor − combustível dos km (busca + viagem). */
    val lucro: Indicador,
    val kmTotal: String,
    val minTotal: String,
    val busca: Indicador,
    val porKm: Indicador,
    val porHora: Indicador,
    val porMinuto: Indicador,
    val nota: Indicador,
    val destinoBloqueado: String?,
)

/** O que o app vai falar e mostrar. */
data class Resultado(
    val cor: Cor,
    val fala: String,
    val titulo: String,
    val detalhes: String,
    val pontos: List<Ponto> = emptyList(),
    val painel: Painel? = null,
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
        val margem = c.margemAmareloPct / 100.0

        val kmConsiderado =
            if (c.incluirBuscaNoCalculo) (kmBusca ?: 0.0) + (o.kmViagem ?: 0.0)
            else o.kmViagem ?: 0.0
        val porKm = if (o.kmViagem != null && kmConsiderado > 0) valor / kmConsiderado else null

        val minConsiderado =
            if (c.incluirBuscaNoCalculo) (o.minutosAtePassageiro ?: 0) + (o.minutosViagem ?: 0)
            else o.minutosViagem ?: 0
        val porHora = if (o.minutosViagem != null && minConsiderado > 0) valor / minConsiderado * 60 else null

        // Lucro real: o combustível é gasto em todos os km (busca + viagem).
        val kmRodados = (kmBusca ?: 0.0) + (o.kmViagem ?: 0.0)
        val lucro = if (kmBusca != null || o.kmViagem != null) {
            valor - Calculos.custoCombustivel(kmRodados, c.consumoKmL, c.precoLitro)
        } else {
            null
        }

        val pontos = mutableListOf<Ponto>()

        // 1) Destino
        val bloqueado = destinoBloqueado(o, c)
        if (bloqueado != null) {
            pontos += Ponto(Cor.VERMELHO, "atenção, corrida para $bloqueado", "Destino bloqueado: $bloqueado")
        }

        // 2) Distância até o passageiro
        val corBusca = faixaMaximo(kmBusca, c.maxKmAtePassageiro, margem)
        pontos += when (corBusca) {
            null -> Ponto(Cor.AMARELO, "distância até o passageiro não lida", "Busca: não lida")
            Cor.VERMELHO -> Ponto(Cor.VERMELHO, "passageiro longe, ${km(kmBusca!!)} quilômetros",
                "Passageiro longe: ${km(kmBusca)} km (máx. ${km(c.maxKmAtePassageiro)})")
            Cor.AMARELO -> Ponto(Cor.AMARELO, "passageiro um pouco longe, ${km(kmBusca!!)} quilômetros",
                "Busca no limite: ${km(kmBusca)} km")
            Cor.VERDE -> Ponto(Cor.VERDE, "passageiro perto, ${km(kmBusca!!)} quilômetros",
                "Passageiro perto: ${km(kmBusca)} km")
        }

        // 3) Valor mínimo da corrida
        val corValor = faixaMinimo(valor, c.minimoCorrida, margem)!!
        pontos += when (corValor) {
            Cor.VERMELHO -> Ponto(Cor.VERMELHO, "abaixo do valor mínimo, ${falaReais(valor)}",
                "Abaixo do mínimo: ${reais(valor)} (mín. ${reais(c.minimoCorrida)})")
            Cor.AMARELO -> Ponto(Cor.AMARELO, "valor perto do mínimo, ${falaReais(valor)}", "Valor no limite: ${reais(valor)}")
            Cor.VERDE -> Ponto(Cor.VERDE, "valor acima do mínimo, ${falaReais(valor)}", "Valor acima do mínimo: ${reais(valor)}")
        }

        // 3b) Lucro da corrida (descontando o combustível)
        val corLucro = faixaMinimo(lucro, c.lucroMinimoCorrida, margem)
        when (corLucro) {
            null -> Unit
            Cor.VERMELHO -> pontos += Ponto(Cor.VERMELHO, "lucro baixo, ${falaReais(lucro!!)}",
                "Lucro baixo: ${reais(lucro)} (mín. ${reais(c.lucroMinimoCorrida)})")
            Cor.AMARELO -> pontos += Ponto(Cor.AMARELO, "lucro no limite, ${falaReais(lucro!!)}", "Lucro no limite: ${reais(lucro)}")
            Cor.VERDE -> pontos += Ponto(Cor.VERDE, "lucro de ${falaReais(lucro!!)}", "Lucro: ${reais(lucro)}")
        }

        // 4) Valor por km
        val corKm = faixaMinimo(porKm, c.minimoPorKm, margem)
        pontos += when (corKm) {
            null -> Ponto(Cor.AMARELO, "distância da viagem não lida", "R$/km: não lido")
            Cor.VERMELHO -> Ponto(Cor.VERMELHO, "barata demais, ${falaReais(porKm!!)} por quilômetro",
                "Barata demais: ${reais(porKm)}/km (mín. ${reais(c.minimoPorKm)})")
            Cor.AMARELO -> Ponto(Cor.AMARELO, "no limite, ${falaReais(porKm!!)} por quilômetro",
                "R$/km no limite: ${reais(porKm)}")
            Cor.VERDE -> Ponto(Cor.VERDE, "bom valor por quilômetro, ${falaReais(porKm!!)}", "Bom R$/km: ${reais(porKm)}")
        }

        // 5) Ganho por hora (só avalia se os minutos foram lidos)
        val corHora = faixaMinimo(porHora, c.minimoPorHora, margem)
        when (corHora) {
            null -> Unit
            Cor.VERMELHO -> pontos += Ponto(Cor.VERMELHO, "pouco por hora, ${inteiro(porHora!!)} reais por hora",
                "Pouco por hora: R$ ${inteiro(porHora)}/h (mín. R$ ${inteiro(c.minimoPorHora)})")
            Cor.AMARELO -> pontos += Ponto(Cor.AMARELO, "por hora no limite, ${inteiro(porHora!!)} reais",
                "R$/h no limite: R$ ${inteiro(porHora)}")
            Cor.VERDE -> pontos += Ponto(Cor.VERDE, "bom ganho por hora, ${inteiro(porHora!!)} reais",
                "Bom R$/h: R$ ${inteiro(porHora)}")
        }

        // 6) Nota do passageiro (abaixo do mínimo deixa amarelo, não reprova sozinha)
        val nota = o.nota
        val corNota = when {
            nota == null -> null
            nota < c.notaMinima -> Cor.AMARELO
            else -> Cor.VERDE
        }
        if (nota != null) {
            pontos += if (corNota == Cor.AMARELO) {
                Ponto(Cor.AMARELO, "nota do passageiro baixa, ${notaTexto(nota)}", "Nota baixa: ${notaTexto(nota)}")
            } else {
                Ponto(Cor.VERDE, "nota boa, ${notaTexto(nota)}", "Nota: ${notaTexto(nota)}")
            }
        }

        val cor = pontos.maxOf { it.cor } // VERMELHO > AMARELO > VERDE
        val titulo = when {
            bloqueado != null -> "BLOQUEADA: $bloqueado"
            cor == Cor.VERMELHO -> "CORRIDA RUIM"
            cor == Cor.AMARELO -> "NO LIMITE"
            else -> "CORRIDA BOA"
        }

        val kmTotal = (kmBusca ?: 0.0) + (o.kmViagem ?: 0.0)
        val minTotal = (o.minutosAtePassageiro ?: 0) + (o.minutosViagem ?: 0)
        val painel = Painel(
            valor = Indicador(reais(valor), corValor),
            lucro = Indicador(lucro?.let { reais(it) } ?: "–", corLucro),
            kmTotal = if (kmTotal > 0) "${km(kmTotal)} km" else "– km",
            minTotal = if (minTotal > 0) "$minTotal min" else "– min",
            busca = Indicador(kmBusca?.let { "${km(it)} km" } ?: "–", corBusca),
            porKm = Indicador(porKm?.let { String.format(BR, "%.2f", it) } ?: "–", corKm),
            porHora = Indicador(porHora?.let { inteiro(it) } ?: "–", corHora),
            porMinuto = Indicador(porHora?.let { String.format(BR, "%.2f", it / 60) } ?: "–", corHora),
            nota = Indicador(nota?.let { notaTexto(it) } ?: "–", corNota),
            destinoBloqueado = bloqueado,
        )

        val detalhes = listOf(reais(valor), painel.kmTotal, painel.minTotal).joinToString("  ·  ")
        // Primeiro os fracos, depois os do limite, por último os fortes.
        val ordenados = pontos.sortedByDescending { it.cor }
        return Resultado(cor, montarFala(cor, ordenados, c.falarPontosFortes), titulo, detalhes, ordenados, painel)
    }

    /** Para "quanto maior, melhor" (valor, R$/km, R$/h). */
    private fun faixaMinimo(v: Double?, minimo: Double, margem: Double): Cor? = when {
        v == null -> null
        v < minimo -> Cor.VERMELHO
        v < minimo * (1 + margem) -> Cor.AMARELO
        else -> Cor.VERDE
    }

    /** Para "quanto menor, melhor" (distância até o passageiro). */
    private fun faixaMaximo(v: Double?, maximo: Double, margem: Double): Cor? = when {
        v == null -> null
        v > maximo -> Cor.VERMELHO
        v > maximo * (1 - margem) -> Cor.AMARELO
        else -> Cor.VERDE
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

    /** 48.97 → "49" */
    fun inteiro(v: Double): String = v.roundToLong().toString()

    /** 4.87 → "4,87" */
    fun notaTexto(v: Double): String = String.format(BR, "%.2f", v)

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
