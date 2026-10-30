package com.example.demo.support;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

/** Dùng MySQL thật để kiểm tra khóa dòng, nhưng không tạo/xóa bảng trong database ứng dụng. */
public class IsolatedMySqlDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String originalUrl = environment.getRequiredProperty("spring.datasource.url");
        if (!originalUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("Integration tests require a MySQL datasource");
        }
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password", "");
        int databaseStart = originalUrl.indexOf('/', "jdbc:mysql://".length());
        int queryStart = originalUrl.indexOf('?', databaseStart);
        String serverUrl = originalUrl.substring(0, databaseStart + 1);
        String options = queryStart < 0 ? "" : originalUrl.substring(queryStart);
        String database = "ecommerce_test_" + UUID.randomUUID().toString().replace("-", "");

        execute(originalUrl, username, password, "CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
        TestPropertyValues.of(
                "spring.datasource.url=" + serverUrl + database + options,
                "spring.jpa.hibernate.ddl-auto=create-only",
                "wallet.settlement.worker-enabled=false",
                "wallet.bank.worker-enabled=false",
                "wallet.reconciliation.worker-enabled=false",
                "wallet.ops.worker-enabled=false",
                "wallet.notifications.worker-enabled=false",
                "app.order.cleanup-initial-delay-ms=3600000",
                "app.token.cleanup-initial-delay-ms=3600000",
                "app.flash-sale.status-sync-interval-ms=3600000")
                .applyTo(context);

        context.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> {
            if (event.getApplicationContext() == context) {
                // Tên được sinh nội bộ, luôn kiểm tra lại trước khi xóa database kiểm thử.
                if (!database.matches("ecommerce_test_[a-f0-9]{32}")) {
                    throw new IllegalStateException("Refusing to drop an unexpected database");
                }
                execute(originalUrl, username, password, "DROP DATABASE IF EXISTS `" + database + "`");
            }
        });
    }

    private static void execute(String url, String username, String password, String sql) {
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot prepare/clean the isolated MySQL test database", exception);
        }
    }
}
