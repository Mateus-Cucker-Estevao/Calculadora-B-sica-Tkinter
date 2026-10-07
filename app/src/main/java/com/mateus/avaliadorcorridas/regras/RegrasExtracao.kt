package com.mateus.avaliadorcorridas.regras

/**
 * =====================================================================
 *   REGRAS DE EXTRAÇÃO — TUDO QUE VOCÊ PRECISA ALTERAR FICA AQUI
 * =====================================================================
 *
 * Quando o layout do Uber mudar:
 *   1. Ligue o "Modo diagnóstico" no app e espere algumas ofertas aparecerem.
 *   2. Abra "Ver log" e veja como o texto REAL aparece.
 *   3. Ajuste as expressões regulares (Regex) abaixo.
 *   4. Use a seção "Testar leitura" do app para conferir, colando o texto do log.
 *
 * Dicas rápidas de Regex (expressão regular):
 *   \d       = um dígito (0 a 9)             \d+   = um ou mais dígitos
 *   \s       = espaço (ou quebra de linha)   \s*   = zero ou mais espaços
 *   [.,]     = um ponto OU uma vírgula       ?     = o item anterior é opcional
 *   (?:...)  = agrupa sem guardar            |     = "ou"
 *   (?<km>...) = guarda o trecho com o nome "km" (o código usa esse nome!)
 *
 * Todas as regras ignoram maiúsculas/minúsculas.
 * O texto da tela chega com cada pedaço em uma linha separada (unidos por "\n").
 */
object RegrasExtracao {

    /** Pacote do app Uber Driver (também está em res/xml/accessibility_service_config.xml). */
    const val PACOTE_UBER = "com.ubercab.driver"

    private val I = setOf(RegexOption.IGNORE_CASE)

    // ---------------------------------------------------------------
    // Pedaços reutilizados nas regras abaixo
    // ---------------------------------------------------------------

    /** Tempo, ex.: "8 min", "15 mins". Guarda o número no grupo "min". */
    private const val TEMPO = """(?<min>\d+)\s*min\w*"""

    /**
     * Distância, ex.: "2,3 km", "800 m", "1.4 mi".
     * Guarda o número no grupo "km" e a unidade no grupo "un" (km, m ou mi).
     * O código converte metros e milhas para km automaticamente.
     */
    private const val DIST = """(?<km>\d+(?:[.,]\d+)?)\s*(?<un>km|mi|m)\b"""

    /** "8 min (2,3 km)" — tempo seguido da distância entre parênteses (o ")" final é opcional). */
    private const val TEMPO_DIST = """$TEMPO\s*\(\s*$DIST\s*\)?"""

    // ---------------------------------------------------------------
    // 1) VALOR DA CORRIDA
    // ---------------------------------------------------------------

    /**
     * Valor em reais, ex.: "R$ 12,50", "R$12.50", "R$ 8".
     * Ignorados:
     *   - valores com "+" na frente (ex.: "+R$ 3,00" de dinâmica);
     *   - valores por km (ex.: "R$1,09/km aprox.", que o Uber mostra logo abaixo do valor).
     *
     * Se aparecerem vários valores (ex.: seus ganhos do dia no topo da tela),
     * o código escolhe o ÚLTIMO valor que aparece ANTES da distância até o
     * passageiro — que normalmente é o valor da oferta.
     */
    val VALOR = Regex("""(?<!\+)(?<!\+ )R\$\s*(?<valor>\d{1,4}(?:[.,]\d{1,2})?)(?![\d.,])(?!\s*/\s*km)""", I)

    // ---------------------------------------------------------------
    // 2) DISTÂNCIA ATÉ O PASSAGEIRO (busca)
    // ---------------------------------------------------------------

    /** Testadas em ordem; a primeira que encontrar algo vence. */
    val DISTANCIA_ATE_PASSAGEIRO = listOf(
        Regex("""$TEMPO_DIST\s*(?:de\s+)?dist""", I),   // "8 min (2,3 km) de distância"
        Regex("""$DIST\s*(?:de\s+)?dist""", I),         // "2,3 km de distância"
        Regex("""$TEMPO_DIST\s*away""", I),             // "8 mins (1.4 mi) away" (app em inglês)
        Regex("""busca\s*:?\s*$TEMPO_DIST""", I),       // "Busca: 8 min (2,3 km)"
    )

    // ---------------------------------------------------------------
    // 3) DISTÂNCIA DA VIAGEM
    // ---------------------------------------------------------------

    /** Testadas em ordem; a primeira que encontrar algo vence. */
    val DISTANCIA_VIAGEM = listOf(
        Regex("""viagem\s*(?:de\s*)?:?\s*$TEMPO_DIST""", I), // "Viagem de 15 min (7,4 km)"
        Regex("""$TEMPO_DIST\s*(?:de\s+)?viagem""", I),      // "15 min (7,4 km) de viagem"
        Regex("""$TEMPO_DIST\s*trip""", I),                  // "15 mins (4.6 mi) trip" (inglês)
    )

    /**
     * Plano B: se as regras acima não acharem nada, procura qualquer
     * "X min (Y km)" na tela. O 1º encontrado é a busca, o 2º é a viagem.
     *
     * FORMATO REAL DO UBER (setembro/2026) — é este plano B que funciona hoje:
     *   R$ 9,73
     *   R$1,60/km aprox.
     *   5 min (2.7 km)                ← busca (até o passageiro)
     *   Rua Desafio Jovem, ...        ← endereço do passageiro
     *   7 minutos (3.4 km)            ← viagem
     *   Avenida Victor Meireles, ...  ← destino
     *   Aceitar / Selecionar
     */
    val TEMPO_E_DISTANCIA_GENERICO = Regex(TEMPO_DIST, I)

    // ---------------------------------------------------------------
    // 3b) NOTA DO PASSAGEIRO
    // ---------------------------------------------------------------

    /**
     * Linha só com a nota, ex.: "★ 4,87", "4,95 ★", "4,74 (41)".
     * A estrela é opcional (a leitura por imagem às vezes troca por "*" ou nem lê).
     * Guarda o número no grupo "nota". Notas acima de 5 são ignoradas.
     */
    val NOTA = Regex("""(?m)^\s*[^\w\s]?\s*(?<nota>[1-5][.,]\d{1,2})\s*[^\w\s]?\s*(?:\(\d+\))?\s*$""")

    // ---------------------------------------------------------------
    // 4) DESTINO
    // ---------------------------------------------------------------

    /** Linha com rótulo explícito, ex.: "Destino: Cocal do Sul". Grupo "destino". */
    val DESTINO_COM_ROTULO = Regex("""(?:destino|drop-?off)\s*:\s*(?<destino>[^\n]+)""", I)

    /**
     * Se não houver rótulo, o destino é a PRIMEIRA linha depois da linha da viagem
     * que não seja um botão/palavra da lista abaixo, que tenha letras e que
     * não seja um valor ou distância.
     * (Compara sem acentos e sem diferenciar maiúsculas; basta a linha COMEÇAR com a palavra.)
     */
    val LINHAS_QUE_NAO_SAO_DESTINO = listOf(
        "aceitar", "selecionar", "recusar", "accept", "match",
        "uberx", "uber x", "comfort", "black", "flash", "moto", "juntos", "priority",
        "viagem", "exclusivo", "verificado", "tarifa", "inclui", "dinamica",
        "ganhos", "promocao", "fechar",
    )

    /**
     * A busca por DESTINOS BLOQUEADOS olha primeiro o destino extraído e, em seguida,
     * todo o texto que aparece DEPOIS da linha da viagem (onde fica o endereço de destino).
     * Se a linha da viagem não for encontrada, olha a tela inteira.
     */
    const val BLOQUEIO_PROCURA_APOS_VIAGEM = true

    // ---------------------------------------------------------------
    // 4b) TELAS DA CORRIDA (calibrado com o log real de 05/10/2026)
    // ---------------------------------------------------------------
    //
    // Depois de aceitar uma oferta, o Uber mostra, nesta ordem:
    //   1. indo buscar:   "Encontro com [nome]", "Estou a caminho", "Cheguei"
    //   2. no embarque:   "Iniciar UberX"
    //   3. em viagem:     "Encerrar UberX", "Destino de [nome]", "A caminho da última parada"
    //   4. fim:           "Como foi a viagem?", "Avaliar usuário", depois "Procurando viagens"
    //
    // Quando o app vê a tela 1 (ou a 3, se perdeu a 1), registra uma corrida no turno usando
    // a última oferta lida até [JANELA_OFERTA_ACEITA_MS] antes. Se não leu nenhuma oferta,
    // registra a corrida "para revisar" (você completa o valor no turno).
    //
    // Compara sem acentos e sem diferenciar maiúsculas. Alguns trechos começam sem a primeira
    // letra ("niciar uber", "ncerrar uber") porque a leitura da imagem às vezes troca o "I" por "l".

    val TELAS_BUSCANDO_PASSAGEIRO = listOf("encontro com", "niciar uber", "estou a caminho", "cheguei")

    val TELAS_EM_VIAGEM = listOf(
        "ncerrar uber", "destino de ",
        "a caminho da primeira parada", "a caminho da ultima parada", "a caminho da proxima parada",
    )

    val TELAS_SEM_CORRIDA = listOf(
        "como foi a viagem", "avaliar usuario", "procurando viagens", "voce esta online", "voce esta offline",
    )

    /** A oferta aceita aparece até alguns segundos antes de "Encontro com..." (no log: 10 a 17 s). */
    const val JANELA_OFERTA_ACEITA_MS = 3 * 60_000L

    /** Sem nenhuma tela de corrida por este tempo, o app considera que não há corrida em andamento. */
    const val SEM_TELA_DE_CORRIDA_MS = 30 * 60_000L

    // ---------------------------------------------------------------
    // 4c) PROTEÇÃO CONTRA ERROS DA LEITURA POR IMAGEM
    // ---------------------------------------------------------------

    /**
     * Valores fora desta faixa são considerados erro de leitura e ignorados
     * (ex.: "R$1l,96/km" lido como R$ 1,00).
     */
    const val VALOR_MINIMO_VALIDO = 3.0
    const val VALOR_MAXIMO_VALIDO = 400.0

    /**
     * A leitura às vezes perde a vírgula: "R$ 1552" (era R$ 15,52) ou "(33 km)" (era 3,3 km).
     * Valor sem vírgula acima do máximo é dividido por 100. Distância sem vírgula que daria
     * uma velocidade acima desta (km ÷ minutos) é dividida por 10.
     */
    const val VELOCIDADE_MAXIMA_KMH = 150.0

    // ---------------------------------------------------------------
    // 5) O QUE CONTA COMO "OFERTA"
    // ---------------------------------------------------------------

    /**
     * Uma tela só é tratada como oferta se tiver um VALOR e pelo menos uma DISTÂNCIA.
     * (Evita avisos falsos na tela inicial, que também mostra "R$" dos seus ganhos.)
     *
     * Se a oferta ainda não mostrou a distância da viagem (tela carregando), o app
     * espera um pouco e tenta ler de novo, no máximo esta quantidade de vezes:
     */
    const val TENTATIVAS_ESPERANDO_VIAGEM = 3
}
