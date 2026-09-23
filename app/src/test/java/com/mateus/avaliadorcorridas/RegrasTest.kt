package com.mateus.avaliadorcorridas

import com.mateus.avaliadorcorridas.dados.Configuracao
import com.mateus.avaliadorcorridas.regras.Avaliador
import com.mateus.avaliadorcorridas.regras.Cor
import com.mateus.avaliadorcorridas.regras.ExtratorOferta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes automáticos das regras de leitura. Rodam no computador (sem celular):
 * no Android Studio, clique com o botão direito neste arquivo → "Run 'RegrasTest'".
 * Quando mudar RegrasExtracao.kt, adicione aqui um exemplo com o texto real do log.
 */
class RegrasTest {

    private val cfg = Configuracao(
        destinosBloqueados = listOf("Cocal do Sul"),
        minimoPorKm = 1.80,
        minimoCorrida = 8.0,
        maxKmAtePassageiro = 4.0,
        margemAmareloPct = 10,
        incluirBuscaNoCalculo = true,
    )

    private fun oferta(valor: String, busca: String, viagem: String, destino: String) = listOf(
        "UberX", "R$ $valor", "4,95 ★",
        "$busca de distância", "Rua Henrique Lage, 100, Centro, Criciúma",
        "Viagem de $viagem", destino, "Aceitar",
    )

    @Test
    fun extraiOfertaCompleta() {
        val o = ExtratorOferta.extrair(oferta("12,50", "8 min (2,3 km)", "15 min (7,4 km)", "Rua São João, 200, Cocal do Sul - SC"))
        assertTrue(o.ehOferta)
        assertEquals(12.5, o.valor!!, 0.001)
        assertEquals(8, o.minutosAtePassageiro)
        assertEquals(2.3, o.kmAtePassageiro!!, 0.001)
        assertEquals(15, o.minutosViagem)
        assertEquals(7.4, o.kmViagem!!, 0.001)
        assertEquals("Rua São João, 200, Cocal do Sul - SC", o.destino)
    }

    @Test
    fun converteMetrosParaKm() {
        val o = ExtratorOferta.extrair(oferta("10,00", "3 min (800 m)", "12 min (5 km)", "Centro, Içara"))
        assertEquals(0.8, o.kmAtePassageiro!!, 0.001)
        assertEquals(5.0, o.kmViagem!!, 0.001)
    }

    @Test
    fun ignoraGanhosDoDiaNoTopoDaTela() {
        val o = ExtratorOferta.extrair(
            listOf("R$ 150,00", "UberX", "R$ 9,80", "5 min (1,2 km) de distância", "Viagem de 10 min (4,0 km)", "Av. Centenário, Criciúma", "Aceitar"),
        )
        assertEquals(9.8, o.valor!!, 0.001)
        assertEquals("Av. Centenário, Criciúma", o.destino)
    }

    @Test
    fun ignoraValorDeDinamicaComMais() {
        val o = ExtratorOferta.extrair(
            listOf("+R$ 3,00 incluído", "R$ 15,00", "5 min (1,2 km) de distância", "Viagem de 10 min (4,0 km)", "Centro"),
        )
        assertEquals(15.0, o.valor!!, 0.001)
    }

    @Test
    fun planoBSemPalavrasChave() {
        val o = ExtratorOferta.extrair(listOf("R$ 20,00", "6 min (2 km)", "Rua A, Centro", "25 min (12 km)", "Rua B, Içara", "Selecionar"))
        assertEquals(2.0, o.kmAtePassageiro!!, 0.001)
        assertEquals(12.0, o.kmViagem!!, 0.001)
        assertEquals("Rua B, Içara", o.destino)
    }

    @Test
    fun appEmIngles() {
        val o = ExtratorOferta.extrair(listOf("R$ 20,00", "4 mins (1.2 mi) away", "20 mins (6 mi) trip", "Downtown"))
        assertEquals(1.2 * 1.609, o.kmAtePassageiro!!, 0.01)
        assertEquals(6 * 1.609, o.kmViagem!!, 0.01)
    }

    @Test
    fun telaInicialNaoEhOferta() {
        val o = ExtratorOferta.extrair(listOf("R$ 150,00", "Você está online", "Procurando viagens"))
        assertFalse(o.ehOferta)
        assertNull(o.kmViagem)
    }

    @Test
    fun destinoBloqueadoFicaVermelho() {
        val o = ExtratorOferta.extrair(oferta("40,00", "2 min (1 km)", "15 min (9 km)", "Rua São João, COCAL DO SUL - SC"))
        val r = Avaliador.avaliar(o, cfg)
        assertEquals(Cor.VERMELHO, r.cor)
        assertEquals("Atenção: corrida para Cocal do Sul", r.fala)
    }

    @Test
    fun bloqueioNaoPegaEnderecoDeBusca() {
        // O passageiro está em Cocal do Sul, mas o destino é Criciúma: não é bloqueio.
        val o = ExtratorOferta.extrair(
            listOf("R$ 25,00", "2 min (1 km) de distância", "Rua X, Cocal do Sul", "Viagem de 15 min (9 km)", "Centro, Criciúma", "Aceitar"),
        )
        assertEquals(Cor.VERDE, Avaliador.avaliar(o, cfg).cor)
    }

    @Test
    fun corridaBoaFicaVerde() {
        val o = ExtratorOferta.extrair(oferta("25,00", "2 min (1 km)", "15 min (9 km)", "Centro, Criciúma"))
        val r = Avaliador.avaliar(o, cfg)
        assertEquals(Cor.VERDE, r.cor)
        assertEquals("Corrida boa: 2 reais e 50 centavos por quilômetro", r.fala)
    }

    @Test
    fun corridaNoLimiteFicaAmarela() {
        val o = ExtratorOferta.extrair(oferta("19,00", "2 min (1 km)", "15 min (9 km)", "Centro, Criciúma"))
        assertEquals(Cor.AMARELO, Avaliador.avaliar(o, cfg).cor)
    }

    @Test
    fun abaixoDoMinimoPorKmFicaVermelho() {
        val o = ExtratorOferta.extrair(oferta("12,50", "8 min (2,3 km)", "15 min (7,4 km)", "Centro, Criciúma"))
        val r = Avaliador.avaliar(o, cfg)
        assertEquals(Cor.VERMELHO, r.cor)
        assertTrue(r.fala.startsWith("Abaixo do mínimo"))
    }

    @Test
    fun passageiroLongeFicaVermelho() {
        val o = ExtratorOferta.extrair(oferta("60,00", "12 min (5,5 km)", "15 min (9 km)", "Centro, Criciúma"))
        assertEquals(Cor.VERMELHO, Avaliador.avaliar(o, cfg).cor)
    }

    @Test
    fun falaDinheiro() {
        assertEquals("2 reais e 10 centavos", Avaliador.falaReais(2.10))
        assertEquals("1 real", Avaliador.falaReais(1.0))
        assertEquals("90 centavos", Avaliador.falaReais(0.9))
        assertEquals("R$ 2,10", Avaliador.reais(2.1))
    }
}
