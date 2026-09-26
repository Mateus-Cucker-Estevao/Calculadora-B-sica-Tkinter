package com.mateus.avaliadorcorridas.dados

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Arquivo de log do MODO DIAGNÓSTICO.
 *
 * Fica na memória interna do app (nenhum outro app consegue ler) e só sai do
 * celular se você mesmo tocar em "Compartilhar". O tamanho é limitado: quando passa
 * de [TAMANHO_MAXIMO], a metade mais antiga é apagada.
 *
 * Atenção: o log guarda TODO o texto da tela do Uber, e isso pode incluir o nome
 * do passageiro. Use o modo diagnóstico só para calibrar e depois toque em "Apagar".
 */
object LogDiagnostico {

    private const val NOME_ARQUIVO = "diagnostico.txt"
    private const val TAMANHO_MAXIMO = 1_000_000L // ~1 MB

    private val gravador = Executors.newSingleThreadExecutor()
    private val formatoHora = SimpleDateFormat("dd/MM HH:mm:ss", Locale.forLanguageTag("pt-BR"))

    fun arquivo(ctx: Context): File = File(ctx.filesDir, NOME_ARQUIVO)

    /** Grava uma "foto" do texto da tela (em segundo plano, sem travar o celular). */
    fun registrar(ctx: Context, linhas: List<String>, leitura: String) {
        val app = ctx.applicationContext
        val bloco = buildString {
            append("===== ").append(formatoHora.format(Date())).append(" =====\n")
            linhas.forEachIndexed { i, l -> append(String.format(Locale.ROOT, "[%02d] ", i)).append(l).append('\n') }
            append("--> LEITURA: ").append(leitura).append("\n\n")
        }
        gravador.execute {
            val f = arquivo(app)
            f.appendText(bloco)
            if (f.length() > TAMANHO_MAXIMO) {
                val conteudo = f.readText()
                f.writeText(conteudo.substring(conteudo.length / 2))
            }
        }
    }

    /** Grava uma linha avulsa, ex.: "Serviço conectado" ou o aviso que foi falado. */
    fun registrarNota(ctx: Context, nota: String) {
        val app = ctx.applicationContext
        val linha = "##### " + formatoHora.format(Date()) + "  " + nota + "\n\n"
        gravador.execute { arquivo(app).appendText(linha) }
    }

    /** Lê o final do log (os registros mais recentes). */
    fun ler(ctx: Context, maxCaracteres: Int = 60_000): String {
        val f = arquivo(ctx)
        if (!f.exists()) return ""
        val t = f.readText()
        return if (t.length > maxCaracteres) "...(início cortado)...\n" + t.takeLast(maxCaracteres) else t
    }

    fun tamanhoKb(ctx: Context): Long = (arquivo(ctx).takeIf { it.exists() }?.length() ?: 0L) / 1024

    fun apagar(ctx: Context) {
        gravador.execute { arquivo(ctx.applicationContext).delete() }
    }
}
