package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.config.BancoDeDadosLocal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MercadoExpressApplication {

    public static void main(String[] args) {
        // Sem banco configurado (DB_HOST), sobe um PostgreSQL embutido para rodar direto no IntelliJ.
        // No deploy (Render) e no Docker Compose o DB_HOST está definido e nada muda.
        BancoDeDadosLocal.prepararSeNecessario();
        SpringApplication.run(MercadoExpressApplication.class, args);
    }
}
