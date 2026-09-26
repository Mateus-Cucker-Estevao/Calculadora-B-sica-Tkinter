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

    /** "8 min (2,3 km)" — tempo seguido da distância entre parênteses. */
    private const val TEMPO_DIST = """$TEMPO\s*\(\s*$DIST\s*\)"""

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
