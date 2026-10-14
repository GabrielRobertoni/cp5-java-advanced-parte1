package br.com.fiap.mercadoexpress.config;

import org.springframework.util.ClassUtils;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Decide qual PostgreSQL a aplicação usa ao iniciar.
 *
 * <ul>
 *   <li><b>DB_HOST configurado</b> (Render, Docker Compose ou um PostgreSQL seu): usa esse banco,
 *       conforme o application.yml.</li>
 *   <li><b>Nenhum banco configurado</b> (ex.: abrir o projeto no IntelliJ e clicar em Run): sobe um
 *       PostgreSQL 16 real embutido, cria o banco "mercado_express" e informa a porta pela variável
 *       DB_PORT. A conexão continua sendo feita pelo application.yml.</li>
 * </ul>
 *
 * Esta classe não referencia a biblioteca do banco embutido (que não vai para o JAR de deploy);
 * quem a usa é {@link PostgresEmbutido}, carregada apenas quando necessário.
 */
public final class BancoDeDadosLocal {

    /** Propriedade global da JVM: evita subir um segundo banco quando o DevTools reinicia a aplicação. */
    public static final String MARCADOR_PORTA = "mercadoexpress.postgres-embutido.porta";
    private static final String MARCADOR_AVISO = "mercadoexpress.postgres-embutido.aviso";

    static final String BANCO = "mercado_express";
    /** Pasta (dentro do projeto) onde o PostgreSQL embutido guarda os dados entre as execuções. */
    static final String PASTA_DADOS = "dados-postgres";
    private static final String CLASSE_POSTGRES_EMBUTIDO = "io.zonky.test.db.postgres.embedded.EmbeddedPostgres";

    private BancoDeDadosLocal() {
    }

    /** Chamado no início do main(), antes de o Spring ler o application.yml. */
    public static void prepararSeNecessario() {
        if (System.getProperty(MARCADOR_PORTA) != null) {
            return; // já iniciado nesta JVM (reinício do DevTools)
        }
        if (!deveUsarBancoEmbutido(System.getenv(), System.getProperties())) {
            return;
        }
        if (!ClassUtils.isPresent(CLASSE_POSTGRES_EMBUTIDO, BancoDeDadosLocal.class.getClassLoader())) {
            if (System.getProperty(MARCADOR_AVISO) != null) {
                return; // aviso já exibido nesta JVM (reinício do DevTools)
            }
            System.setProperty(MARCADOR_AVISO, "true");
            System.out.println();
            System.out.println(">>> ATENCAO: o PostgreSQL embutido nao esta no classpath; tentando localhost:5432.");
            System.out.println(">>> No IntelliJ, recarregue o Maven (Ctrl+Shift+O ou o botao 'Load Maven Changes')");
            System.out.println(">>> e execute de novo. Ou defina DB_HOST para usar o seu proprio PostgreSQL.");
            System.out.println();
            return;
        }

        File pastaDados = new File(System.getProperty("user.dir"), PASTA_DADOS);
        PostgresEmbutido.Servidor servidor = PostgresEmbutido.iniciar(BANCO, pastaDados);
        int porta = servidor.porta();

        // Lidos pelo application.yml: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:mercado_express}
        System.setProperty("DB_PORT", String.valueOf(porta));
        System.setProperty("DB_NAME", BANCO);
        System.setProperty(MARCADOR_PORTA, String.valueOf(porta));

        // Sem acentos de propósito: alguns consoles do Windows não exibem UTF-8 corretamente.
        System.out.println();
        System.out.println("==================================================================");
        System.out.println(" Banco de dados: PostgreSQL 16 EMBUTIDO (nenhum DB_HOST configurado)");
        System.out.println(" Porta " + porta + " | banco " + BANCO + " | configuracao: application.yml");
        if (servidor.persistente()) {
            System.out.println(" Dados SALVOS na pasta: " + pastaDados.getAbsolutePath());
            System.out.println(" (continuam la ao reiniciar; para zerar, pare o sistema e apague essa pasta)");
        } else {
            System.out.println(" ATENCAO: usando banco TEMPORARIO, os dados se perdem ao parar.");
            System.out.println(" Motivo: " + servidor.motivoTemporario());
        }
        System.out.println(" Para usar outro PostgreSQL, defina a variavel de ambiente DB_HOST.");
        System.out.println("==================================================================");
        System.out.println();
    }

    /**
     * Usa o banco embutido somente quando NENHUM banco foi configurado
     * (nem DB_HOST nem spring.datasource.url, por variável de ambiente ou propriedade).
     */
    static boolean deveUsarBancoEmbutido(Map<String, String> ambiente, Properties propriedades) {
        return !temValor(ambiente.get("DB_HOST"))
                && !temValor(propriedades.getProperty("DB_HOST"))
                && !temValor(ambiente.get("SPRING_DATASOURCE_URL"))
                && !temValor(propriedades.getProperty("spring.datasource.url"));
    }

    private static boolean temValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
