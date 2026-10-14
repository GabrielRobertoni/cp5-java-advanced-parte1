# 🛒 Mercado Express — Spring Security MVC

**Checkpoint 5 · Parte I (Spring Security) · Java Advanced · FIAP – Análise e Desenvolvimento de Sistemas (TDS)**
Prof. Dr. Marcel Stefan Wagner

Aplicação web de um **mercado express** construída com **Spring Boot (MVC) + Spring Security + Thymeleaf + PostgreSQL**, com
CRUD completo de produtos, **tela de login própria**, **Sign Up** com gravação do perfil no banco, **dois tipos de usuário**
com acessos diferentes e deploy em nuvem.

| | |
|---|---|
| 🌐 **Deploy (produção)** | `https://SEU-SERVICO.onrender.com` ← *(substituir pelo link do Render após o deploy)* |
| 💻 **IDE utilizada** | IntelliJ IDEA |
| ☁️ **Plataforma de deploy** | Render (Web Service Docker + PostgreSQL gerenciado) |

### 🔑 Acesso para avaliação

As duas contas abaixo já existem no banco e também aparecem na **página inicial** e **logo abaixo da tela de login**
(com botão **Preencher**), para entrar direto em cada perfil:

| Perfil | E-mail | Senha |
|---|---|---|
| **ADMIN** (CRUD completo + gestão de usuários) | `admin@mercadoexpress.com` | `Admin@123` |
| **CLIENTE** (somente consulta) | `cliente@mercadoexpress.com` | `Cliente@123` |

> Essas duas contas são protegidas: não podem ser excluídas nem ter o perfil alterado pela tela de usuários,
> para que o acesso continue funcionando durante a avaliação.

## 👥 Integrantes

| Nome | RM |
|---|---|
| Gabriel Robertoni Padilha | 566293 |
| Bruno Ferreira | 563489 |

---

## 📑 Sumário

1. [Requisitos do CP5 × o que foi implementado](#-requisitos-do-cp5--o-que-foi-implementado)
2. [Tecnologias e configuração do Spring Initializr](#-tecnologias-e-configuração-do-spring-initializr)
3. [Arquitetura do projeto](#-arquitetura-do-projeto)
4. [Banco de dados PostgreSQL](#-banco-de-dados-postgresql)
5. [Segurança: login, Sign Up e perfis](#-segurança-login-sign-up-e-perfis)
6. [Telas da aplicação (com prints)](#-telas-da-aplicação-com-prints)
7. [Endpoints](#-endpoints)
8. [Como executar localmente](#-como-executar-localmente)
9. [Testes automatizados](#-testes-automatizados)
10. [Deploy no Render](#-deploy-no-render)

---

## ✅ Requisitos do CP5 × o que foi implementado

| Requisito do enunciado | Como foi atendido |
|---|---|
| Mesmo tema do último CP (CP4: mercado express) | Mesmo Mercado Express, mantendo as colunas pedidas no CP4 (**Id, Nome, Tipo, Setor, Tamanho e Preco**) e acrescentando estoque e descrição |
| Spring Framework MVC, Maven, Java, Lombok | Spring Boot 4.1.1 (Spring MVC 7), Maven, Java 21 e Lombok nas entidades e DTOs |
| Interface Web com CRUD em links/botões | Botões **+ Novo produto**, **Ver**, **Editar**, **Excluir** e formulário de busca |
| Consulta de produto em tabela do PostgreSQL | Tabela **`TDS_Sec_MVC_TB_Mercado`** (lista, busca por nome/tipo, filtro por setor e detalhe) |
| Configuração básica em `application.yml` | [`src/main/resources/application.yml`](src/main/resources/application.yml) |
| Tela de login **diferente** da padrão do Spring Security | [`templates/login.html`](src/main/resources/templates/login.html) com `formLogin().loginPage("/login")` |
| Usuário sem login → tela de Sign Up | Login com e-mail não cadastrado redireciona para **`/cadastro`** com aviso e e-mail preenchido |
| Perfil salvo em tabela de usuários | Tabela **`TDS_Users_Mercado`** (senha em hash **BCrypt**) |
| Redirecionamento para tela restrita após login | **ADMIN → `/admin/painel`** · **CLIENTE → `/produtos`** |
| Pelo menos 2 tipos de usuário e respectivos acessos | **ADMIN** (gerente) e **CLIENTE** — ver [matriz de acesso](#perfis-e-permissões) |
| Landing page `index.html` | [`templates/index.html`](src/main/resources/templates/index.html), pública, em `/` e em `/index.html` |
| Deploy com link de produção no GitHub | Render, com [`Dockerfile`](Dockerfile) e [`render.yaml`](render.yaml) ([passo a passo](#-deploy-no-render)) |

---

## 🧰 Tecnologias e configuração do Spring Initializr

- **Java 21** · **Spring Boot 4.1.1** · **Maven** (com Maven Wrapper `./mvnw`)
- Spring Web MVC · Spring Security 7 · Thymeleaf (+ `thymeleaf-extras-springsecurity6`)
- Spring Data JPA / Hibernate 7 · **PostgreSQL** · **Flyway** (versionamento do banco, visto na Aula 10)
- Bean Validation · Lombok · DevTools
- Testes: JUnit 5, Spring Security Test, MockMvc e **PostgreSQL embarcado** (zonky)

**Configuração usada no [Spring Initializr](https://start.spring.io):**

| Campo | Valor |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | 4.1.1 |
| Group | `br.com.fiap` |
| Artifact / Name | `mercado-express-security` |
| Package name | `br.com.fiap.mercadoexpress` |
| Packaging | Jar |
| Configuration | **YAML** |
| Java | 21 |
| Dependências | Spring Web, Thymeleaf, Spring Security, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Validation, Lombok, Spring Boot DevTools |

> O print da configuração final do Initializr é entregue junto com o `.txt` (ver [`docs/INITIALIZR.md`](docs/INITIALIZR.md)).

---

## 🏗️ Arquitetura do projeto

Organização em camadas (MVC):

```
src/main/java/br/com/fiap/mercadoexpress
├── MercadoExpressApplication.java
├── config/
│   ├── SecurityConfig.java          # regras de acesso, login próprio, logout, CSRF, cabeçalhos
│   ├── PasswordConfig.java          # BCryptPasswordEncoder
│   ├── CargaInicialUsuarios.java    # cria o admin (e um cliente demo) na 1ª execução
│   ├── BancoDeDadosLocal.java       # sem DB_HOST: usa PostgreSQL embutido (rodar na máquina)
│   └── PostgresEmbutido.java        # PostgreSQL 16 embutido com dados persistentes (fora do JAR de deploy)
├── controller/                      # camada Web (Controller do MVC)
│   ├── HomeController.java          # /, /login, /acesso-negado
│   ├── CadastroController.java      # /cadastro (Sign Up)
│   ├── ProdutoController.java       # CRUD /produtos
│   └── AdminController.java         # /admin/painel, /admin/usuarios
├── dto/CadastroForm.java            # dados + validações do Sign Up
├── exception/                       # 404 e regras de negócio
├── model/                           # entidades JPA (Model do MVC)
│   ├── Produto.java                 # -> TDS_Sec_MVC_TB_Mercado
│   ├── Usuario.java                 # -> TDS_Users_Mercado
│   └── Perfil.java                  # ADMIN | CLIENTE
├── repository/                      # Spring Data JPA
├── security/                        # integração com o Spring Security
│   ├── UsuarioDetailsService.java   # carrega o usuário do banco pelo e-mail
│   ├── UsuarioAutenticado.java      # usuário logado (UserDetails)
│   ├── LoginSucessoHandler.java     # redireciona por perfil após o login
│   └── LoginFalhaHandler.java       # sem conta -> Sign Up | senha errada -> /login?erro
└── service/                         # regras de negócio
src/main/resources
├── application.yml
├── db/migration/                    # V1 (tabelas) e V2 (carga de produtos) - Flyway
├── templates/                       # View do MVC (Thymeleaf): index, login, cadastro, produtos/*, admin/*, erros
└── static/                          # css, js e imagens
```

---

## 🐘 Banco de dados PostgreSQL

As tabelas são criadas e versionadas pelo **Flyway** ([`V1__criar_tabelas.sql`](src/main/resources/db/migration/V1__criar_tabelas.sql)).
O Hibernate apenas **valida** o mapeamento (`ddl-auto: validate`), como recomendado para produção.

```sql
CREATE TABLE TDS_Users_Mercado (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    nome       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,
    senha      VARCHAR(100) NOT NULL,              -- hash BCrypt
    perfil     VARCHAR(20)  NOT NULL CHECK (perfil IN ('ADMIN','CLIENTE')),
    criado_em  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE TDS_Sec_MVC_TB_Mercado (
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    nome               VARCHAR(100)  NOT NULL,
    tipo               VARCHAR(50)   NOT NULL,
    setor              VARCHAR(50)   NOT NULL,
    tamanho            VARCHAR(30),
    preco              NUMERIC(10,2) NOT NULL CHECK (preco > 0),
    quantidade_estoque INTEGER       NOT NULL DEFAULT 0 CHECK (quantidade_estoque >= 0),
    descricao          VARCHAR(255),
    criado_em          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

> No PostgreSQL, nomes sem aspas são gravados em minúsculas: no pgAdmin as tabelas aparecem como
> `tds_sec_mvc_tb_mercado` e `tds_users_mercado`.

A migration [`V2__popular_produtos.sql`](src/main/resources/db/migration/V2__popular_produtos.sql) carrega 12 produtos
(hortifruti, mercearia, frios, limpeza e utilidades).

Configuração em [`application.yml`](src/main/resources/application.yml). Os valores vêm de variáveis de ambiente,
com padrões para rodar localmente:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:mercado_express}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

---

## 🔐 Segurança: login, Sign Up e perfis

### Perfis e permissões

| Funcionalidade | Visitante | CLIENTE | ADMIN |
|---|:---:|:---:|:---:|
| Landing page, login e Sign Up | ✅ | ✅ | ✅ |
| Consultar, buscar e ver detalhes de produtos | ❌ | ✅ | ✅ |
| Cadastrar, editar e excluir produtos | ❌ | ❌ (403) | ✅ |
| Painel gerencial e gestão de usuários | ❌ | ❌ (403) | ✅ |

Quem se cadastra pelo Sign Up **sempre** recebe o perfil **CLIENTE** (o formulário não permite escolher o perfil).
Somente um ADMIN pode promover um cliente a administrador na tela **Usuários**.

### Regras no `SecurityConfig`

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/", "/login", "/cadastro", "/acesso-negado", "/error",
                "/css/**", "/js/**", "/img/**", "/favicon.ico", "/favicon.svg").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/produtos/novo", "/produtos/*/editar").hasRole("ADMIN")
        .requestMatchers(HttpMethod.POST, "/produtos", "/produtos/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.PUT, "/produtos/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.DELETE, "/produtos/**").hasRole("ADMIN")
        .requestMatchers(HttpMethod.GET, "/produtos", "/produtos/*").hasAnyRole("ADMIN", "CLIENTE")
        .anyRequest().authenticated())
    .formLogin(form -> form
        .loginPage("/login")                 // tela de login PRÓPRIA
        .usernameParameter("email")
        .passwordParameter("senha")
        .successHandler(loginSucessoHandler) // ADMIN -> /admin/painel | CLIENTE -> /produtos
        .failureHandler(loginFalhaHandler)   // sem conta -> /cadastro | senha errada -> /login?erro
        .permitAll())
    .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/?saiu"))
    .exceptionHandling(ex -> ex.accessDeniedPage("/acesso-negado"));
```

### Como funciona a implementação da tela de login

1. **Tela própria:** `GET /login` retorna o template `login.html`. O formulário envia `email`, `senha` e o **token CSRF**
   (gerado automaticamente pelo Thymeleaf no `th:action`) para `POST /login`, que é processado pelo Spring Security.
2. **Autenticação no banco:** o `UsuarioDetailsService` busca o usuário na tabela `TDS_Users_Mercado` pelo e-mail
   (sem diferenciar maiúsculas) e o Spring Security compara a senha digitada com o **hash BCrypt** gravado.
3. **Sucesso:** o `LoginSucessoHandler` lê o perfil e redireciona para a **tela restrita de cada perfil**.
4. **Falha:** o `LoginFalhaHandler` verifica se o e-mail existe:
   - **não existe** → redireciona para `/cadastro?semConta&email=...` (requisito *"caso o usuário não possua um login,
     deve-se redirecioná-lo para uma tela de Sign Up"*);
   - **existe, mas a senha está errada** → volta para `/login?erro` com a mensagem *"E-mail ou senha incorretos"*.
5. **Sign Up:** `POST /cadastro` valida nome, e-mail, senha (6 a 72 caracteres) e confirmação; recusa e-mail repetido;
   grava o usuário como **CLIENTE** com a senha em BCrypt e volta ao login com o e-mail preenchido.

**Exemplo — formulário da tela de login** ([`login.html`](src/main/resources/templates/login.html)):

```html
<form th:action="@{/login}" method="post" class="formulario">
    <!-- o Thymeleaf insere automaticamente o campo oculto _csrf -->
    <input type="email" id="email" name="email" required autocomplete="username" th:value="${param.email}">
    <input type="password" id="senha" name="senha" required autocomplete="current-password">
    <button type="submit">Entrar</button>
</form>
<a th:href="@{/cadastro}">Cadastre-se (Sign Up)</a>
```

**Exemplo — usuário sem login vai para o Sign Up** ([`LoginFalhaHandler.java`](src/main/java/br/com/fiap/mercadoexpress/security/LoginFalhaHandler.java)):

```java
String email = request.getParameter("email");
if (email != null && !email.isBlank() && !usuarioService.emailCadastrado(email)) {
    // e-mail sem conta -> tela de Sign Up, já com o e-mail preenchido
    redirectStrategy.sendRedirect(request, response, "/cadastro?semConta&email=" + email);
    return;
}
redirectStrategy.sendRedirect(request, response, "/login?erro");   // senha errada
```

**Exemplo — redirecionamento por perfil após o login** ([`LoginSucessoHandler.java`](src/main/java/br/com/fiap/mercadoexpress/security/LoginSucessoHandler.java)):

```java
boolean admin = authentication.getAuthorities().stream()
        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
return admin ? "/admin/painel" : "/produtos";
```

**Exemplo de fluxo completo:**

```
1. Visitante abre /produtos                         -> 302 para /login
2. Digita ana@email.com (sem conta) e uma senha    -> 302 para /cadastro?semConta&email=ana@email.com
3. Preenche o Sign Up e envia                       -> grava em TDS_Users_Mercado (perfil CLIENTE) -> /login
4. Faz login com ana@email.com                      -> 302 para /produtos (catálogo restrito)
5. Tenta abrir /admin/painel                        -> 403 Acesso negado
```

### Outras proteções

- **CSRF habilitado** em todos os formulários (login, cadastro, CRUD e logout via POST).
- **Senhas com BCrypt**, limitadas a 72 bytes (limite do algoritmo), sem erro 500 para senhas longas.
- **Content-Security-Policy** (sem scripts/estilos inline), `X-Frame-Options: DENY`, `nosniff` e `Referrer-Policy`.
- O formulário de produto não aceita os campos `id`, `criadoEm` e `atualizadoEm` (evita sobrescrever registros).
- Um ADMIN não consegue excluir a própria conta nem remover o próprio acesso de administrador.
- Atrás do proxy HTTPS da plataforma (`server.forward-headers-strategy: native`), os redirecionamentos usam `https://`
  e o cookie de sessão sai com a flag `Secure`.

---

## 🖥️ Telas da aplicação (com prints)

> Prints gerados automaticamente pelo roteiro de testes no navegador ([`docs/e2e/e2e.js`](docs/e2e/e2e.js)),
> com a aplicação rodando em container Docker e PostgreSQL 16.

### 1. Landing page (`index.html`) — pública
Apresenta o mercado, os botões **Entrar** / **Criar conta grátis**, as **contas de avaliação** (e-mail e senha dos dois
perfis) e a matriz de perfis de acesso.

![Landing page](docs/prints/01-landing-page.png)

### 2. Tela de login personalizada
Ao tentar acessar uma rota restrita sem estar logado, o visitante é levado para cá. Abaixo do formulário ficam as
contas de avaliação, com o botão **Preencher** que coloca e-mail e senha no formulário.

![Tela de login](docs/prints/02-tela-login.png)

Com a senha errada, a mensagem de erro aparece na própria tela:

![Login com senha errada](docs/prints/10-login-senha-errada.png)

### 3. Sign Up (usuário sem login)
Quem tenta entrar com um e-mail que não tem conta é redirecionado ao cadastro, com aviso e e-mail já preenchido.
As validações aparecem campo a campo:

![Sign Up com validação](docs/prints/03-signup-validacao.png)

![Sign Up preenchido](docs/prints/04-signup-preenchido.png)

Após o cadastro, o usuário volta ao login com mensagem de sucesso:

![Cadastro concluído](docs/prints/05-cadastro-sucesso-login.png)

### 4. Área do CLIENTE — catálogo (READ)
Depois do login, o cliente vai direto para o catálogo. Ele consulta, busca e vê detalhes, **sem** botões de alteração.

![Catálogo do cliente](docs/prints/06-cliente-catalogo.png)

Busca por nome/tipo e filtro por setor:

![Busca](docs/prints/07-cliente-busca.png)

Detalhe do produto (consulta na tabela `TDS_Sec_MVC_TB_Mercado`):

![Detalhe do produto](docs/prints/08-cliente-detalhe.png)

Tentativa de acessar a área do administrador → **403**:

![Acesso negado](docs/prints/09-cliente-acesso-negado.png)

### 5. Área do ADMIN — painel gerencial
O administrador vai para o painel após o login: indicadores de produtos, estoque, valor em estoque, usuários e
produtos com estoque baixo.

![Painel do administrador](docs/prints/11-admin-painel.png)

### 6. CRUD de produtos (ADMIN)

**READ** — lista com botões **Ver**, **Editar** e **Excluir**:

![Lista do admin](docs/prints/12-admin-lista-produtos.png)

**CREATE** — validação e cadastro:

![Create com validação](docs/prints/13-admin-create-validacao.png)

![Create preenchido](docs/prints/14-admin-create-preenchido.png)

![Create com sucesso](docs/prints/15-admin-create-sucesso.png)

**UPDATE** — edição (enviada como `PUT /produtos/{id}`):

![Update](docs/prints/16-admin-update-form.png)

![Update com sucesso](docs/prints/17-admin-update-sucesso.png)

**DELETE** — exclusão com confirmação (enviada como `DELETE /produtos/{id}`):

![Delete com sucesso](docs/prints/18-admin-delete-sucesso.png)

### 7. Gestão de usuários (ADMIN)
Lista da tabela `TDS_Users_Mercado`. O admin promove clientes a administradores ou exclui contas.

![Usuários](docs/prints/19-admin-usuarios.png)

### 8. Versão para celular

| Landing (menu aberto) | Login |
|---|---|
| ![Mobile landing](docs/prints/20-mobile-landing.png) | ![Mobile login](docs/prints/21-mobile-login.png) |

---

## 🔗 Endpoints

| Método | Rota | Descrição | Acesso |
|---|---|---|---|
| GET | `/` e `/index.html` | Landing page (`index.html`) | Público |
| GET | `/login` | Tela de login personalizada | Público |
| POST | `/login` | Autenticação (Spring Security) | Público |
| GET | `/cadastro` | Tela de Sign Up | Público |
| POST | `/cadastro` | Cria usuário CLIENTE em `TDS_Users_Mercado` | Público |
| POST | `/logout` | Encerra a sessão | Logado |
| GET | `/produtos?q=&setor=` | **READ** – lista e busca | CLIENTE, ADMIN |
| GET | `/produtos/{id}` | **READ** – detalhe | CLIENTE, ADMIN |
| GET | `/produtos/novo` | Formulário de criação | ADMIN |
| POST | `/produtos` | **CREATE** | ADMIN |
| GET | `/produtos/{id}/editar` | Formulário de edição | ADMIN |
| PUT | `/produtos/{id}` | **UPDATE** | ADMIN |
| DELETE | `/produtos/{id}` | **DELETE** | ADMIN |
| GET | `/admin/painel` | Painel gerencial | ADMIN |
| GET | `/admin/usuarios` | Lista de usuários | ADMIN |
| PUT | `/admin/usuarios/{id}/perfil` | Altera o perfil (ADMIN/CLIENTE) | ADMIN |
| DELETE | `/admin/usuarios/{id}` | Exclui usuário | ADMIN |

> Formulários HTML só enviam GET/POST. O PUT e o DELETE usam o campo oculto `_method`, gerado pelo Thymeleaf
> (`th:method="put"`), com `spring.mvc.hiddenmethod.filter.enabled: true`.

---

## ▶️ Como executar localmente

**Pré-requisito:** JDK 21 (o IntelliJ pode baixar em *File → Project Structure → SDK*). **Não é preciso instalar o PostgreSQL.**

> 🌐 Para apenas conferir o sistema, use o **link do deploy** no topo deste README.

### Opção 1 — IntelliJ ou terminal, sem instalar nada ⭐ (recomendada)

- **IntelliJ:** abra a pasta do projeto (a que contém o `pom.xml`), aguarde o Maven carregar e execute a classe
  **`MercadoExpressApplication`** (botão ▶️ ao lado do `main`).
- **Terminal**, na pasta do projeto:

```bash
mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run          # Linux/Mac
```

Quando **nenhum banco é configurado** (variável `DB_HOST` ausente), a aplicação sobe sozinha um **PostgreSQL 16 real
embutido** e cria nele o banco `mercado_express`. A conexão continua sendo feita pelo **`application.yml`**, exatamente
como em produção; só a porta do servidor é informada pela variável `DB_PORT`. O console mostra:

```
==================================================================
 Banco de dados: PostgreSQL 16 EMBUTIDO (nenhum DB_HOST configurado)
 Porta 5xxxx | banco mercado_express | configuracao: application.yml
 Dados SALVOS na pasta: ...\dados-postgres
==================================================================
...
>>> Mercado Express pronto: http://localhost:8080
```

Acesse **http://localhost:8080**. Na primeira execução, o Maven baixa as dependências, o que pode levar alguns minutos.

**Os dados são salvos:** ficam na pasta **`dados-postgres/`**, dentro do projeto (fora do Git), e **continuam lá ao
parar e iniciar o sistema de novo**: usuários cadastrados, produtos criados, editados e excluídos. Na primeira execução
o banco nasce com 12 produtos e os usuários de demonstração abaixo.

- **Para zerar tudo:** pare o sistema e apague a pasta `dados-postgres/`.
- Se o sistema for encerrado à força, o PostgreSQL que ficou rodando é desligado automaticamente na próxima execução,
  sem perder dados.
- Se você abrir uma segunda cópia do sistema ao mesmo tempo, ela não derruba a primeira: usa um banco temporário e
  avisa isso no console.

> O PostgreSQL embutido serve apenas para rodar na máquina e para os testes: ele **não entra no JAR de deploy**. No
> Render (e no Docker Compose) a variável `DB_HOST` está definida e a aplicação usa o banco PostgreSQL configurado.

### Opção 2 — Docker Compose (banco + aplicação, com dados persistentes)

Com o **Docker Desktop aberto**, na pasta do projeto:

```bash
docker compose up --build
```

Acesse **http://localhost:8080**. Os dados ficam guardados em um volume e **sobrevivem** a reinícios.
Para consultar o banco no pgAdmin/DBeaver: host `localhost`, porta **5433**, banco `mercado_express`,
usuário `mercado`, senha `mercado123`. Para parar: `Ctrl+C` (ou `docker compose down`); para apagar os dados
também: `docker compose down -v`.

### Opção 3 — Usando um PostgreSQL já instalado (pgAdmin)

1. Crie o banco **`mercado_express`** (as tabelas são criadas pelo Flyway).
2. Defina as variáveis de ambiente do seu banco: `DB_HOST=localhost` (obrigatória para não usar o banco embutido),
   e, se forem diferentes do padrão, `DB_PORT`, `DB_USER` e `DB_PASSWORD`. No IntelliJ: *Run → Edit Configurations →
   MercadoExpressApplication → Environment variables*, por exemplo `DB_HOST=localhost;DB_PASSWORD=sua_senha`.
3. Execute `MercadoExpressApplication` (ou `mvnw spring-boot:run`).

### 3. Usuários de demonstração (criados automaticamente)

| Perfil | E-mail | Senha (padrão local) |
|---|---|---|
| ADMIN | `admin@mercadoexpress.com` | `Admin@123` |
| CLIENTE | `cliente@mercadoexpress.com` | `Cliente@123` |

### Variáveis de ambiente (opcionais)

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_HOST` | *(ausente → PostgreSQL embutido)* | Servidor do PostgreSQL. Se definido, usa esse banco |
| `DB_PORT` / `DB_NAME` | `5432` / `mercado_express` | Porta e nome do banco |
| `DB_USER` / `DB_PASSWORD` | `postgres` / `postgres` | Credenciais do banco |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@mercadoexpress.com` / `Admin@123` | Administrador inicial |
| `DEMO_CLIENTE_PASSWORD` | `Cliente@123` | Senha do cliente de demonstração |
| `DEMO_CLIENTE_ENABLED` | `true` | Cria (ou não) o cliente de demonstração |
| `PORT` | `8080` | Porta HTTP (o Render define automaticamente) |

---

## 🧪 Testes automatizados

```bash
./mvnw test
```

São **64 testes** (54 de integração, que sobem a aplicação inteira contra um **PostgreSQL 16 real embarcado**, e 10 do modo local)
(biblioteca zonky). Não é preciso ter Docker nem banco instalado para rodar os testes. Eles cobrem:

| Classe | O que verifica |
|---|---|
| `RotasPublicasTest` | landing `index.html`, login próprio (não é a tela padrão), contas de avaliação visíveis na landing e no login, rotas protegidas, cabeçalhos de segurança |
| `AutenticacaoTest` | redirecionamento por perfil, senha errada, **sem conta → Sign Up**, CSRF, logout, Sign Up (validações, e-mail duplicado, BCrypt, não permite virar ADMIN) |
| `ProdutoCrudTest` | **CREATE, READ, UPDATE (PUT) e DELETE** gravando no PostgreSQL, validações, busca, 404, painel |
| `ControleDeAcessoClienteTest` | CLIENTE só consulta: 403 em criação/edição/exclusão e na área admin |
| `AdminUsuariosTest` | promover/rebaixar perfil, excluir usuário, proteções contra o próprio admin, contas de avaliação protegidas |
| `BancoDeDadosTest` | migrations do Flyway, nomes das tabelas, carga inicial e restrições (UNIQUE, CHECK) |
| `BancoDeDadosLocalTest` | escolha do banco ao iniciar: embutido sem `DB_HOST`, banco configurado com `DB_HOST` (Render/Compose) |
| `PostgresEmbutidoTest` | dados persistem ao reiniciar; PostgreSQL órfão (processo morto à força) é desligado sem perder dados; pasta em uso ou inutilizável cai no banco temporário |

Além disso, o roteiro [`docs/e2e/e2e.js`](docs/e2e/e2e.js) (Playwright) percorre todas as telas no navegador
Chromium com a aplicação em container Docker: **49 verificações**, incluindo checagem de que não há erros de
JavaScript nem violações de CSP no console.

---

## ☁️ Deploy no Render

O projeto já tem [`Dockerfile`](Dockerfile) (build multi-stage com Maven e JRE 21) e [`render.yaml`](render.yaml).

### Opção A — Blueprint (cria banco + aplicação de uma vez)

1. Acesse [dashboard.render.com](https://dashboard.render.com) → **New** → **Blueprint**.
2. Conecte este repositório do GitHub. O Render encontra o `render.yaml` na raiz automaticamente.
3. Clique em **Apply** e aguarde o build (alguns minutos). O link fica em `https://mercado-express-XXXX.onrender.com`.
   As contas de avaliação são as mesmas da tabela do topo.

### Opção B — Manual

1. **New → PostgreSQL**: nome `mercado-express-db`, database `mercado_express`, plano Free. Anote *Hostname* (interno),
   *Port*, *Database*, *Username* e *Password*.
2. **New → Web Service** → repositório do GitHub:
   - **Language/Runtime:** Docker
   - **Root Directory:** deixe em branco (o projeto está na raiz do repositório)
   - **Instance Type:** Free
   - **Environment Variables:** `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (dados do passo 1).
3. **Create Web Service** e aguarde o deploy.

> ℹ️ No plano gratuito, o serviço "dorme" após um tempo sem acesso. O primeiro acesso depois disso pode levar cerca de
> 1 minuto. A imagem foi testada com limite de **512 MB de RAM** (uso medido: ~300 MB).
