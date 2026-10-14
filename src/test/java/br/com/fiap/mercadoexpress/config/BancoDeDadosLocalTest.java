package br.com.fiap.mercadoexpress.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Escolha do banco ao iniciar (embutido x configurado)")
class BancoDeDadosLocalTest {

    @Test
    @DisplayName("Sem nenhum banco configurado (IntelliJ) usa o PostgreSQL embutido")
    void semConfiguracaoUsaEmbutido() {
        assertThat(BancoDeDadosLocal.deveUsarBancoEmbutido(Map.of(), new Properties())).isTrue();
    }

    @Test
    @DisplayName("Com DB_HOST no ambiente (Render / Docker Compose) usa o banco configurado")
    void comDbHostNoAmbiente() {
        assertThat(BancoDeDadosLocal.deveUsarBancoEmbutido(Map.of("DB_HOST", "dpg-123.render.com"), new Properties()))
                .isFalse();
    }

    @Test
    @DisplayName("Com DB_HOST como propriedade da JVM usa o banco configurado")
    void comDbHostComoPropriedade() {
        Properties propriedades = new Properties();
        propriedades.setProperty("DB_HOST", "localhost");
        assertThat(BancoDeDadosLocal.deveUsarBancoEmbutido(Map.of(), propriedades)).isFalse();
    }

    @Test
    @DisplayName("Com spring.datasource.url definido usa o banco configurado")
    void comDatasourceUrl() {
        Properties propriedades = new Properties();
        propriedades.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/mercado_express");
        assertThat(BancoDeDadosLocal.deveUsarBancoEmbutido(Map.of(), propriedades)).isFalse();
        assertThat(BancoDeDadosLocal.deveUsarBancoEmbutido(
                Map.of("SPRING_DATASOURCE_URL", "jdbc:postgresql://db:5432/x"), new Properties())).isFalse();
    }

    @Test
    @DisplayName("DB_HOST vazio é tratado como não configurado")
    void dbHostVazio() {
        assertThat(BancoDeDadosLocal.deveUsarBancoEmbutido(Map.of("DB_HOST", "  "), new Properties())).isTrue();
    }
}
