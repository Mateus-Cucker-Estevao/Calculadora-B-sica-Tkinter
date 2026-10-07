package com.mateus.avaliadorcorridas

import com.mateus.avaliadorcorridas.dados.Corrida
import com.mateus.avaliadorcorridas.dados.Turno
import com.mateus.avaliadorcorridas.regras.Calculos
import com.mateus.avaliadorcorridas.regras.ItemManutencao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Testes das contas do turno (km de deslocamento, combustível, lucro por hora). */
class CalculosTest {

    private val hora = 3_600_000L

    private fun corrida(valor: Double, busca: Double, viagem: Double) =
        Corrida(id = 0, hora = 0, valor = valor, kmBusca = busca, kmViagem = viagem)

    @Test
    fun turnoFechadoCalculaDeslocamentoECombustivel() {
        // 4 horas, odômetro 10.000 → 10.090 (90 km), corridas somando 60 km e R$ 150.
        val t = Turno(
            id = 1, inicio = 0, fim = 4 * hora,
            odometroInicial = 10_000.0, odometroFinal = 10_090.0,
            consumoKmL = 11.0, precoLitro = 6.60,
            corridas = listOf(corrida(50.0, 3.0, 17.0), corrida(60.0, 2.0, 18.0), corrida(40.0, 4.0, 16.0)),
        )
        val r = Calculos.resumo(t, agora = 999 * hora)
        assertEquals(150.0, r.faturamento, 0.001)
        assertEquals(60.0, r.kmCorridas, 0.001)
        assertEquals(90.0, r.kmTotal!!, 0.001)
        assertEquals(30.0, r.kmDeslocamento!!, 0.001)       // 90 − 60
        assertEquals(54.0, r.custoCombustivel, 0.001)       // 90 km ÷ 11 × 6,60
        assertEquals(96.0, r.lucro, 0.001)                  // 150 − 54
        assertEquals(4.0, r.horas, 0.001)
        assertEquals(24.0, r.lucroPorHora!!, 0.001)         // 96 ÷ 4
        assertEquals(2.0 / 3.0, r.aproveitamento!!, 0.001)  // 60 de 90 km pagos
    }

    @Test
    fun turnoAbertoEstimaCustoPelasCorridas() {
        val t = Turno(
            id = 1, inicio = 0, odometroInicial = 10_000.0, consumoKmL = 10.0, precoLitro = 6.0,
            corridas = listOf(corrida(30.0, 2.0, 8.0)),
        )
        val r = Calculos.resumo(t, agora = 2 * hora)
        assertNull(r.kmTotal)
        assertNull(r.kmDeslocamento)
        assertEquals(6.0, r.custoCombustivel, 0.001) // 10 km ÷ 10 × 6
        assertEquals(24.0, r.lucro, 0.001)
        assertEquals(12.0, r.lucroPorHora!!, 0.001)
    }

    @Test
    fun odometroFinalMenorQueCorridasNaoDaDeslocamentoNegativo() {
        val t = Turno(
            id = 1, inicio = 0, fim = hora, odometroInicial = 100.0, odometroFinal = 105.0,
            consumoKmL = 10.0, precoLitro = 6.0, corridas = listOf(corrida(20.0, 2.0, 8.0)),
        )
        assertEquals(0.0, Calculos.resumo(t, hora).kmDeslocamento!!, 0.001)
    }

    @Test
    fun resumoDoMesSomaOsTurnos() {
        val t1 = Turno(1, 0, 2 * hora, 0.0, 50.0, 10.0, 6.0, listOf(corrida(60.0, 5.0, 25.0)))
        val t2 = Turno(2, 10 * hora, 12 * hora, 50.0, 100.0, 10.0, 6.0, listOf(corrida(40.0, 5.0, 15.0)))
        val m = Calculos.resumoMes(listOf(t1, t2), 99 * hora)
        assertEquals(2, m.turnos)
        assertEquals(100.0, m.faturamento, 0.001)
        assertEquals(60.0, m.custos, 0.001)       // 100 km ÷ 10 × 6
        assertEquals(40.0, m.lucro, 0.001)
        assertEquals(100.0, m.kmTotal, 0.001)
        assertEquals(0.5, m.aproveitamento!!, 0.001)
        assertEquals(10.0, m.lucroPorHora!!, 0.001) // 40 ÷ 4 h
    }

    @Test
    fun turnoComManutencao() {
        // Mesmo turno de 90 km, agora com R$ 0,25/km de manutenção: 90 × 0,25 = R$ 22,50 a mais de custo.
        val t = Turno(
            id = 1, inicio = 0, fim = 4 * hora,
            odometroInicial = 10_000.0, odometroFinal = 10_090.0,
            consumoKmL = 11.0, precoLitro = 6.60, manutencaoPorKm = 0.25,
            corridas = listOf(corrida(150.0, 10.0, 50.0)),
        )
        val r = Calculos.resumo(t, agora = 999 * hora)
        assertEquals(54.0, r.custoCombustivel, 0.001)
        assertEquals(22.5, r.custoManutencao, 0.001)
        assertEquals(76.5, r.custoTotal, 0.001)
        assertEquals(73.5, r.lucro, 0.001) // 150 − 76,50
    }

    @Test
    fun calculadoraDeManutencao() {
        val itens = listOf(
            ItemManutencao("Pneus", 1400.0, 40_000.0),   // 0,035/km
            ItemManutencao("Óleo", 280.0, 10_000.0),     // 0,028/km
            ItemManutencao("Sem km", 500.0, 0.0),        // ignorado (km = 0)
        )
        assertEquals(0.063, Calculos.manutencaoPorKm(itens), 0.0001)
        // Os valores de exemplo do app dão ~R$ 0,27/km, dentro da recomendação (R$ 0,20 a 0,30).
        assertEquals(0.27, Calculos.manutencaoPorKm(Calculos.ITENS_MANUTENCAO_EXEMPLO), 0.01)
        assertEquals(0.8218, Calculos.custoPorKm(11.0, 6.29, 0.25), 0.001) // 0,5718 + 0,25
    }
}
