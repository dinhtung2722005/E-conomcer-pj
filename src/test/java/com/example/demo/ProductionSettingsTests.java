package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import com.example.demo.application.config.ProductionSettings;

class ProductionSettingsTests {
    private MockEnvironment settings(){
        return new MockEnvironment().withProperty("spring.datasource.username","wallet_runtime")
            .withProperty("spring.datasource.url","jdbc:mysql://database/ecommerce?sslMode=VERIFY_IDENTITY")
            .withProperty("spring.jpa.hibernate.ddl-auto","validate").withProperty("spring.flyway.enabled","false")
            .withProperty("app.jwt.secret",Base64.getEncoder().encodeToString(new byte[48]));
    }
    @Test void dedicatedRuntimeSettingsAreAccepted(){assertDoesNotThrow(()->ProductionSettings.validate(settings()));}
    @Test void rootAndInlineCredentialsAreRejected(){
        assertThrows(IllegalStateException.class,()->ProductionSettings.validate(settings().withProperty("spring.datasource.username","root")));
        assertThrows(IllegalStateException.class,()->ProductionSettings.validate(settings().withProperty("spring.datasource.url","jdbc:mysql://database/ecommerce?user=root")));
    }
    @Test void automaticDdlAndRuntimeMigrationsAreRejected(){
        assertThrows(IllegalStateException.class,()->ProductionSettings.validate(settings().withProperty("spring.jpa.hibernate.ddl-auto","update")));
        assertThrows(IllegalStateException.class,()->ProductionSettings.validate(settings().withProperty("spring.flyway.enabled","true")));
    }
    @Test void missingWeakAndInvalidJwtKeysAreRejected(){
        for(String key:java.util.List.of("","not-base64",Base64.getEncoder().encodeToString(new byte[16])))
            assertThrows(IllegalStateException.class,()->ProductionSettings.validate(settings().withProperty("app.jwt.secret",key)));
    }
}
