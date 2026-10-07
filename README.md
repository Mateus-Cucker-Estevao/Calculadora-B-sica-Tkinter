# Avaliador de Corridas (Uber Driver)

App Android para uso pessoal, organizado **por turnos**. Durante o turno ele lê as ofertas do
**Uber Driver** pela imagem da tela, avisa por voz e com um aviso colorido se a corrida vale a pena,
e no fim mostra o **lucro real** do turno — descontando o combustível de **todos** os km rodados,
inclusive os de deslocamento sem passageiro.

- ✅ **Só lê e avisa.** Nunca toca em botões, nunca aceita nem recusa corridas.
- ✅ **Nada sai do celular.** O app nem tem permissão de internet. Turnos ficam num arquivo local.
- ✅ **Não guarda dados de passageiros**: as corridas têm só números (valor, km, minutos, nota).

---

## Sumário

1. [Baixar e instalar](#1-baixar-e-instalar)
2. [Permissões](#2-permissões)
3. [Como usar: turnos](#3-como-usar-turnos)
4. [Parâmetros](#4-parâmetros)
5. [Como as contas são feitas](#5-como-as-contas-são-feitas)
6. [Calibrar a leitura (modo diagnóstico)](#6-calibrar-a-leitura-modo-diagnóstico)
7. [Estrutura do projeto](#7-estrutura-do-projeto)
8. [Gerar o APK no Android Studio](#8-gerar-o-apk-no-android-studio)
9. [Problemas comuns](#9-problemas-comuns)

---

## 1. Baixar e instalar

O GitHub gera o APK sozinho a cada mudança no código:

1. Abra o repositório no GitHub → aba **Actions** → execução mais recente **"Gerar APK"** (✅ verde).
2. Em **Artifacts**, baixe **AvaliadorCorridas-apk** (um `.zip`) e extraia o `AvaliadorCorridas.apk`.
3. Instale:
   - **Pelo computador (recomendado):** ative a *Depuração USB* no celular (Configurações → Sobre o
     telefone → toque 7 vezes em "Número da versão" → Opções do desenvolvedor → Depuração USB),
     baixe o [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools),
     ligue o cabo e rode `adb install -r AvaliadorCorridas.apk`.
   - **Pelo celular:** toque no APK e permita instalar desta fonte. Se o Play Protect bloquear,
     use o caminho pelo computador.

> Atualizar: instale o APK novo por cima (`adb install -r ...`). Seus turnos e parâmetros ficam.

## 2. Permissões

| Permissão | Para quê | Quando |
|---|---|---|
| **Sobrepor a outros apps** | Mostrar o aviso colorido por cima do Uber | Uma vez (o app pede no primeiro turno, ou em Parâmetros) |
| **Gravar/compartilhar a tela** | Ler as ofertas pela imagem | **A cada turno** (o Android exige). Escolha **"Tela inteira"** e toque em **Iniciar** |

Não precisa mais de acessibilidade nem de "configurações restritas".

**Voz em português:** Configurações → Sistema → Idiomas → Saída de texto para fala → instale os dados
de voz **Português (Brasil)**.

## 3. Como usar: turnos

**Tela Turnos** (igual a um app de finanças):

- No topo, o **mês** (setas ‹ › para trocar) e o estado: **Monitorando**, **Leitura parada** ou **Parado**.
- **Resumo do mês:** lucro real líquido, faturamento, custos (combustível), km rodados,
  aproveitamento pago (% dos km que foram em corrida), turnos, horas e média de lucro por hora.

**Começar:** toque em **＋ Novo turno** → digite o **KM do odômetro** do painel do carro → autorize a
gravação da tela. Pronto: abra o Uber e fique online.

Durante o turno:
- Cada oferta aparece com o **aviso** no topo da tela (enquanto a oferta estiver aparecendo):

  ```
  ┌──────────── borda verde / amarela / vermelha ────────────┐
  │    ✅ Valor      │    ❌ R$/km     │     ✅ Nota          │
  │    R$ 19,59      │      1,72       │      4,87            │
  │ ✅ lucro R$ 13,07 │                 │                      │
  │ ─────────────────────────────────────────────────────── │
  │ ✅ Até passageiro │ Distância total │ Tempo estimado       │
  │      2,9 km       │    11,4 km      │    24 min            │
  └───────────────────────────────────────────────────────────┘
  ```
- A **voz** fala o diagnóstico (pontos fracos, no limite e fortes).
- Quando você **aceita** uma oferta, ela entra sozinha como corrida do turno (veja a seção 6).
- O cartão **Turno em andamento** mostra corridas, faturamento e lucro estimado. Se a leitura parar
  (celular reiniciou, você tocou em "Parar compartilhamento"...), toque em **Retomar leitura**.

**Terminar:** toque em **Finalizar turno** → digite o **Km do odômetro final**. O turno aparece na
lista do mês, **separado por dia**. Toque num turno para ver os detalhes e:
- **editar** odômetros, consumo e preço do combustível;
- **adicionar, editar ou excluir corridas** (se alguma não foi registrada sozinha);
- **excluir** o turno.

## 4. Parâmetros

- **Custos do carro:**
  - **Combustível:** consumo real (km/l) e preço (R$/L).
  - **Manutenção (R$ por km):** pneus, óleo, revisões, freios, limpeza... Recomendado para carro popular
    1.0: **R$ 0,20 a R$ 0,30 por km**. O botão **🧮 Calcular manutenção por km** abre uma janela onde você
    lista cada gasto (quanto custa e quantos km dura) e o app calcula e preenche o valor.
    Conta simples: *custo ÷ km que dura* para cada item, somando tudo (pneus de R$ 1.400 que duram
    40.000 km = R$ 0,035/km). Ou: tudo que gastou com o carro em 6 meses ÷ km rodados no período.
  - Cada turno guarda os valores do dia em que começou (mudar depois não altera turnos antigos; dá para
    editar no turno).
- **Critérios das ofertas:** R$/km mínimo, valor mínimo, **lucro mínimo** (já sem combustível),
  distância máxima até o passageiro, R$/h mínimo, nota mínima, margem do amarelo.
- **Destinos bloqueados**, **aviso e voz**, **permissão do aviso**, **testar leitura** (ouve a voz e vê o
  aviso sem precisar de oferta real) e **modo diagnóstico**.

## 5. Como as contas são feitas

**Por oferta:**
- **R$/km** = valor ÷ (km até o passageiro + km da viagem).
- **R$/h** = valor ÷ (minutos de busca + viagem) × 60.
- **Lucro** = valor − (km até o passageiro + km da viagem) × custo por km
  (custo por km = preço do combustível ÷ consumo + manutenção por km).
- Cada critério vira ✅ (bom), ⚠️ (no limite: até X% do limite) ou ❌ (ruim). A borda do aviso tem a
  cor do pior critério. Nota baixa só deixa amarelo.

**Por turno:**
- **Km total** = odômetro final − odômetro inicial.
- **Km de deslocamento** = km total − km das corridas (o que você rodou sem ser pago).
- **Combustível** = km total ÷ consumo × preço. **Manutenção** = km total × manutenção por km.
  (Com o turno aberto, os dois são estimados só pelos km das corridas.)
- **Lucro real** = soma das corridas − combustível − manutenção. **Lucro/h** = lucro ÷ horas do turno.
- **Aproveitamento pago** = km das corridas ÷ km total.

## 6. Calibrar a leitura (modo diagnóstico)

As regras de leitura ficam em
**`app/src/main/java/com/mateus/avaliadorcorridas/regras/RegrasExtracao.kt`**:

| O quê | Onde |
|---|---|
| Valor, distâncias, destino, nota | `VALOR`, `DISTANCIA_ATE_PASSAGEIRO`, `DISTANCIA_VIAGEM`, `DESTINO_COM_ROTULO`, `NOTA` |
| **Telas da corrida** (busca, viagem, fim) | `TELAS_BUSCANDO_PASSAGEIRO`, `TELAS_EM_VIAGEM`, `TELAS_SEM_CORRIDA` ✅ calibrado (log de 05/10/2026) |
| Erros de leitura (vírgula perdida, valores absurdos) | `VALOR_MINIMO_VALIDO`, `VALOR_MAXIMO_VALIDO`, `VELOCIDADE_MAXIMA_KMH` |

**Como as corridas entram no turno:** depois de aceitar, o Uber mostra **"Encontro com [nome]"** →
**"Iniciar UberX"** → **"Encerrar UberX"** / **"Destino de [nome]"** → **"Como foi a viagem?"**. Quando o
app vê "Encontro com...", registra a corrida com a última oferta lida (até 3 min antes). Se não leu a
oferta, a corrida entra com ⚠️ **para revisar**: abra o turno e preencha valor e km no lápis.

Para calibrar:
1. Em Parâmetros, ligue o **Modo diagnóstico** e apague o log.
2. Faça um turno normal: aceite e conclua pelo menos uma corrida.
3. Em **Ver log**, linhas `##### CORRIDA registrada` mostram quando o app registrou uma corrida.
   Se o Uber mudar as telas, veja as palavras novas no log e ajuste as listas `TELAS_*`
   (ou mande o log para ajustar).
4. Use **Testar leitura** para conferir textos de oferta sem sair de carro.
5. Terminou? Desligue o diagnóstico e **apague** o log (pode conter o nome do passageiro).

## 7. Estrutura do projeto

```
app/src/main/java/com/mateus/avaliadorcorridas/
├── regras/
│   ├── RegrasExtracao.kt   ★ regras de leitura (edite aqui)
│   ├── ExtratorOferta.kt   ← aplica as regras ao texto lido
│   ├── Avaliador.kt        ← critérios, cores, voz e painel do aviso
│   └── Calculos.kt         ← contas do turno e do mês
├── dados/
│   ├── Configuracao.kt / Preferencias.kt   ← parâmetros
│   ├── Turno.kt / RepositorioTurnos.kt     ← turnos e corridas (arquivo turnos.json local)
│   └── LogDiagnostico.kt                   ← log do modo diagnóstico
├── servico/
│   ├── MonitorService.kt   ← durante o turno: captura a tela, lê (ML Kit offline), avisa, registra corridas
│   ├── BannerSobreposto.kt ← aviso flutuante
│   └── Falador.kt          ← voz em português
└── ui/
    ├── MainActivity.kt     ← navegação Turnos / Parâmetros
    ├── TelaTurnos.kt       ← tela do mês e turno em andamento
    ├── TelaDetalheTurno.kt ← detalhes, edição e exclusão
    ├── TelaParametros.kt   ← parâmetros, teste e diagnóstico
    └── Tema.kt / Componentes.kt
app/src/test/…              ← testes automáticos (leitura, avaliação e contas)
```

## 8. Gerar o APK no Android Studio

1. Instale o [Android Studio](https://developer.android.com/studio) (opções padrão).
2. **File → Open** → pasta do projeto (a que tem `settings.gradle.kts`) → aguarde o Gradle Sync.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)** → `app/build/outputs/apk/debug/app-debug.apk`.
4. Testes: botão direito em `app/src/test` → **Run Tests**.

## 9. Problemas comuns

| Problema | Solução |
|---|---|
| Não aparece o aviso | Parâmetros → "Aviso por cima do Uber" → permitir. |
| "Leitura parada" | Toque em **Retomar leitura** e autorize "Tela inteira". |
| Não fala nada | Confira "Aviso por voz", o volume de mídia e a voz em português. |
| Corridas não entram no turno | Mande um log (seção 6) ou adicione à mão no turno. |
| Corrida com ⚠️ "para revisar" | O app não leu a oferta (ex.: chegou com o app aberto). Preencha valor e km no turno. |
| O serviço para sozinho (Xiaomi/Samsung) | Configurações → Apps → Avaliador → Bateria → **Sem restrições**. |
