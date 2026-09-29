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
    val custoManutencao: Double,
    /** Combustível + manutenção. */
    val custoTotal: Double,
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

/** Um item do cálculo de manutenção: quanto custa e quantos km dura. Ex.: pneus, R$ 1.400, 40.000 km. */
data class ItemManutencao(val nome: String, val custo: Double, val kmDuracao: Double) {
    /** Custo deste item por km: custo ÷ km que ele dura. */
    val porKm: Double get() = if (kmDuracao > 0) custo / kmDuracao else 0.0
}

object Calculos {

    /** Quanto custa rodar [km] com o combustível: km ÷ consumo × preço. */
    fun custoCombustivel(km: Double, consumoKmL: Double, precoLitro: Double): Double =
        if (consumoKmL > 0) km / consumoKmL * precoLitro else 0.0

    /** Custo total de rodar [km]: combustível + manutenção. */
    fun custoRodar(km: Double, consumoKmL: Double, precoLitro: Double, manutencaoPorKm: Double): Double =
        custoCombustivel(km, consumoKmL, precoLitro) + km * manutencaoPorKm

    /** Custo por km rodado (combustível + manutenção). */
    fun custoPorKm(consumoKmL: Double, precoLitro: Double, manutencaoPorKm: Double = 0.0): Double =
        custoRodar(1.0, consumoKmL, precoLitro, manutencaoPorKm)

    /** Manutenção por km: soma de (custo ÷ km que dura) de cada item. */
    fun manutencaoPorKm(itens: List<ItemManutencao>): Double = itens.sumOf { it.porKm }

    /**
     * Valores de EXEMPLO para um carro popular 1.0 (como o HB20) rodando em aplicativo.
     * São só um ponto de partida: troque pelos seus preços reais.
     */
    val ITENS_MANUTENCAO_EXEMPLO = listOf(
        ItemManutencao("Pneus (jogo de 4)", 1400.0, 40_000.0),
        ItemManutencao("Troca de óleo e filtros", 280.0, 10_000.0),
        ItemManutencao("Revisões e mecânica", 1200.0, 20_000.0),
        ItemManutencao("Freios (pastilhas e discos)", 500.0, 30_000.0),
        ItemManutencao("Suspensão e amortecedores", 1600.0, 60_000.0),
        ItemManutencao("Limpeza e lavagem (por mês)", 150.0, 3_000.0),
        ItemManutencao("Imprevistos", 1000.0, 20_000.0),
    )

    fun resumo(t: Turno, agora: Long): ResumoTurno {
        val faturamento = t.corridas.sumOf { it.valor }
        val kmCorridas = t.corridas.sumOf { it.km }
        val kmTotal = t.odometroFinal?.let { (it - t.odometroInicial).coerceAtLeast(0.0) }
        val kmDeslocamento = kmTotal?.let { (it - kmCorridas).coerceAtLeast(0.0) }
        // Enquanto o turno está aberto, o custo é estimado só com os km das corridas.
        val kmBaseCusto = kmTotal ?: kmCorridas
        val combustivel = custoCombustivel(kmBaseCusto, t.consumoKmL, t.precoLitro)
        val manutencao = kmBaseCusto * t.manutencaoPorKm
        val lucro = faturamento - combustivel - manutencao
        val horas = ((t.fim ?: agora) - t.inicio).coerceAtLeast(0L) / 3_600_000.0
        return ResumoTurno(
            faturamento = faturamento,
            kmCorridas = kmCorridas,
            kmTotal = kmTotal,
            kmDeslocamento = kmDeslocamento,
            custoCombustivel = combustivel,
            custoManutencao = manutencao,
            custoTotal = combustivel + manutencao,
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
        val custos = resumos.sumOf { it.custoTotal }
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
