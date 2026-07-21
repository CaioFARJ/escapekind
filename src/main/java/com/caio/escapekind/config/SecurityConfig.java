package com.caio.escapekind.config;

import com.caio.escapekind.model.Admin;
import com.caio.escapekind.repository.AdminRepository;
import com.caio.escapekind.service.AdminUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança da aplicação.
 *
 * Regras:
 *  - Recursos estáticos (HTML, CSS, JS) → públicos
 *  - POST /api/sessions e POST /api/events → públicos (jogadores anónimos)
 *  - GET /api/admin/** → requer autenticação HTTP Basic com papel ADMIN
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API REST stateless — CSRF não se aplica
            .authorizeHttpRequests(auth -> auth
                // Recursos estáticos e raiz
                .requestMatchers("/", "/index.html", "/admin.html", "/css/**", "/js/**", "/img/**", "/narrative.json").permitAll()
                // Endpoints públicos do jogo
                .requestMatchers(HttpMethod.POST, "/api/sessions").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/sessions/*/finish").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/events").permitAll()
                // Painel de administração — requer autenticação
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Tudo o resto requer autenticação
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults()); // Autenticação via HTTP Basic

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(AdminUserDetailsService userDetailsService) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Inicializa o administrador padrão na base de dados na primeira execução.
     * Se já existir um admin com esse username, não faz nada.
     */
    @Bean
    public CommandLineRunner initAdmin(AdminRepository adminRepository) {
        return args -> {
            if (adminRepository.findByUsername(adminUsername).isEmpty()) {
                String hash = passwordEncoder().encode(adminPassword);
                adminRepository.save(new Admin(adminUsername, hash));
                System.out.println("[EscapeKind] Administrador inicial criado: " + adminUsername);
            }
        };
    }
}
