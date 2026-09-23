package com.mateus.avaliadorcorridas.dados

/** Todos os seus critérios e opções. Os valores abaixo são o padrão da primeira vez. */
data class Configuracao(
    val monitorando: Boolean = false,
    val modoDiagnostico: Boolean = false,
    val destinosBloqueados: List<String> = listOf("Cocal do Sul"),
    val minimoPorKm: Double = 1.80,
    val minimoCorrida: Double = 8.00,
    val maxKmAtePassageiro: Double = 4.0,
    /** Até quantos % acima do mínimo a corrida é "amarela" (no limite). */
    val margemAmareloPct: Int = 10,
    /** Se true, o R$/km é calculado com (busca + viagem). Se false, só com a viagem. */
    val incluirBuscaNoCalculo: Boolean = true,
    val segundosBanner: Int = 6,
    val vozAtiva: Boolean = true,
)
