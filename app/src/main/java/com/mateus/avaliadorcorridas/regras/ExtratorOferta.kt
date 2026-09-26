package com.mateus.avaliadorcorridas.regras

import java.text.Normalizer

/** Os dados que conseguimos tirar da tela de oferta. Qualquer campo pode faltar (null). */
data class Oferta(
    val valor: Double?,
    val minutosAtePassageiro: Int?,
    val kmAtePassageiro: Double?,
    val minutosViagem: Int?,
    val kmViagem: Double?,
    val destino: String?,
    /** Nota do passageiro (1 a 5), se aparecer na oferta. */
    val nota: Double? = null,
    /** Texto onde procuramos destinos bloqueados (veja BLOQUEIO_PROCURA_APOS_VIAGEM). */
    val trechoDestino: String,
) {
    /** Tem valor e pelo menos uma distância? */
    val ehOferta: Boolean get() = valor != null && (kmAtePassageiro != null || kmViagem != null)

    /** Identifica a oferta, para não avisar duas vezes a mesma. */
    val chave: String get() = "$valor|$kmAtePassageiro|$kmViagem"

    fun resumo(): String =
        "valor=${valor ?: "?"} | busca=${kmAtePassageiro ?: "?"} km (${minutosAtePassageiro ?: "?"} min)" +
            " | viagem=${kmViagem ?: "?"} km (${minutosViagem ?: "?"} min) | nota=${nota ?: "?"} | destino=${destino ?: "?"}"
}

/**
 * Aplica as regras de [RegrasExtracao] ao texto lido da tela.
 * Não há regras "escondidas" aqui: para mudar O QUE é procurado, edite RegrasExtracao.
 */
object ExtratorOferta {

    fun extrair(linhas: List<String>): Oferta {
        val texto = linhas.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        var busca = primeiroResultado(RegrasExtracao.DISTANCIA_ATE_PASSAGEIRO, texto)
        var viagem = primeiroResultado(RegrasExtracao.DISTANCIA_VIAGEM, texto)

        // Plano B: "X min (Y km)" genérico, para o que ainda não foi encontrado.
        if (busca == null || viagem == null) {
            val jaUsados = listOfNotNull(busca, viagem)
            val genericos = RegrasExtracao.TEMPO_E_DISTANCIA_GENERICO.findAll(texto)
                .filter { g -> jaUsados.none { u -> g.range.first in u.range || u.range.first in g.range } }
                .toList()
            when {
                busca == null && viagem == null -> {
                    busca = genericos.getOrNull(0)
                    viagem = genericos.getOrNull(1)
                }
                busca == null -> busca = genericos.firstOrNull { it.range.first < viagem!!.range.first }
                else -> viagem = genericos.firstOrNull { it.range.first > busca.range.first }
            }
        }

        val trechoDestino =
            if (RegrasExtracao.BLOQUEIO_PROCURA_APOS_VIAGEM && viagem != null) texto.substring(viagem.range.last + 1)
            else texto

        return Oferta(
            valor = extrairValor(texto, busca?.range?.first),
            minutosAtePassageiro = busca?.let { grupo(it, "min")?.toIntOrNull() },
            kmAtePassageiro = busca?.let { km(it) },
            minutosViagem = viagem?.let { grupo(it, "min")?.toIntOrNull() },
            kmViagem = viagem?.let { km(it) },
            destino = extrairDestino(texto, viagem),
            nota = RegrasExtracao.NOTA.find(texto)
                ?.let { grupo(it, "nota") }?.let { numero(it) }
                ?.takeIf { it in 1.0..5.0 },
            trechoDestino = trechoDestino,
        )
    }

    /** Último valor antes da distância até o passageiro; se não houver, o primeiro valor da tela. */
    private fun extrairValor(texto: String, posicaoBusca: Int?): Double? {
        val valores = RegrasExtracao.VALOR.findAll(texto).toList()
        if (valores.isEmpty()) return null
        val escolhido = posicaoBusca
            ?.let { pos -> valores.lastOrNull { it.range.first < pos } }
            ?: valores.first()
        return grupo(escolhido, "valor")?.let { numero(it) }
    }

    private fun extrairDestino(texto: String, viagem: MatchResult?): String? {
        RegrasExtracao.DESTINO_COM_ROTULO.find(texto)?.let { m ->
            grupo(m, "destino")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        if (viagem == null) return null
        return texto.substring(viagem.range.last + 1)
            .lines()
            .map { it.trim() }
            .firstOrNull { pareceDestino(it) }
    }

    private fun pareceDestino(linha: String): Boolean {
        if (linha.length < 3 || linha.none { it.isLetter() }) return false
        if (RegrasExtracao.VALOR.containsMatchIn(linha)) return false
        if (RegrasExtracao.TEMPO_E_DISTANCIA_GENERICO.containsMatchIn(linha)) return false
        val n = normalizar(linha)
        return RegrasExtracao.LINHAS_QUE_NAO_SAO_DESTINO.none { n.startsWith(normalizar(it)) }
    }

    private fun primeiroResultado(regras: List<Regex>, texto: String): MatchResult? =
        regras.firstNotNullOfOrNull { it.find(texto) }

    /** Lê um grupo nomeado; devolve null se a regra não tiver esse grupo. */
    private fun grupo(m: MatchResult, nome: String): String? =
        try { m.groups[nome]?.value } catch (e: IllegalArgumentException) { null }

    private fun km(m: MatchResult): Double? {
        val v = grupo(m, "km")?.let { numero(it) } ?: return null
        return when (grupo(m, "un")?.lowercase()) {
            "m" -> v / 1000.0
            "mi" -> v * 1.609
            else -> v
        }
    }

    /** "12,50" ou "12.50" → 12.5 ; "1.234,56" → 1234.56 */
    fun numero(s: String): Double? {
        val t = if (s.contains(',') && s.contains('.')) s.replace(".", "").replace(',', '.') else s.replace(',', '.')
        return t.toDoubleOrNull()
    }

    /** Minúsculas, sem acentos e com espaços simples: "Côcal  do SUL" → "cocal do sul". */
    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
}
