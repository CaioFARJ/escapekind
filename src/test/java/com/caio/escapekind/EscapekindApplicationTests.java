package com.caio.escapekind;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de integração base: verifica que o contexto Spring arranca sem erros.
 * Usa um perfil de teste separado para evitar dependência de PostgreSQL.
 */
@SpringBootTest
@ActiveProfiles("test")
class EscapekindApplicationTests {

    @Test
    void contextLoads() {
        // Se o contexto do Spring falhar ao arrancar, este teste falha automaticamente.
    }
}
