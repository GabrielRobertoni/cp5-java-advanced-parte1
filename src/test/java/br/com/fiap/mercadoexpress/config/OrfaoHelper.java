package br.com.fiap.mercadoexpress.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Executada em OUTRA JVM pelos testes: sobe um PostgreSQL com dados persistentes, grava um registro e
 * morre sem desligar o banco (Runtime.halt), igual a um processo encerrado à força no IntelliJ/Windows.
 */
public class OrfaoHelper {

    public static void main(String[] args) throws Exception {
        File pasta = new File(args[0]);
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setDataDirectory(pasta)
                .setCleanDataDirectory(false)
                .setRegisterShutdownHook(false)
                .start();
        try (Connection conexao = postgres.getPostgresDatabase().getConnection();
             Statement sql = conexao.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS orfao(valor text)");
            sql.execute("INSERT INTO orfao VALUES ('sobreviveu')");
        }
        System.out.println("PRONTO");
        System.out.flush();
        Runtime.getRuntime().halt(0); // morre sem fechar o PostgreSQL
    }
}
