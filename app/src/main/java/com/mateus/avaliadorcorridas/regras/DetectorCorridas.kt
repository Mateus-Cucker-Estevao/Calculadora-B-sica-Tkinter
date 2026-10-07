package com.mateus.avaliadorcorridas.regras

/** Em que parte de uma corrida a tela do Uber está. */
enum class FaseCorrida { SEM_CORRIDA, BUSCANDO_PASSAGEIRO, EM_VIAGEM }

/** Uma corrida que acabou de começar. [oferta] é null quando o app não leu a oferta aceita. */
data class NovaCorrida(val oferta: Oferta?)

/**
 * Acompanha as telas do Uber e descobre quando uma corrida COMEÇA:
 *   - ao entrar em "buscando passageiro" ("Encontro com...") vindo de "sem corrida" ou de outra viagem;
 *   - ao entrar em "em viagem" direto de "sem corrida" (quando o app perdeu a tela de busca).
 * A corrida usa a última oferta lida até [RegrasExtracao.JANELA_OFERTA_ACEITA_MS] antes.
 *
 * Não usa nomes de passageiros: só o tipo de tela.
 */
class DetectorCorridas {

    var fase: FaseCorrida = FaseCorrida.SEM_CORRIDA
        private set

    private var oferta: Oferta? = null
    private var ofertaEm = 0L
    private var ultimaTelaDeCorridaEm = 0L

    /** Chame a cada leitura que tenha uma oferta. Guarda a melhor leitura (a mais completa). */
    fun ofertaVista(o: Oferta, agora: Long) {
        if (!o.ehOferta) return
        val atual = oferta
        val mesmaOferta = atual != null && atual.valor == o.valor && agora - ofertaEm < 60_000
        if (mesmaOferta && atual!!.kmViagem != null && o.kmViagem == null) {
            ofertaEm = agora // leitura pior da mesma oferta: mantém a anterior
            return
        }
        oferta = o
        ofertaEm = agora
    }

    /** Chame a cada leitura SEM oferta. Devolve [NovaCorrida] quando uma corrida começa. */
    fun telaLida(linhas: List<String>, agora: Long): NovaCorrida? {
        val nova = ExtratorOferta.faseDaTela(linhas) ?: return null
        if (agora - ultimaTelaDeCorridaEm > RegrasExtracao.SEM_TELA_DE_CORRIDA_MS) fase = FaseCorrida.SEM_CORRIDA
        ultimaTelaDeCorridaEm = agora

        val anterior = fase
        fase = nova
        val comecou = when (nova) {
            FaseCorrida.BUSCANDO_PASSAGEIRO -> anterior != FaseCorrida.BUSCANDO_PASSAGEIRO
            FaseCorrida.EM_VIAGEM -> anterior == FaseCorrida.SEM_CORRIDA
            FaseCorrida.SEM_CORRIDA -> false
        }
        if (!comecou) return null

        val aceita = oferta?.takeIf { agora - ofertaEm <= RegrasExtracao.JANELA_OFERTA_ACEITA_MS }
        oferta = null
        return NovaCorrida(aceita)
    }
}
