package br.com.fiap.mercadoexpress.config;

import br.com.fiap.mercadoexpress.security.LoginFalhaHandler;
import br.com.fiap.mercadoexpress.security.LoginSucessoHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Regras de acesso do Mercado Express.
 *
 * Público ........ /, /login, /cadastro, arquivos estáticos e páginas de erro
 * ADMIN e CLIENTE  consultar produtos (GET /produtos e /produtos/{id})
 * Somente ADMIN .. criar/editar/excluir produtos e tudo em /admin/**
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           LoginSucessoHandler loginSucessoHandler,
                                           LoginFalhaHandler loginFalhaHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Rotas públicas
                .requestMatchers("/", "/index.html", "/login", "/cadastro", "/acesso-negado", "/error",
                        "/css/**", "/js/**", "/img/**", "/favicon.ico", "/favicon.svg").permitAll()
                // Área do administrador
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Telas e operações de escrita de produtos: somente ADMIN
                .requestMatchers(HttpMethod.GET, "/produtos/novo", "/produtos/*/editar").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/produtos", "/produtos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/produtos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/produtos/**").hasRole("ADMIN")
                // Consulta de produtos: qualquer usuário logado (ADMIN ou CLIENTE)
                .requestMatchers(HttpMethod.GET, "/produtos", "/produtos/*").hasAnyRole("ADMIN", "CLIENTE")
                // Qualquer outra rota exige login
                .anyRequest().authenticated()
            )
            // Tela de login própria (não usa a tela padrão do Spring Security)
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("senha")
                .successHandler(loginSucessoHandler)
                .failureHandler(loginFalhaHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?saiu")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(ex -> ex.accessDeniedPage("/acesso-negado"))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
                    + "form-action 'self'; frame-ancestors 'none'; base-uri 'self'"))
                .referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            );
        // CSRF permanece HABILITADO (padrão): os formulários Thymeleaf enviam o token automaticamente.
        return http.build();
    }
}
