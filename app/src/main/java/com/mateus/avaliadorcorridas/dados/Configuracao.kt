package com.mateus.avaliadorcorridas.dados

/** Todos os seus critérios e opções. Os valores abaixo são o padrão da primeira vez. */
data class Configuracao(
    val monitorando: Boolean = false,
    val modoDiagnostico: Boolean = false,
    val destinosBloqueados: List<String> = listOf("Cocal do Sul"),
    val minimoPorKm: Double = 1.80,
    val minimoCorrida: Double = 8.00,
    val maxKmAtePassageiro: Double = 4.0,
    /** Ganho mínimo por hora de corrida: valor ÷ (minutos de busca + viagem) × 60. */
    val minimoPorHora: Double = 45.0,
    /** Nota do passageiro abaixo disso deixa a corrida amarela (não reprova sozinha). */
    val notaMinima: Double = 4.80,
    /** Até quantos % acima do mínimo a corrida é "amarela" (no limite). */
    val margemAmareloPct: Int = 10,
    /** Se true, R$/km e R$/hora usam (busca + viagem). Se false, só a viagem. */
    val incluirBuscaNoCalculo: Boolean = true,
    /** Tempo do aviso no botão "Testar". Nas ofertas reais, o aviso fica enquanto a oferta estiver na tela. */
    val segundosBanner: Int = 6,
    val vozAtiva: Boolean = true,
    /** Se false, a voz fala só os pontos fracos e os do limite (fica mais curta). */
    val falarPontosFortes: Boolean = true,
)
