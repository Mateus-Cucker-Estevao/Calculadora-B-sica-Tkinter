package com.mateus.avaliadorcorridas

import com.mateus.avaliadorcorridas.regras.DetectorCorridas
import com.mateus.avaliadorcorridas.regras.ExtratorOferta
import com.mateus.avaliadorcorridas.regras.FaseCorrida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Telas reais do Uber (log de 05/10/2026, leitura por imagem, nomes trocados).
 * Testam a detecção das corridas e as correções de erros de leitura.
 */
class CorridasTest {

    private val s = 1000L
    private val min = 60 * s

    // ---- Telas copiadas do log ----
    private val oferta620 = listOf(
        "2 UberX", "R$ 6,20", "R$2,30/km aprox.", "4,95 (120)", "2 min (0.8 km)",
        "Rua A, Centro,", "Criciúma", "5 minutos (1.9 km)", "R. Osvaldo Pinto da Veiga, 1575,", "Aceitar",
    )
    private val mapaIndoBuscar = listOf("Rua Oscar", "Pedro Mariano", "LIMITE", "Avenida Centenário", "l min O 0,6 km", "Encontro com Maria")
    private val noEmbarque = listOf("2EDRO MARIAN0", "l min e 0,2 km", "O Verificado", "Maria", "Iniciar UberX")
    private val emViagem = listOf("VENID", "Navegar", "4 min e 2 km", "O Verificado", "Maria", "Encerrar UberX")
    private val destino = listOf("Avenida Victor Meireles", "l min 0,1 km", "Destino de Maria")
    private val avaliacao = listOf("1-15 min", "Como foi a viagem?", "Maria", "Avaliar usuário")
    private val procurando = listOf("Arena Criciúma", "Procurando viagens", "O passe de 24 horas está", "ativo")
    private val mapa = listOf("Avenida Centenário", "175 m", "Rua Carlos Gomes", "Google Maps", "Waze")

    @Test
    fun reconheceAsTelasDaCorrida() {
        assertEquals(FaseCorrida.BUSCANDO_PASSAGEIRO, ExtratorOferta.faseDaTela(mapaIndoBuscar))
        assertEquals(FaseCorrida.BUSCANDO_PASSAGEIRO, ExtratorOferta.faseDaTela(noEmbarque))
        assertEquals(FaseCorrida.BUSCANDO_PASSAGEIRO, ExtratorOferta.faseDaTela(listOf("lniciar uberX")))
        assertEquals(FaseCorrida.BUSCANDO_PASSAGEIRO, ExtratorOferta.faseDaTela(listOf("Cheguei Ok, entendi", "Estou a caminho")))
        assertEquals(FaseCorrida.EM_VIAGEM, ExtratorOferta.faseDaTela(emViagem))
        assertEquals(FaseCorrida.EM_VIAGEM, ExtratorOferta.faseDaTela(destino))
        assertEquals(FaseCorrida.EM_VIAGEM, ExtratorOferta.faseDaTela(listOf("A caminho da última parada", "Davi", "Encerrar UberX")))
        assertEquals(FaseCorrida.SEM_CORRIDA, ExtratorOferta.faseDaTela(avaliacao))
        assertEquals(FaseCorrida.SEM_CORRIDA, ExtratorOferta.faseDaTela(procurando))
        assertNull(ExtratorOferta.faseDaTela(mapa))
    }

    @Test
    fun corridaCompletaRegistraUmaVezComAOfertaAceita() {
        val d = DetectorCorridas()
        val o = ExtratorOferta.extrair(oferta620)
        d.ofertaVista(o, 0)
        assertNull(d.telaLida(mapa, 5 * s))
        val nova = d.telaLida(mapaIndoBuscar, 15 * s)          // "Encontro com..." 15 s depois da oferta
        assertNotNull(nova)
        assertEquals(6.2, nova!!.oferta!!.valor!!, 0.001)
        assertEquals(1.9, nova.oferta!!.kmViagem!!, 0.001)
        // O resto da mesma corrida não registra de novo (no log, "Encontro com" volta depois do "Iniciar").
        assertNull(d.telaLida(noEmbarque, 56 * s))
        assertNull(d.telaLida(mapaIndoBuscar, 66 * s))
        assertNull(d.telaLida(noEmbarque, 87 * s))
        assertNull(d.telaLida(emViagem, 97 * s))
        assertNull(d.telaLida(destino, 5 * min))
        assertNull(d.telaLida(avaliacao, 6 * min))
        assertNull(d.telaLida(procurando, 7 * min))
        assertEquals(FaseCorrida.SEM_CORRIDA, d.fase)
    }

    @Test
    fun corridaSemOfertaLidaFicaParaRevisar() {
        val d = DetectorCorridas()
        d.ofertaVista(ExtratorOferta.extrair(oferta620), 0)
        // A próxima corrida começa 12 min depois, sem oferta lida (no log: corrida das 20:27).
        val nova = d.telaLida(mapaIndoBuscar, 12 * min)
        assertNotNull(nova)
        assertNull(nova!!.oferta)
    }

    @Test
    fun corridaEmSequenciaDepoisDaViagemContaComoNova() {
        val d = DetectorCorridas()
        d.ofertaVista(ExtratorOferta.extrair(oferta620), 0)
        assertNotNull(d.telaLida(mapaIndoBuscar, 10 * s))
        assertNull(d.telaLida(emViagem, 3 * min))
        // Oferta da próxima viagem chega durante a viagem e o Uber vai direto para "Encontro com".
        d.ofertaVista(ExtratorOferta.extrair(oferta620.map { it.replace("6,20", "9,10") }), 5 * min)
        val segunda = d.telaLida(mapaIndoBuscar, 5 * min + 12 * s)
        assertEquals(9.1, segunda!!.oferta!!.valor!!, 0.001)
    }

    @Test
    fun leituraPiorDaMesmaOfertaNaoSubstituiAMelhor() {
        val d = DetectorCorridas()
        d.ofertaVista(ExtratorOferta.extrair(oferta620), 0)
        // Mesma oferta lida com erro (sem a distância da viagem).
        d.ofertaVista(ExtratorOferta.extrair(listOf("R$ 6,20", "2 min (0.8 km)", "Aceitar")), 1 * s)
        val nova = d.telaLida(mapaIndoBuscar, 10 * s)
        assertEquals(1.9, nova!!.oferta!!.kmViagem!!, 0.001)
    }

    // ---- Erros de leitura da imagem (casos reais do log) ----

    @Test
    fun valorSemVirgulaEValorPorKmMalLido() {
        // Log 20:14:54: "R$ 1552" (era R$ 15,52) e "R$1l,96/km" (lido como R$ 1,00).
        val o = ExtratorOferta.extrair(
            listOf(
                "2 UberX", "R$ 1552", "RE R$1l,96/km aprox.", "4 min (2.2 km)", "R Miguel Ana Maria,",
                "10 minutos (5.7 km)", "Rua Abilio Paulo 270, Centro,", "Selecionar",
            ),
        )
        assertEquals(15.52, o.valor!!, 0.001)
    }

    @Test
    fun distanciaSemVirgula() {
        // Log 21:18:15: "8 minutos (33 km)" — era 3,3 km. Log 22:06:08: "10 minutos (52 km)" — era 5,2 km.
        val o = ExtratorOferta.extrair(listOf("R$ 12,76", "3 min (1.3 km)", "Rua X", "8 minutos (33 km)", "Criciúma"))
        assertEquals(3.3, o.kmViagem!!, 0.001)
        val o2 = ExtratorOferta.extrair(listOf("R$ 15,04", "3 min (0.9 km)", "Rua X", "10 minutos (52 km)", "Centro"))
        assertEquals(5.2, o2.kmViagem!!, 0.001)
        // Distância longa de verdade não é alterada.
        val o3 = ExtratorOferta.extrair(listOf("R$ 40,00", "5 min (2 km)", "Rua X", "25 minutos (18 km)", "Içara"))
        assertEquals(18.0, o3.kmViagem!!, 0.001)
    }

    @Test
    fun parenteseFinalFaltando() {
        // "4 min (2.2 km" sem o ")" continua sendo lido como a busca.
        val o = ExtratorOferta.extrair(listOf("R$ 15,52", "4 min (2.2 km", "Rua X", "10 minutos (5.7 km)", "Centro"))
        assertEquals(2.2, o.kmAtePassageiro!!, 0.001)
        assertEquals(5.7, o.kmViagem!!, 0.001)
    }
}
