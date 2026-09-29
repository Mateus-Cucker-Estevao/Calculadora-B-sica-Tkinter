package com.mateus.avaliadorcorridas.dados

/** Todos os seus parâmetros. Os valores abaixo são o padrão da primeira vez. */
data class Configuracao(
    val modoDiagnostico: Boolean = false,
    val destinosBloqueados: List<String> = listOf("Cocal do Sul"),

    // ---- Veículo ----
    /** Consumo real do carro (km por litro). */
    val consumoKmL: Double = 11.0,
    /** Preço do combustível (R$ por litro). */
    val precoLitro: Double = 6.29,

    // ---- Critérios das ofertas ----
    val minimoPorKm: Double = 1.80,
    val minimoCorrida: Double = 8.00,
    /** Lucro mínimo da corrida: valor − combustível dos km (busca + viagem). */
    val lucroMinimoCorrida: Double = 6.00,
    val maxKmAtePassageiro: Double = 4.0,
    /** Ganho mínimo por hora de corrida: valor ÷ (minutos de busca + viagem) × 60. */
    val minimoPorHora: Double = 45.0,
    /** Nota do passageiro abaixo disso deixa a corrida amarela (não reprova sozinha). */
    val notaMinima: Double = 4.80,
    /** Até quantos % acima do mínimo a corrida é "amarela" (no limite). */
    val margemAmareloPct: Int = 10,
    /** Se true, R$/km e R$/hora usam (busca + viagem). Se false, só a viagem. */
    val incluirBuscaNoCalculo: Boolean = true,

    // ---- Aviso e voz ----
    /** Tempo do aviso no botão "Testar". Nas ofertas reais, o aviso fica enquanto a oferta estiver na tela. */
    val segundosBanner: Int = 6,
    val vozAtiva: Boolean = true,
    /** Se false, a voz fala só os pontos fracos e os do limite (fica mais curta). */
    val falarPontosFortes: Boolean = true,
)
