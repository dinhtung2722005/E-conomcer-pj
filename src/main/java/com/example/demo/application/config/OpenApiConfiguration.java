package com.example.demo.application.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Documentation metadata only; SecurityConfig and method security remain authoritative. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfiguration {
    @Bean
    OpenAPI projectOpenApi() {
        return new OpenAPI()
                .info(new Info().title("E-commerce & Wallet API").version("v1")
                        .description("""
                                API tài khoản, thương mại điện tử và ví nội bộ (VND).

                                Đăng nhập tại `POST /api/auth/login`, lấy `result.accessToken`,
                                bấm **Authorize** và chỉ dán token (không thêm `Bearer`).
                                Trình duyệt cũng có thể dùng cookie HttpOnly `jwt` sau đăng nhập cùng origin.
                                Các API quản trị yêu cầu tài khoản ADMIN; token không thay thế mã PIN của ví.

                                Phân trang bắt đầu từ `page=1`, mặc định `size=10`, tối đa 100.
                                Response dùng `{code, message, result}`; HTTP status là trạng thái thực tế.
                                `Idempotency-Key` phải giữ nguyên khi thử lại cùng yêu cầu và cùng nội dung;
                                dùng khóa mới cho thao tác mới. Nạp/rút chỉ hoàn tất sau xác nhận từ đối tác.
                                """))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Access token từ result.accessToken của API đăng nhập."))
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("jwt")
                                .description("Cookie HttpOnly do API đăng nhập thiết lập; trình duyệt gửi tự động cùng origin."))
                        .addSchemas("ApiError", errorSchema()))
                // Separate requirements mean bearer OR cookie, matching JwtUtils.
                .security(List.of(new SecurityRequirement().addList("bearerAuth"),
                        new SecurityRequirement().addList("cookieAuth")));
    }

    @Bean
    GroupedOpenApi allApi() {
        return GroupedOpenApi.builder().group("all").pathsToMatch("/api/**").build();
    }

    @Bean
    GroupedOpenApi usersApi() {
        return moduleApi("users", "user");
    }

    @Bean
    GroupedOpenApi ecommerceApi() {
        return moduleApi("ecommerce", "ecommerce");
    }

    @Bean
    GroupedOpenApi walletApi() {
        return moduleApi("wallet", "wallet");
    }

    private GroupedOpenApi moduleApi(String group, String module) {
        return GroupedOpenApi.builder().group(group).pathsToMatch("/api/**")
                .packagesToScan("com.example.demo." + module + ".controller").build();
    }

    @Bean
    GlobalOpenApiCustomizer commonApiDocumentation() {
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            boolean publicApi = Set.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                    .contains(path) || (method == PathItem.HttpMethod.GET
                    && (path.startsWith("/api/categories") || path.startsWith("/api/products")
                    || path.startsWith("/api/flash-sales")));
            if (publicApi) {
                operation.setSecurity(new ArrayList<>());
                if (path.equals("/api/auth/login") || path.equals("/api/auth/refresh")) {
                    addError(operation, "401", "Thông tin đăng nhập hoặc refresh token không hợp lệ.");
                }
            } else {
                addError(operation, "401", "Chưa đăng nhập, token hết hạn hoặc phiên đã bị thu hồi.");
                addError(operation, "403", "Không có quyền truy cập tài nguyên hoặc thao tác này.");
            }
            addError(operation, "400", "Dữ liệu không hợp lệ hoặc không thỏa điều kiện nghiệp vụ; xem message.");
            addError(operation, "404", "Không tìm thấy tài nguyên.");
            addError(operation, "409", "Xung đột dữ liệu hoặc thao tác đồng thời.");
            addError(operation, "500", "Lỗi máy chủ; dùng X-Request-Id để tra cứu log.");
            if (operation.getTags() != null && operation.getTags().stream().anyMatch(tag -> tag.contains("Wallet"))) {
                addError(operation, "429", "Vượt hạn mức ngày/tần suất, PIN bị khóa hoặc đang trong thời gian chờ bảo mật.");
            }
            if (method == PathItem.HttpMethod.POST && (path.equals("/api/wallet/top-ups")
                    || path.equals("/api/wallet/withdrawals") || path.equals("/api/orders/{id}/pay"))) {
                addError(operation, "422", "Số tiền vượt hạn mức cho một giao dịch.");
            }
            if (path.contains("/top-ups") || path.contains("/withdrawals") || path.contains("/bank-accounts")
                    || path.contains("/recovery") || path.endsWith("/avatar")) {
                addError(operation, "503", "Dịch vụ phụ trợ chưa cấu hình hoặc tạm thời không sẵn sàng.");
            }
            if (path.endsWith("/avatar")) {
                addError(operation, "413", "Ảnh vượt kích thước upload cho phép.");
            }
            if (operation.getParameters() != null) {
                operation.getParameters().stream()
                        .filter(parameter -> "Idempotency-Key".equalsIgnoreCase(parameter.getName()))
                        .forEach(parameter -> parameter.description("""
                                Khóa duy nhất cho một thao tác. Giữ nguyên khóa và nội dung khi thử lại;
                                dùng khóa mới cho thao tác mới. Không điền mã PIN hoặc thông tin cá nhân vào khóa.
                                """).example("8de10ac5-c3f2-4ec5-a284-60873cddfc95"));
            }
        }));
    }

    private Schema<?> errorSchema() {
        var schema = new ObjectSchema();
        schema.addProperty("code", new IntegerSchema().example(400));
        schema.addProperty("message", new StringSchema().example("Nội dung request không hợp lệ"));
        schema.addProperty("result", new Schema<>().type("null"));
        schema.setRequired(List.of("code", "message", "result"));
        return schema;
    }

    private void addError(Operation operation, String status, String description) {
        operation.getResponses().putIfAbsent(status, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json",
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
    }
}
