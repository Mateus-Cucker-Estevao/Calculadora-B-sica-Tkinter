package com.mateus.avaliadorcorridas.regras

import com.mateus.avaliadorcorridas.dados.Turno

/** Números de um turno. Campos com "?" ficam null quando ainda não dá para calcular. */
data class ResumoTurno(
    val faturamento: Double,
    val kmCorridas: Double,
    /** Odômetro final − inicial (null enquanto o turno está aberto). */
    val kmTotal: Double?,
    /** km rodados sem ser em corrida: kmTotal − kmCorridas. */
    val kmDeslocamento: Double?,
    val custoCombustivel: Double,
    val lucro: Double,
    val horas: Double,
    val lucroPorHora: Double?,
    /** Parte dos km que foi paga (kmCorridas ÷ kmTotal), de 0 a 1. */
    val aproveitamento: Double?,
    val corridas: Int,
)

/** Soma dos turnos de um mês. */
data class ResumoMes(
    val faturamento: Double,
    val custos: Double,
    val lucro: Double,
    val kmTotal: Double,
    val kmCorridas: Double,
    val aproveitamento: Double?,
    val turnos: Int,
    val horas: Double,
    val lucroPorHora: Double?,
    val corridas: Int,
)

object Calculos {

    /** Quanto custa rodar [km] com o combustível: km ÷ consumo × preço. */
    fun custoCombustivel(km: Double, consumoKmL: Double, precoLitro: Double): Double =
        if (consumoKmL > 0) km / consumoKmL * precoLitro else 0.0

    /** Custo de combustível por km rodado. */
    fun custoPorKm(consumoKmL: Double, precoLitro: Double): Double = custoCombustivel(1.0, consumoKmL, precoLitro)

    fun resumo(t: Turno, agora: Long): ResumoTurno {
        val faturamento = t.corridas.sumOf { it.valor }
        val kmCorridas = t.corridas.sumOf { it.km }
        val kmTotal = t.odometroFinal?.let { (it - t.odometroInicial).coerceAtLeast(0.0) }
        val kmDeslocamento = kmTotal?.let { (it - kmCorridas).coerceAtLeast(0.0) }
        // Enquanto o turno está aberto, o custo é estimado só com os km das corridas.
        val custo = custoCombustivel(kmTotal ?: kmCorridas, t.consumoKmL, t.precoLitro)
        val lucro = faturamento - custo
        val horas = ((t.fim ?: agora) - t.inicio).coerceAtLeast(0L) / 3_600_000.0
        return ResumoTurno(
            faturamento = faturamento,
            kmCorridas = kmCorridas,
            kmTotal = kmTotal,
            kmDeslocamento = kmDeslocamento,
            custoCombustivel = custo,
            lucro = lucro,
            horas = horas,
            lucroPorHora = if (horas > 0.01) lucro / horas else null,
            aproveitamento = kmTotal?.takeIf { it > 0 }?.let { (kmCorridas / it).coerceIn(0.0, 1.0) },
            corridas = t.corridas.size,
        )
    }

    fun resumoMes(turnos: List<Turno>, agora: Long): ResumoMes {
        val resumos = turnos.map { resumo(it, agora) }
        val faturamento = resumos.sumOf { it.faturamento }
        val custos = resumos.sumOf { it.custoCombustivel }
        val lucro = faturamento - custos
        val kmTotal = resumos.sumOf { it.kmTotal ?: it.kmCorridas }
        val kmCorridas = resumos.sumOf { it.kmCorridas }
        val horas = resumos.sumOf { it.horas }
        return ResumoMes(
            faturamento = faturamento,
            custos = custos,
            lucro = lucro,
            kmTotal = kmTotal,
            kmCorridas = kmCorridas,
            aproveitamento = if (kmTotal > 0) (kmCorridas / kmTotal).coerceIn(0.0, 1.0) else null,
            turnos = turnos.size,
            horas = horas,
            lucroPorHora = if (horas > 0.01) lucro / horas else null,
            corridas = resumos.sumOf { it.corridas },
        )
    }
}
