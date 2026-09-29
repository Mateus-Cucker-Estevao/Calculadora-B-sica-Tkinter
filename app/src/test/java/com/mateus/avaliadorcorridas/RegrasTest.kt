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
        manutencaoPorKm = 0.0, // os testes antigos consideram só o combustível
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
        assertEquals("BLOQUEADA: Cocal do Sul", r.titulo)
        assertTrue(r.fala.contains("Pontos fracos: atenção, corrida para Cocal do Sul"))
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
        assertEquals(
            "Corrida boa. Pontos fortes: passageiro perto, 1,0 quilômetros; valor acima do mínimo, 25 reais; " +
                "lucro de 19 reais e 28 centavos; bom valor por quilômetro, 2 reais e 50 centavos; bom ganho por hora, 88 reais; nota boa, 4,95.",
            r.fala,
        )
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
        assertTrue(r.fala.startsWith("Corrida ruim. Pontos fracos: barata demais"))
    }

    @Test
    fun passageiroLongeFicaVermelho() {
        val o = ExtratorOferta.extrair(oferta("60,00", "12 min (5,5 km)", "15 min (9 km)", "Centro, Criciúma"))
        assertEquals(Cor.VERMELHO, Avaliador.avaliar(o, cfg).cor)
    }

    // ---- Ofertas reais (prints de 26/09/2026) ----

    @Test
    fun ofertaRealExclusivo() {
        val o = ExtratorOferta.extrair(
            listOf(
                "UberX", "Exclusivo", "R$ 9,73", "R$1,60/km aprox.", "4,94 (32)", "Verificado",
                "5 min (2.7 km)", "Rua Desafio Jovem, Recanto Verde, Criciúma",
                "7 minutos (3.4 km)", "Avenida Victor Meireles, 1275, Santa Bárbara, Criciúma", "Aceitar",
            ),
        )
        assertEquals(9.73, o.valor!!, 0.001)
        assertEquals(2.7, o.kmAtePassageiro!!, 0.001)
        assertEquals(3.4, o.kmViagem!!, 0.001)
        assertEquals(7, o.minutosViagem)
        assertEquals("Avenida Victor Meireles, 1275, Santa Bárbara, Criciúma", o.destino)
        val r = Avaliador.avaliar(o, cfg)
        assertEquals(Cor.VERMELHO, r.cor)
        assertEquals(
            "Corrida ruim. Pontos fracos: barata demais, 1 real e 60 centavos por quilômetro. " +
                "No limite: lucro no limite, 6 reais e 24 centavos; por hora no limite, 49 reais. " +
                "Pontos fortes: passageiro perto, 2,7 quilômetros; valor acima do mínimo, 9 reais e 73 centavos; " +
                "nota boa, 4,94.",
            r.fala,
        )
    }

    @Test
    fun ofertaRealPassageiroLonge() {
        val o = ExtratorOferta.extrair(
            listOf(
                "UberX", "R$ 12,40", "R$1,09/km aprox.", "4,74 (41)",
                "17 min (11.4 km)", "R. Luiz Santino Roque, Quarta Linha, Criciúma",
                "<1 min (0 km)", "R. Oitocentos, 48, Quarta Linha, Criciúma", "Selecionar",
            ),
        )
        assertEquals(12.4, o.valor!!, 0.001)
        assertEquals(11.4, o.kmAtePassageiro!!, 0.001)
        assertEquals(0.0, o.kmViagem!!, 0.001)
        assertEquals("R. Oitocentos, 48, Quarta Linha, Criciúma", o.destino)
        // Dois pontos fracos ao mesmo tempo: os dois precisam ser falados.
        val r = Avaliador.avaliar(o, cfg)
        assertEquals(Cor.VERMELHO, r.cor)
        assertTrue(r.fala.contains("passageiro longe, 11,4 quilômetros"))
        assertTrue(r.fala.contains("barata demais, 1 real e 9 centavos por quilômetro"))
        assertTrue(r.fala.contains("pouco por hora, 41 reais por hora"))
        assertTrue(r.fala.contains("No limite: nota do passageiro baixa, 4,74"))
        assertTrue(r.fala.contains("Pontos fortes: valor acima do mínimo, 12 reais e 40 centavos"))
        assertEquals(Cor.VERMELHO, r.pontos.first().cor)
    }

    @Test
    fun valorPorKmNaoEhValorDaCorrida() {
        val o = ExtratorOferta.extrair(listOf("R$1,09/km aprox.", "R$ 12,40", "5 min (2 km)", "10 min (5 km)"))
        assertEquals(12.4, o.valor!!, 0.001)
    }

    @Test
    fun pontosNoLimiteSaoFaladosSeparados() {
        // Busca 3,8 km (entre 3,6 e 4,0), valor R$ 8,50 (entre 8,00 e 8,80) e
        // R$ 8,50 / 4,3 km = R$ 1,98/km (entre 1,80 e 1,98): tudo "no limite".
        val o = ExtratorOferta.extrair(oferta("8,50", "9 min (3,8 km)", "3 min (0,5 km)", "Centro, Criciúma"))
        val r = Avaliador.avaliar(o, cfg.copy(minimoPorHora = 30.0))
        assertEquals(Cor.AMARELO, r.cor)
        assertEquals(
            "Corrida no limite. No limite: passageiro um pouco longe, 3,8 quilômetros; " +
                "valor perto do mínimo, 8 reais e 50 centavos; lucro no limite, 6 reais e 4 centavos; " +
                "no limite, 1 real e 98 centavos por quilômetro. " +
                "Pontos fortes: bom ganho por hora, 43 reais; nota boa, 4,95.",
            r.fala,
        )
    }

    @Test
    fun semPontosFortesAFalaFicaCurta() {
        val o = ExtratorOferta.extrair(oferta("12,50", "8 min (2,3 km)", "15 min (7,4 km)", "Centro, Criciúma"))
        val r = Avaliador.avaliar(o, cfg.copy(falarPontosFortes = false))
        assertEquals(
            "Corrida ruim. Pontos fracos: barata demais, 1 real e 29 centavos por quilômetro; pouco por hora, 33 reais por hora.",
            r.fala,
        )
        assertEquals(6, r.pontos.size) // o texto da tela de teste continua mostrando tudo
    }

    // ---- Painel flutuante (modelo GigU) ----

    @Test
    fun painelDaOfertaDoPrintGigU() {
        // Print enviado em 26/09/2026. O GigU mostrou: 11.40 km, 24 min, $/Km 1.72, $/Hr 49.2, $/Min 0.82, Nota 4.87.
        val o = ExtratorOferta.extrair(
            listOf(
                "UberX", "Exclusivo", "R$ 19,59", "★ 4,87",
                "6 minutos (2.9 km) de distância", "Av. Presidente Kennedy, Santa Isabel", "e arredores",
                "Viagem de 18 minutos (8.5 km)", "Rua Vereador Joel Loureiro, 7690 -",
                "Pedra Mole - Teresina - PI, 64066-050", "Várias paradas", "Aceitar",
            ),
        )
        assertEquals(19.59, o.valor!!, 0.001)
        assertEquals(4.87, o.nota!!, 0.001)
        val p = Avaliador.avaliar(o, cfg).painel!!
        assertEquals("R$ 19,59", p.valor.texto)
        assertEquals("R$ 13,07", p.lucro.texto) // 19,59 − 11,4 km ÷ 11 km/l × R$ 6,29
        assertEquals("11,4 km", p.kmTotal)
        assertEquals("24 min", p.minTotal)
        assertEquals("2,9 km", p.busca.texto)
        assertEquals("1,72", p.porKm.texto)
        assertEquals(Cor.VERMELHO, p.porKm.cor) // 1,72 < 1,80
        assertEquals("49", p.porHora.texto)
        assertEquals(Cor.AMARELO, p.porHora.cor) // 49 fica entre 45 e 49,50
        assertEquals("0,82", p.porMinuto.texto)
        assertEquals("4,87", p.nota.texto)
        assertEquals(Cor.VERDE, p.nota.cor)
        assertNull(p.destinoBloqueado)
    }

    @Test
    fun lucroDescontaManutencao() {
        // 19,59 − 11,4 km × (6,29 ÷ 11 + 0,25) = 19,59 − 6,52 − 2,85 = 10,22
        val o = ExtratorOferta.extrair(
            listOf("R$ 19,59", "6 minutos (2.9 km) de distância", "Viagem de 18 minutos (8.5 km)", "Centro"),
        )
        val p = Avaliador.avaliar(o, cfg.copy(manutencaoPorKm = 0.25)).painel!!
        assertEquals("R$ 10,22", p.lucro.texto)
    }

    @Test
    fun leNotaEmVariosFormatos() {
        fun nota(linha: String) = ExtratorOferta.extrair(listOf("R$ 10,00", linha, "5 min (2 km)", "10 min (5 km)")).nota
        assertEquals(4.87, nota("★ 4,87")!!, 0.001)
        assertEquals(4.87, nota("* 4.87")!!, 0.001)
        assertEquals(4.95, nota("4,95 ★")!!, 0.001)
        assertEquals(4.74, nota("4,74 (41)")!!, 0.001)
        assertNull(nota("R$ 4,50"))
        assertNull(nota("Verificado"))
    }

    @Test
    fun notaBaixaSoDeixaAmarelo() {
        val linhas = listOf("R$ 25,00", "3,90 ★", "2 min (1 km) de distância", "Viagem de 15 min (9 km)", "Centro")
        val r = Avaliador.avaliar(ExtratorOferta.extrair(linhas), cfg)
        assertEquals(Cor.AMARELO, r.cor)
        assertTrue(r.fala.contains("nota do passageiro baixa, 3,90"))
    }

    @Test
    fun falaDinheiro() {
        assertEquals("2 reais e 10 centavos", Avaliador.falaReais(2.10))
        assertEquals("1 real", Avaliador.falaReais(1.0))
        assertEquals("90 centavos", Avaliador.falaReais(0.9))
        assertEquals("R$ 2,10", Avaliador.reais(2.1))
    }
}
