package com.mateus.avaliadorcorridas.dados

/**
 * Uma corrida feita dentro de um turno. Só números: nenhum nome ou endereço de passageiro.
 * [manual] = true quando foi adicionada ou editada à mão.
 */
data class Corrida(
    val id: Long,
    /** Quando a corrida foi aceita (milissegundos desde 1970). */
    val hora: Long,
    val valor: Double,
    val kmBusca: Double,
    val kmViagem: Double,
    val minBusca: Int = 0,
    val minViagem: Int = 0,
    val nota: Double? = null,
    val manual: Boolean = false,
    /** true quando o app registrou a corrida mas não leu a oferta: o valor precisa ser preenchido. */
    val precisaRevisar: Boolean = false,
) {
    val km: Double get() = kmBusca + kmViagem
}

/**
 * Um turno de trabalho: começa com o odômetro inicial e termina com o final.
 * O consumo, o preço do combustível e a manutenção por km ficam guardados no turno (foto do momento),
 * para que mudar os parâmetros depois não altere o passado.
 */
data class Turno(
    val id: Long,
    val inicio: Long,
    val fim: Long? = null,
    val odometroInicial: Double,
    val odometroFinal: Double? = null,
    val consumoKmL: Double,
    val precoLitro: Double,
    val corridas: List<Corrida> = emptyList(),
    /** Quantas ofertas diferentes apareceram durante o turno. */
    val ofertasVistas: Int = 0,
    /** Custo de manutenção por km (foto do momento em que o turno começou). */
    val manutencaoPorKm: Double = 0.0,
) {
    val aberto: Boolean get() = fim == null
}
