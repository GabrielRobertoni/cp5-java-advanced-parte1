package br.com.fiap.mercadoexpress.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Sobe um PostgreSQL 16 real embutido (biblioteca zonky) para rodar o projeto na máquina
 * sem instalar banco. Usada somente por {@link BancoDeDadosLocal}; não existe no JAR de deploy.
 *
 * <p>Os dados ficam numa pasta do projeto e sobrevivem a reinícios. Para a execução nunca falhar:
 * <ul>
 *   <li>um PostgreSQL deixado rodando por uma execução encerrada à força é desligado antes de subir o novo;</li>
 *   <li>se outra execução ainda está usando a pasta, ela não é derrubada: esta usa um banco temporário;</li>
 *   <li>se a pasta não puder ser usada por qualquer motivo, usa-se um banco temporário.</li>
 * </ul>
 */
final class PostgresEmbutido {

    /** Resultado da inicialização. {@code persistente} é false quando caiu no banco temporário. */
    record Servidor(int porta, boolean persistente, String motivoTemporario, AutoCloseable encerrar) {
    }

    private static final Duration ESPERA_PASTA_EM_USO = Duration.ofSeconds(10);
    private static final Duration ESPERA_ENCERRAR_ORFAO = Duration.ofSeconds(20);

    private PostgresEmbutido() {
    }

    /**
     * @param pastaDados pasta onde ficam os dados persistentes; se for nula, usa sempre o banco temporário
     */
    static Servidor iniciar(String nomeBanco, File pastaDados) {
        String motivo = null;
        if (pastaDados != null) {
            try {
                if (pastaEmUsoPorOutraExecucao(pastaDados)) {
                    motivo = "outra execucao do sistema ainda esta usando a pasta de dados";
                } else {
                    liberarPostgresOrfao(pastaDados);
                    return subir(nomeBanco, pastaDados, true, null);
                }
            } catch (IOException | SQLException | RuntimeException e) {
                motivo = "nao foi possivel usar a pasta de dados (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
            }
        } else {
            motivo = "pasta de dados nao informada";
        }

        try {
            File temporaria = new File(pastaDeTrabalhoDaBiblioteca(), "epg-mercado-express-" + UUID.randomUUID());
            return subir(nomeBanco, temporaria, false, motivo);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException(
                    "Nao foi possivel iniciar o PostgreSQL embutido. Alternativas: rodar 'docker compose up --build' "
                    + "ou configurar um PostgreSQL proprio pelas variaveis DB_HOST, DB_PORT, DB_USER e DB_PASSWORD.", e);
        }
    }

    private static Servidor subir(String nomeBanco, File pasta, boolean persistente, String motivo)
            throws IOException, SQLException {
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setDataDirectory(pasta)
                // Temporário: apagado ao encerrar. Persistente: mantido para a próxima execução.
                .setCleanDataDirectory(!persistente)
                .start();
        AutoCloseable encerrar = () -> {
            try {
                postgres.close();
            } catch (IOException ignored) {
                // a aplicação está encerrando
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                encerrar.close();
            } catch (Exception ignored) {
                // a aplicação está encerrando
            }
        }, "encerrar-postgres-embutido"));

        try {
            criarBancoSeNaoExistir(postgres, nomeBanco);
        } catch (SQLException | RuntimeException e) {
            try {
                encerrar.close();
            } catch (Exception ignored) {
                // já falhou
            }
            throw e;
        }
        return new Servidor(postgres.getPort(), persistente, motivo, encerrar);
    }

    private static File pastaDeTrabalhoDaBiblioteca() {
        return new File(System.getProperty("ot.epg.working-dir",
                new File(System.getProperty("java.io.tmpdir"), "embedded-pg").getPath()));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * A biblioteca mantém um bloqueio de arquivo (epg-lock) enquanto a execução está viva.
     * Bloqueio ativo = há outra execução usando a pasta. Espera um pouco, porque uma execução
     * que está sendo encerrada (ex.: botão Rerun do IntelliJ) libera o bloqueio em instantes.
     */
    static boolean pastaEmUsoPorOutraExecucao(File pasta) throws IOException {
        File lock = new File(pasta, "epg-lock");
        if (!lock.exists()) {
            return false;
        }
        long limite = System.nanoTime() + ESPERA_PASTA_EM_USO.toNanos();
        do {
            try (FileChannel canal = FileChannel.open(lock.toPath(), StandardOpenOption.WRITE);
                 FileLock bloqueio = canal.tryLock()) {
                if (bloqueio != null) {
                    return false;
                }
            } catch (OverlappingFileLockException e) {
                // bloqueado por esta mesma JVM: em uso
            }
            dormir(300);
        } while (System.nanoTime() < limite);
        return true;
    }

    /**
     * Se uma execução anterior foi encerrada à força, o PostgreSQL dela continua rodando e impede
     * uma nova subida ("lock file postmaster.pid already exists"). Aqui ele é desligado.
     * Só encerra processos que são, de fato, um PostgreSQL.
     */
    static void liberarPostgresOrfao(File pasta) throws IOException {
        File arquivoPid = new File(pasta, "postmaster.pid");
        if (!arquivoPid.exists()) {
            return;
        }
        Optional<ProcessHandle> processo = lerProcessoDoPostmaster(arquivoPid);
        if (processo.isPresent() && processo.get().isAlive()) {
            if (!pareceSerPostgres(processo.get())) {
                throw new IOException("o processo " + processo.get().pid() + " usa a pasta de dados, mas nao e um PostgreSQL");
            }
            encerrar(processo.get());
        }
        // Processo inexistente ou já encerrado: o arquivo ficou velho e é removido.
        Files.deleteIfExists(arquivoPid.toPath());
    }

    private static Optional<ProcessHandle> lerProcessoDoPostmaster(File arquivoPid) {
        try {
            List<String> linhas = Files.readAllLines(arquivoPid.toPath());
            return ProcessHandle.of(Long.parseLong(linhas.get(0).trim()));
        } catch (IOException | RuntimeException e) {
            return Optional.empty(); // arquivo ilegível: tratado como velho
        }
    }

    private static boolean pareceSerPostgres(ProcessHandle processo) {
        return processo.info().command()
                .map(comando -> comando.toLowerCase(Locale.ROOT).contains("postgres"))
                .orElse(false);
    }

    private static void encerrar(ProcessHandle postmaster) throws IOException {
        List<ProcessHandle> filhos = postmaster.descendants().toList();
        postmaster.destroy();   // Linux/Mac: desligamento normal do PostgreSQL (SIGTERM)
        if (!aguardarEncerrar(postmaster, Duration.ofSeconds(8))) {
            postmaster.destroyForcibly();
            filhos.forEach(ProcessHandle::destroyForcibly);
        }
        if (!aguardarEncerrar(postmaster, ESPERA_ENCERRAR_ORFAO)) {
            throw new IOException("nao foi possivel desligar o PostgreSQL da execucao anterior (pid " + postmaster.pid() + ")");
        }
        filhos.forEach(filho -> aguardarEncerrar(filho, Duration.ofSeconds(5)));
    }

    private static boolean aguardarEncerrar(ProcessHandle processo, Duration limite) {
        long fim = System.nanoTime() + limite.toNanos();
        while (processo.isAlive() && System.nanoTime() < fim) {
            dormir(200);
        }
        return !processo.isAlive();
    }

    private static void dormir(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void criarBancoSeNaoExistir(EmbeddedPostgres postgres, String nomeBanco) throws SQLException {
        try (Connection conexao = postgres.getPostgresDatabase().getConnection();
             Statement sql = conexao.createStatement()) {
            try (ResultSet existe = sql.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + nomeBanco + "'")) {
                if (existe.next()) {
                    return;
                }
            }
            sql.execute("CREATE DATABASE " + nomeBanco);
        }
    }
}
