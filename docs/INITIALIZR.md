# Print do Spring Initializr (entregável do CP5)

O enunciado pede *"um print da tela com a configuração final do Spring Initializr e respectivas dependências em
.jpg, .jpeg ou .png"*.

## Passo a passo

1. Abra o link abaixo. Ele já carrega a configuração do projeto:

   <https://start.spring.io/#!type=maven-project&language=java&platformVersion=4.1.1&packaging=jar&configurationFileFormat=yaml&jvmVersion=21&groupId=br.com.fiap&artifactId=mercado-express-security&name=mercado-express-security&description=Mercado%20Express%20-%20Spring%20Security%20MVC&packageName=br.com.fiap.mercadoexpress&dependencies=web,thymeleaf,security,data-jpa,postgresql,flyway,validation,lombok,devtools>

2. Confira se a tela está assim (se algum campo não vier preenchido, ajuste manualmente):

| Campo | Valor |
|---|---|
| Project | **Maven** |
| Language | **Java** |
| Spring Boot | **4.1.1** (ou o 4.1.x estável exibido) |
| Group | `br.com.fiap` |
| Artifact | `mercado-express-security` |
| Name | `mercado-express-security` |
| Package name | `br.com.fiap.mercadoexpress` |
| Packaging | **Jar** |
| Configuration | **YAML** |
| Java | **21** |

3. Dependências (lado direito):
   - Spring Web
   - Thymeleaf
   - Spring Security
   - Spring Data JPA
   - PostgreSQL Driver
   - Flyway Migration
   - Validation
   - Lombok
   - Spring Boot DevTools

4. Tire o print da tela inteira (Windows: `Win + Shift + S`) e salve como **`spring-initializr.png`**.
   Envie o arquivo junto com o `.txt` no Teams.

> Observação: o Initializr gera `spring-boot-starter-thymeleaf` + `thymeleaf-extras-springsecurity6` quando Thymeleaf e
> Spring Security são selecionados juntos — exatamente o que está no `pom.xml` do projeto. O PostgreSQL embarcado usado
> nos testes (`io.zonky.test:embedded-postgres`) foi adicionado manualmente ao `pom.xml`, pois não existe no Initializr.
