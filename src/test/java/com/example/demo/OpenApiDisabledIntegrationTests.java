package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.api.UserRole;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.JwtUtils;

import io.swagger.v3.oas.models.OpenAPI;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false",
        "debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ActiveProfiles("dev")
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
class OpenApiDisabledIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired ApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtUtils jwt;

    @Test
    void disablingDocumentationRejectsEvenAuthenticatedAdmins() throws Exception {
        assertTrue(context.getBeansOfType(OpenAPI.class).isEmpty());
        User admin = new User();
        String unique = UUID.randomUUID().toString().replace("-", "");
        admin.setUsername("docs_" + unique);
        admin.setEmail(unique + "@example.test");
        admin.setPassword("unused-by-this-test");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        users.saveAndFlush(admin);
        String token = jwt.generateToken(admin);
        var client = HttpClient.newHttpClient();
        for (String path : List.of("/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js",
                "/v3/api-docs", "/v3/api-docs/all", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml")) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build();
            assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode(), path);
        }
    }
}
