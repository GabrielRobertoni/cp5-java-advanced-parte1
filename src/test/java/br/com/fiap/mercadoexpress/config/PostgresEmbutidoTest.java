package br.com.fiap.mercadoexpress.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostgreSQL embutido: dados persistentes, órfão e pasta em uso")
class PostgresEmbutidoTest {

    private static final String BANCO = "mercado_express";

    private static Connection conectar(int porta) throws Exception {
        return conectar(porta, BANCO);
    }

    private static Connection conectar(int porta, String banco) throws Exception {
        return DriverManager.getConnection("jdbc:postgresql://localhost:" + porta + "/" + banco, "postgres", "");
    }

    private static void gravar(int porta, String valor) throws Exception {
        try (Connection c = conectar(porta); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS recado(valor text)");
            s.execute("INSERT INTO recado VALUES ('" + valor + "')");
        }
    }

    private static List<String> ler(int porta, String tabela, String coluna) throws Exception {
        return ler(porta, BANCO, tabela, coluna);
    }

    private static List<String> ler(int porta, String banco, String tabela, String coluna) throws Exception {
        List<String> valores = new ArrayList<>();
        try (Connection c = conectar(porta, banco); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT " + coluna + " FROM " + tabela + " ORDER BY 1")) {
            while (r.next()) {
                valores.add(r.getString(1));
            }
        }
        return valores;
    }

    @Test
    @DisplayName("Os dados sobrevivem a reiniciar o sistema (pasta persistente)")
    void dadosSobrevivemAoReiniciar(@TempDir File pasta) throws Exception {
        File dados = new File(pasta, "dados-postgres");

        PostgresEmbutido.Servidor primeiro = PostgresEmbutido.iniciar(BANCO, dados);
        assertThat(primeiro.persistente()).isTrue();
        gravar(primeiro.porta(), "cadastro da primeira execucao");
        primeiro.encerrar().close();

        PostgresEmbutido.Servidor segundo = PostgresEmbutido.iniciar(BANCO, dados);
        try {
            assertThat(segundo.persistente()).isTrue();
            assertThat(ler(segundo.porta(), "recado", "valor")).containsExactly("cadastro da primeira execucao");
        } finally {
            segundo.encerrar().close();
        }
    }

    @Test
    @DisplayName("Execução anterior morta à força: o PostgreSQL órfão é desligado e os dados são mantidos")
    void orfaoEDesligadoEDadosMantidos(@TempDir File pasta) throws Exception {
        File dados = new File(pasta, "dados-postgres");

        // Outra JVM sobe o banco, grava e morre sem desligá-lo (igual a "Kill" no IntelliJ)
        Process outraJvm = new ProcessBuilder(
                new File(System.getProperty("java.home"), "bin/java").getPath(),
                "-cp", System.getProperty("java.class.path"),
                OrfaoHelper.class.getName(), dados.getAbsolutePath())
                .redirectErrorStream(true).start();
        StringBuilder saida = new StringBuilder();
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(outraJvm.getInputStream()))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                saida.append(linha).append('\n');
            }
        }
        assertThat(outraJvm.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(saida.toString()).contains("PRONTO");

        File pidFile = new File(dados, "postmaster.pid");
        assertThat(pidFile).as("o PostgreSQL da outra JVM ficou rodando (órfão)").exists();
        long pidOrfao = Long.parseLong(Files.readAllLines(pidFile.toPath()).get(0).trim());
        assertThat(ProcessHandle.of(pidOrfao)).as("órfão vivo").isPresent();
        assertThat(ProcessHandle.of(pidOrfao).orElseThrow().isAlive()).isTrue();

        // Sem a proteção, aqui ocorreria: lock file "postmaster.pid" already exists
        PostgresEmbutido.Servidor servidor = PostgresEmbutido.iniciar(BANCO, dados);
        try {
            assertThat(servidor.persistente()).isTrue();
            assertThat(ProcessHandle.of(pidOrfao).map(ProcessHandle::isAlive).orElse(false))
                    .as("o órfão foi desligado").isFalse();
            // o OrfaoHelper gravou no banco padrão "postgres" antes de morrer
            assertThat(ler(servidor.porta(), "postgres", "orfao", "valor")).containsExactly("sobreviveu");
        } finally {
            servidor.encerrar().close();
        }
    }

    @Test
    @DisplayName("Arquivo postmaster.pid velho (processo inexistente) não impede a execução")
    void pidVelhoNaoImpede(@TempDir File pasta) throws Exception {
        File dados = new File(pasta, "dados-postgres");
        PostgresEmbutido.Servidor primeiro = PostgresEmbutido.iniciar(BANCO, dados);
        gravar(primeiro.porta(), "antes");
        primeiro.encerrar().close();

        // simula um processo que já não existe
        Files.writeString(new File(dados, "postmaster.pid").toPath(), "999999\n" + dados + "\n");

        PostgresEmbutido.Servidor segundo = PostgresEmbutido.iniciar(BANCO, dados);
        try {
            assertThat(segundo.persistente()).isTrue();
            assertThat(ler(segundo.porta(), "recado", "valor")).containsExactly("antes");
        } finally {
            segundo.encerrar().close();
        }
    }

    @Test
    @DisplayName("Pasta em uso por outra execução ativa: não derruba a outra e usa banco temporário")
    void pastaEmUsoUsaTemporario(@TempDir File pasta) throws Exception {
        File dados = new File(pasta, "dados-postgres");

        PostgresEmbutido.Servidor emExecucao = PostgresEmbutido.iniciar(BANCO, dados);
        try {
            gravar(emExecucao.porta(), "intocado");

            PostgresEmbutido.Servidor outra = PostgresEmbutido.iniciar(BANCO, dados);
            try {
                assertThat(outra.persistente()).isFalse();
                assertThat(outra.motivoTemporario()).contains("outra execucao");
                assertThat(outra.porta()).isNotEqualTo(emExecucao.porta());
            } finally {
                outra.encerrar().close();
            }

            // o banco da primeira execução continua de pé e com os dados
            assertThat(ler(emExecucao.porta(), "recado", "valor")).containsExactly("intocado");
        } finally {
            emExecucao.encerrar().close();
        }
    }

    @Test
    @DisplayName("Pasta inutilizável (é um arquivo): cai no banco temporário em vez de falhar")
    void pastaInutilizavelUsaTemporario(@TempDir File pasta) throws Exception {
        File arquivoNoLugarDaPasta = new File(pasta, "dados-postgres");
        Files.writeString(arquivoNoLugarDaPasta.toPath(), "nao sou uma pasta");

        PostgresEmbutido.Servidor servidor = PostgresEmbutido.iniciar(BANCO, arquivoNoLugarDaPasta);
        try {
            assertThat(servidor.persistente()).isFalse();
            assertThat(servidor.motivoTemporario()).isNotBlank();
            gravar(servidor.porta(), "funciona mesmo assim");
        } finally {
            servidor.encerrar().close();
        }
    }
}
