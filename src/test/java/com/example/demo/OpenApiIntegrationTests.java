package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.api.UserRole;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.JwtUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true",
        "debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ActiveProfiles("dev")
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
class OpenApiIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JwtUtils jwt;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Test
    void swaggerUiConfigurationAndLocalAssetsAreAccessibleWithoutLogin() throws Exception {
        var ui = get("/swagger-ui.html", null);
        assertEquals(200, ui.statusCode());
        assertTrue(ui.body().contains("swagger-ui-bundle.js"));
        assertEquals(200, get("/swagger-ui/swagger-ui-bundle.js", null).statusCode());
        var config = document("/v3/api-docs/swagger-config");
        Set<String> groups = new HashSet<>();
        config.path("urls").forEach(group -> groups.add(group.path("name").asString()));
        assertEquals(Set.of("all", "users", "ecommerce", "wallet"), groups);
        assertEquals("all", config.path("urls.primaryName").asString());
        assertFalse(config.path("persistAuthorization").asBoolean());
        assertTrue(config.path("validatorUrl").asString().isEmpty());
        var yaml = get("/v3/api-docs.yaml", null);
        assertEquals(200, yaml.statusCode());
        assertTrue(yaml.body().contains("openapi:"));
    }

    @Test
    void documentsBearerOrCookieAuthenticationAndPublicEndpointsAccurately() throws Exception {
        var spec = document("/v3/api-docs");
        assertTrue(spec.path("openapi").asString().startsWith("3.1"));
        assertEquals("E-commerce & Wallet API", spec.path("info").path("title").asString());
        var schemes = spec.path("components").path("securitySchemes");
        assertEquals("bearer", schemes.path("bearerAuth").path("scheme").asString());
        assertEquals("cookie", schemes.path("cookieAuth").path("in").asString());
        assertEquals("jwt", schemes.path("cookieAuth").path("name").asString());
        assertEquals(2, spec.path("security").size());
        assertTrue(spec.path("security").get(0).has("bearerAuth"));
        assertTrue(spec.path("security").get(1).has("cookieAuth"));
        for (String path : List.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout")) {
            assertEquals(0, operation(spec, path, "post").path("security").size());
            assertTrue(operation(spec, path, "post").has("security"));
        }
        for (String path : List.of("/api/products", "/api/products/{id}", "/api/categories", "/api/flash-sales/current")) {
            var operation = operation(spec, path, "get");
            assertTrue(operation.has("security"), path);
            assertEquals(0, operation.path("security").size(), path);
        }
        assertFalse(operation(spec, "/api/wallet", "get").has("security"));
        assertTrue(operation(spec, "/api/admin/users", "get").path("responses").has("403"));
        assertTrue(operation(spec, "/api/auth/login", "post").path("responses").has("401"));
    }

    @Test
    void allBusinessRoutesHaveSummariesTagsAndTypedResponses() throws Exception {
        var spec = document("/v3/api-docs/all");
        Set<String> operationIds = new HashSet<>();
        Set<String> expected = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.example.demo.")) return;
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith("/api/")) continue;
                mapping.getMethodsCondition().getMethods().forEach(method -> {
                    expected.add(method.name().toLowerCase() + " " + path);
                    var operation = operation(spec, path, method.name().toLowerCase());
                    assertFalse(operation.path("summary").asString().isBlank(), path);
                    assertFalse(operation.path("tags").isEmpty(), path);
                    assertTrue(operation.path("responses").has("200") || operation.path("responses").has("201"), path);
                    String id = operation.path("operationId").asString();
                    assertFalse(id.isBlank(), path);
                    assertTrue(operationIds.add(id), "Duplicate operationId: " + id);
                    assertEquals("#/components/schemas/ApiError", operation.path("responses").path("400")
                            .path("content").path("application/json").path("schema").path("$ref").asString());
                });
            }
        });
        assertTrue(expected.size() >= 115);
        int documented = 0;
        for (var path : spec.path("paths").properties()) {
            assertTrue(path.getKey().startsWith("/api/"));
            for (String method : List.of("get", "post", "put", "patch", "delete")) {
                if (path.getValue().has(method)) {
                    documented++;
                    assertTrue(expected.contains(method + " " + path.getKey()));
                }
            }
        }
        assertEquals(expected.size(), documented);
        var registered = operation(spec, "/api/auth/register", "post");
        assertTrue(registered.path("responses").path("201").path("content").path("application/json").has("schema"));
    }

    @Test
    void groupedSpecificationsFollowControllerOwnershipIncludingWalletOrderRoutes() throws Exception {
        var usersSpec = document("/v3/api-docs/users");
        var accounts = usersSpec.path("paths");
        assertTrue(accounts.has("/api/auth/login"));
        assertTrue(accounts.has("/api/admin/users"));
        assertTrue(accounts.has("/api/users"));
        assertFalse(accounts.has("/api/wallet"));
        var commerce = document("/v3/api-docs/ecommerce").path("paths");
        assertTrue(commerce.has("/api/orders/checkout"));
        assertTrue(commerce.has("/api/products"));
        assertFalse(commerce.has("/api/orders/{id}/pay"));
        var wallet = document("/v3/api-docs/wallet");
        assertTrue(wallet.path("paths").has("/api/orders/{id}/pay"));
        assertTrue(wallet.path("paths").has("/api/admin/orders/{id}/refund/decision"));
        assertFalse(wallet.path("paths").has("/api/auth/login"));
        assertEquals(0, operation(usersSpec, "/api/auth/login", "post").path("security").size());
        assertTrue(operation(wallet, "/api/wallet", "get").path("responses").has("401"));
    }

    @Test
    void queryObjectsAndIdempotencyHeadersCanBeUsedFromSwagger() throws Exception {
        var spec = document("/v3/api-docs");
        var history = operation(spec, "/api/wallet/transactions", "get");
        assertFalse(history.has("requestBody"));
        Set<String> names = new HashSet<>();
        history.path("parameters").forEach(parameter -> names.add(parameter.path("name").asString()));
        assertEquals(Set.of("page", "size", "type", "status", "from", "to"), names);
        assertEquals(1, parameter(history, "page").path("schema").path("default").asInt());
        assertEquals(10, parameter(history, "size").path("schema").path("default").asInt());
        assertEquals(100, parameter(history, "size").path("schema").path("maximum").asInt());
        for (String path : List.of("/api/wallet/top-ups", "/api/wallet/withdrawals", "/api/orders/{id}/pay",
                "/api/wallet/debts/repay", "/api/admin/wallet-ops/refund-reserve/fund")) {
            var key = parameter(operation(spec, path, "post"), "Idempotency-Key");
            assertEquals("header", key.path("in").asString());
            assertTrue(key.path("required").asBoolean());
            assertFalse(key.path("description").asString().isBlank());
        }
        assertTrue(operation(spec, "/api/wallet/top-ups", "post").path("responses").has("422"));
        assertTrue(operation(spec, "/api/wallet/withdrawals", "post").path("responses").has("429"));
        var refresh = parameter(operation(spec, "/api/auth/refresh", "post"), "Refresh-Token");
        assertEquals("header", refresh.path("in").asString());
        assertFalse(refresh.path("required").asBoolean());
        var avatar = operation(spec, "/api/details/avatar", "post");
        assertTrue(avatar.path("requestBody").path("content").has("multipart/form-data"), avatar::toPrettyString);
        var upload = resolve(spec, avatar.path("requestBody").path("content")
                .path("multipart/form-data").path("schema"));
        assertEquals("binary", upload.path("properties").path("file").path("format").asString());
    }

    @Test
    void financialSchemasDocumentSecretsAsWriteOnlyAndPreserveResponseEnvelopes() throws Exception {
        var spec = document("/v3/api-docs");
        var withdraw = requestSchema(spec, "/api/wallet/withdrawals").path("properties");
        assertTrue(withdraw.path("pin").path("writeOnly").asBoolean());
        assertEquals("password", withdraw.path("pin").path("format").asString());
        assertEquals(1, withdraw.path("amount").path("multipleOf").asInt());
        var login = requestSchema(spec, "/api/auth/login").path("properties");
        assertTrue(login.path("password").path("writeOnly").asBoolean());
        var response = operation(spec, "/api/wallet", "get").path("responses").path("200")
                .path("content").path("application/json").path("schema");
        var envelope = resolve(spec, response).path("properties");
        assertTrue(envelope.has("code"));
        assertTrue(envelope.has("message"));
        var wallet = resolve(spec, envelope.path("result")).path("properties");
        assertTrue(wallet.has("balance"));
        assertFalse(wallet.has("pinHash"));
        var profile = spec.path("components").path("schemas").path("UserProfileResponse").path("properties");
        assertFalse(profile.has("password"));
        assertFalse(profile.has("passwordHash"));
        assertFalse(profile.isEmpty());
    }

    @Test
    void enablingDocumentationPreservesAuthenticationAndAdminRestrictions() throws Exception {
        assertEquals(401, get("/api/wallet", null).statusCode());
        assertEquals(401, get("/api/admin/users", null).statusCode());
        assertEquals(200, get("/api/categories", null).statusCode());
        User customer = new User();
        String unique = UUID.randomUUID().toString().replace("-", "");
        customer.setUsername("docs_" + unique);
        customer.setEmail(unique + "@example.test");
        customer.setPassword("unused-by-this-test");
        customer.setRole(UserRole.CUSTOMER);
        customer.setActive(true);
        users.saveAndFlush(customer);
        String token = jwt.generateToken(customer);
        assertEquals(403, get("/api/admin/users", token).statusCode());
        assertEquals(403, get("/api/admin/wallet-ops/summary", token).statusCode());
    }

    private JsonNode operation(JsonNode spec, String path, String method) {
        var operation = spec.path("paths").path(path).path(method);
        assertFalse(operation.isMissingNode(), method + " " + path);
        return operation;
    }

    private JsonNode parameter(JsonNode operation, String name) {
        for (var parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asString())) return parameter;
        }
        return fail("Missing parameter " + name);
    }

    private JsonNode requestSchema(JsonNode spec, String path) {
        return resolve(spec, operation(spec, path, "post").path("requestBody")
                .path("content").path("application/json").path("schema"));
    }

    private JsonNode resolve(JsonNode spec, JsonNode schema) {
        return schema.has("$ref") ? spec.at(schema.path("$ref").asString().substring(1)) : schema;
    }

    private JsonNode document(String path) throws Exception {
        var response = get(path, null);
        assertEquals(200, response.statusCode(), () -> path + ": " + response.body());
        return json.readTree(response.body());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(45)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
