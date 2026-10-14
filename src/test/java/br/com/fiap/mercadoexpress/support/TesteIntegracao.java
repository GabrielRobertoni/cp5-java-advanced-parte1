package br.com.fiap.mercadoexpress.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Base dos testes de integração.
 *
 * Sobe um PostgreSQL 16 REAL embarcado (biblioteca zonky), sem precisar de Docker
 * nem de banco instalado. O Flyway aplica as mesmas migrations da produção e o
 * Hibernate valida o mapeamento (ddl-auto=validate).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class TesteIntegracao {

    public static final String ADMIN_EMAIL = "admin@mercadoexpress.com";
    public static final String ADMIN_SENHA = "Admin@123";
    public static final String CLIENTE_EMAIL = "cliente@mercadoexpress.com";
    public static final String CLIENTE_SENHA = "Cliente@123";

    private static final EmbeddedPostgres POSTGRES = iniciarPostgres();

    private static EmbeddedPostgres iniciarPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // encerrando a JVM
                }
            }));
            return postgres;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível iniciar o PostgreSQL embarcado", e);
        }
    }

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }
}
