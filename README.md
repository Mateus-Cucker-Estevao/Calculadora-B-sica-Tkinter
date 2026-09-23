# Avaliador de Corridas (Uber Driver)

App Android para uso pessoal. Ele lê a tela de oferta do **Uber Driver** e avisa, por voz e com um
banner colorido, se a corrida atende aos seus critérios.

- ✅ **Só lê e avisa.** Nunca toca em botões, nunca aceita nem recusa corridas.
- ✅ **Nenhum dado sai do celular.** O app nem tem permissão de internet.
- ✅ **Não guarda dados de passageiros** (só o modo diagnóstico grava texto, e só quando você liga).

---

## Sumário

0. [Jeito mais rápido: baixar o APK pronto do GitHub](#0-jeito-mais-rápido-baixar-o-apk-pronto-do-github)
1. [Instalar o Android Studio e abrir o projeto](#1-instalar-o-android-studio-e-abrir-o-projeto)
2. [Estrutura do projeto (o que cada arquivo faz)](#2-estrutura-do-projeto)
3. [Gerar o APK e instalar no celular](#3-gerar-o-apk-e-instalar-no-celular)
4. [Ativar o serviço de acessibilidade](#4-ativar-o-serviço-de-acessibilidade)
5. [Usar o app](#5-usar-o-app)
6. [Modo diagnóstico: calibrar a leitura das ofertas](#6-modo-diagnóstico-calibrar-a-leitura-das-ofertas)
7. [Problemas comuns](#7-problemas-comuns)

---

## 0. Jeito mais rápido: baixar o APK pronto do GitHub

Este repositório tem uma "linha de montagem" automática (GitHub Actions) que gera o APK sempre que
o código muda. Você não precisa instalar nada no computador para testar.

1. No celular (ou no computador), abra o repositório no GitHub e entre na aba **Actions**.
2. Toque na execução mais recente chamada **"Gerar APK"** (precisa ter o ✅ verde).
3. Role até **Artifacts** e baixe **AvaliadorCorridas-apk** (vem em um `.zip`).
4. Abra o `.zip` no gerenciador de arquivos do celular e toque em **AvaliadorCorridas.apk**.
5. Pule para o passo [3.3 — Instalar no celular](#33-instalar-no-celular).

> Você precisa estar logado no GitHub para baixar os "Artifacts".

---

## 1. Instalar o Android Studio e abrir o projeto

Faça isso quando quiser **alterar** o app (por exemplo, ajustar as regras de leitura).

### 1.1 Instalar

1. Acesse **https://developer.android.com/studio** e baixe o Android Studio.
2. Instale com as opções padrão (Next, Next, Finish).
3. Na primeira abertura, escolha **Standard** no assistente. Ele baixa o Android SDK sozinho
   (demora alguns minutos e precisa de uns 5 GB livres).

### 1.2 Baixar este projeto

Opção A, sem Git: no GitHub, clique em **Code → Download ZIP** e descompacte a pasta.

Opção B, com Git:

```bash
git clone https://github.com/Mateus-Cucker-Estevao/Calculadora-B-sica-Tkinter.git
```

### 1.3 Abrir

1. No Android Studio: **File → Open** (ou "Open" na tela inicial).
2. Selecione a **pasta do projeto** (a que tem o arquivo `settings.gradle.kts`).
3. Aguarde o "Gradle Sync" terminar (barra de progresso embaixo). Na primeira vez demora mais.

> **Não precisa criar um projeto novo.** O projeto já está pronto aqui. Se mesmo assim quiser criar
> do zero: *New Project → Empty Activity*, nome "AvaliadorCorridas", pacote
> `com.mateus.avaliadorcorridas`, linguagem Kotlin, Minimum SDK "API 29 (Android 10)". Depois
> copie os arquivos deste repositório por cima.

---

## 2. Estrutura do projeto

```
├── settings.gradle.kts            ← nome do projeto e de onde baixar bibliotecas
├── build.gradle.kts               ← versões dos plugins (Android, Kotlin)
├── gradle.properties              ← opções do Gradle
├── gradlew / gradlew.bat / gradle/ ← "Gradle Wrapper": baixa o Gradle certo automaticamente
├── .github/workflows/gerar-apk.yml ← gera o APK automaticamente no GitHub
└── app/
    ├── build.gradle.kts           ← configuração do app (SDK mínimo, bibliotecas)
    ├── debug.keystore             ← chave de assinatura fixa (uso pessoal)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml         ← "RG" do app: telas, serviços, permissões
        │   ├── res/xml/accessibility_service_config.xml ← diz que o serviço só olha o Uber
        │   ├── res/xml/file_paths.xml      ← permite compartilhar o log
        │   ├── res/values/…                ← textos, cores e tema
        │   ├── res/drawable/, mipmap-…/    ← ícones
        │   └── java/com/mateus/avaliadorcorridas/
        │       ├── regras/
        │       │   ├── RegrasExtracao.kt   ★ AS REGRAS DE LEITURA (edite aqui!)
        │       │   ├── ExtratorOferta.kt   ← aplica as regras e monta a "Oferta"
        │       │   └── Avaliador.kt        ← decide verde/amarelo/vermelho e o que falar
        │       ├── dados/
        │       │   ├── Configuracao.kt     ← seus critérios (valores padrão)
        │       │   ├── Preferencias.kt     ← salva os critérios no celular
        │       │   └── LogDiagnostico.kt   ← arquivo de log do modo diagnóstico
        │       ├── servico/
        │       │   ├── OfertaAccessibilityService.kt ← lê a tela do Uber em segundo plano
        │       │   ├── Falador.kt          ← voz em português (Text-to-Speech)
        │       │   ├── BannerSobreposto.kt ← banner colorido no topo da tela
        │       │   └── MonitorTileService.kt ← botão liga/desliga nas Configurações rápidas
        │       └── ui/
        │           └── MainActivity.kt     ← tela principal (Jetpack Compose)
        └── test/…/RegrasTest.kt            ← testes automáticos das regras de leitura
```

### Como o app funciona, em 5 passos

1. O Uber mostra uma oferta → o Android avisa o nosso **serviço de acessibilidade**
   (ele só recebe avisos do pacote `com.ubercab.driver`).
2. O serviço junta todos os textos da tela em uma lista de linhas.
3. O **ExtratorOferta** usa as regras do **RegrasExtracao.kt** para achar valor, distâncias e destino.
4. O **Avaliador** compara com seus critérios, nesta ordem:
   1. destino bloqueado → 🔴 "Atenção: corrida para Cocal do Sul"
   2. passageiro mais longe que o máximo → 🔴 "Passageiro longe"
   3. valor abaixo do mínimo da corrida → 🔴 "Abaixo do mínimo"
   4. distância da viagem não lida → 🟡 "Distância não lida"
   5. R$/km abaixo do mínimo → 🔴 "Abaixo do mínimo: … por quilômetro"
   6. até X% acima de algum limite (a "margem do amarelo") → 🟡 "No limite: … por quilômetro"
   7. senão → 🟢 "Corrida boa: 2 reais e 10 centavos por quilômetro"
5. O app fala o aviso e mostra o banner por alguns segundos.

**R$/km** = valor ÷ (distância até o passageiro + distância da viagem). Dá para desligar a opção
"Contar a distância até o passageiro no R$/km" para usar só a distância da viagem.

---

## 3. Gerar o APK e instalar no celular

### 3.1 Pelo Android Studio (mais fácil)

1. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
2. Quando terminar, aparece um aviso no canto: clique em **locate**.
3. O arquivo é `app/build/outputs/apk/debug/app-debug.apk`.

### 3.2 Pelo terminal

```bash
./gradlew assembleDebug        # Linux/Mac
gradlew.bat assembleDebug      # Windows
```

### 3.3 Instalar no celular

1. Leve o APK para o celular: cabo USB, Google Drive, WhatsApp para você mesmo… ou baixe direto do
   GitHub (passo 0).
2. Toque no arquivo `.apk`.
3. O Android vai pedir para **permitir instalar apps desta fonte** (ex.: "Arquivos" ou "Chrome").
   Toque em **Configurações → ativar "Permitir desta fonte"** e volte.
4. Toque em **Instalar**. Se o Play Protect avisar, toque em **Mais detalhes → Instalar mesmo assim**
   (é normal para apps que não vêm da Play Store).

> **Atualizar:** instale o APK novo por cima do antigo. Suas configurações são mantidas.

### 3.4 Alternativa: instalar direto pelo cabo (Android Studio)

1. No celular: **Configurações → Sobre o telefone → toque 7 vezes em "Número da versão"**
   para liberar as "Opções do desenvolvedor".
2. **Configurações → Sistema → Opções do desenvolvedor → Depuração USB: ativar**.
3. Ligue o cabo, aceite a pergunta no celular e clique no ▶ (Run) verde do Android Studio.

---

## 4. Ativar o serviço de acessibilidade

1. Abra o app **Avaliador de Corridas**.
2. Toque em **Abrir configurações de acessibilidade**.
3. Procure **Avaliador de Corridas** (às vezes fica dentro de **Apps instalados** ou
   **Serviços baixados**) e ative. Confirme em **Permitir**.

### ⚠️ "Configuração restrita" (Android 13 ou mais novo)

Apps instalados por APK têm a acessibilidade bloqueada até você liberar:

1. No app, toque em **Abrir informações do app**
   (ou: Configurações → Apps → Avaliador de Corridas).
2. Toque nos **3 pontinhos (⋮)** no canto de cima → **Permitir configurações restritas**.
   (Se não aparecer, tente primeiro ativar a acessibilidade uma vez; aí a opção aparece.)
3. Volte para a acessibilidade e ative o serviço.

### E a permissão de sobreposição de tela?

**Não é necessária.** O banner usa uma janela especial que só serviços de acessibilidade podem
criar (`TYPE_ACCESSIBILITY_OVERLAY`). Ativando a acessibilidade, o banner já funciona.
Ele também é "não tocável": seus toques passam direto para o Uber.

### Voz em português

Se a voz sair em outro idioma ou não sair: **Configurações → Sistema → Idiomas → Saída de
texto para fala (Text-to-speech)** → escolha o "Speech Services by Google" → **Instalar dados de
voz → Português (Brasil)**.

---

## 5. Usar o app

1. Ajuste os **Critérios** e toque em **Salvar critérios**.
2. Adicione os **Destinos bloqueados** (maiúsculas e acentos não importam).
3. Use **Testar leitura** para ouvir a voz e ver o banner sem precisar de uma oferta real.
   O exemplo já vem com "Cocal do Sul" e deve dar 🔴 "Atenção: corrida para Cocal do Sul".
4. Toque no botão grande para **LIGAR** o monitoramento e abra o Uber Driver.

**Ligar/desligar rápido:** puxe a cortina de notificações duas vezes → ícone de lápis (editar) →
arraste o botão **Avaliador** para a área ativa. Um toque liga/desliga e o app fala o novo estado.

---

## 6. Modo diagnóstico: calibrar a leitura das ofertas

O layout do Uber muda com frequência. O modo diagnóstico mostra **exatamente** o texto que o app
lê, para você ajustar as regras.

### 6.1 Coletar

1. Na tela do app, ligue **Modo diagnóstico → Gravar o texto lido da tela do Uber**.
   (Funciona mesmo com o monitoramento desligado.)
2. Fique online no Uber e espere **algumas ofertas** aparecerem (não precisa aceitar).
3. Volte ao app e toque em **Ver log**.

### 6.2 Ler o log

Cada "foto" da tela aparece assim:

```
===== 23/09 18:42:10 =====
[00] UberX
[01] R$ 18,50
[02] 4,95 ★
[03] 6 min (2,1 km) de distância
[04] Rua Henrique Lage, Centro, Criciúma
[05] Viagem de 18 min (8,4 km)
[06] Rua São João, Cocal do Sul - SC
[07] Aceitar
--> LEITURA: OFERTA  valor=18.5 | busca=2.1 km (6 min) | viagem=8.4 km (18 min) | destino=Rua São João, Cocal do Sul - SC
```

- As linhas `[00]`, `[01]`… são os textos da tela, na ordem em que o app os encontrou.
- A linha **`--> LEITURA`** mostra o que o app entendeu. Se algum campo estiver com `?` (ou errado)
  numa oferta de verdade, a regra correspondente precisa de ajuste.

### 6.3 Ajustar as regras

Tudo fica em **`app/src/main/java/com/mateus/avaliadorcorridas/regras/RegrasExtracao.kt`**:

| O que não foi lido | Onde mexer |
|---|---|
| Valor | `VALOR` |
| Distância até o passageiro | `DISTANCIA_ATE_PASSAGEIRO` (lista de regras) |
| Distância da viagem | `DISTANCIA_VIAGEM` (lista de regras) |
| Destino | `DESTINO_COM_ROTULO` e `LINHAS_QUE_NAO_SAO_DESTINO` |

**Exemplo:** se o log mostra `Busca de 6 min • 2,1 km` (formato novo), adicione esta regra à lista
`DISTANCIA_ATE_PASSAGEIRO`:

```kotlin
Regex("""busca\s+de\s+$TEMPO\s*•\s*$DIST""", I),
```

`$TEMPO` e `$DIST` são pedaços prontos, definidos no começo do arquivo: eles já entendem
"6 min", "2,1 km", "800 m" etc. Mantenha os nomes `min` e `km`, porque o código usa esses nomes.

### 6.4 Conferir sem sair dirigindo

1. No **Ver log**, toque e segure para selecionar e copiar as linhas de uma oferta.
2. Cole na seção **Testar leitura** (pode colar com os `[00]`, o app remove) e toque em **Testar**.
3. Veja se a "Leitura" saiu certa. Se não saiu, ajuste a regra, gere o APK de novo e repita.

Dica: acrescente o texto real no arquivo `app/src/test/.../RegrasTest.kt` como um novo teste.
Assim você garante que uma mudança futura não estraga a leitura (no Android Studio: botão direito
no arquivo → **Run 'RegrasTest'**).

### 6.5 Terminou? Limpe

O log pode conter o nome do passageiro. Depois de calibrar, **desligue o modo diagnóstico** e toque
em **Apagar**. O botão **Compartilhar** só envia o arquivo se você escolher para onde.

---

## 7. Problemas comuns

| Problema | Solução |
|---|---|
| Não fala nada | Confira se o serviço está ativo, se o botão está LIGADO e se "Aviso por voz" está ligado. Veja a voz em português (passo 4). |
| O log fica vazio | O serviço de acessibilidade está desligado, ou o Uber não estava na tela. Alguns celulares desligam serviços para economizar bateria: Configurações → Apps → Avaliador de Corridas → Bateria → **Sem restrições**. |
| O serviço desliga sozinho (Xiaomi/Samsung) | Tire o app da otimização de bateria (acima) e, na Xiaomi, ative **Início automático**. |
| Avisa na tela errada / não avisa | Use o modo diagnóstico (passo 6) e ajuste `RegrasExtracao.kt`. |
| "App não instalado" ao atualizar | Desinstale a versão antiga e instale a nova (acontece se o APK foi assinado com outra chave). |
