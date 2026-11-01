package com.example.demo.application.config;

import java.util.Base64;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Reject unsafe production defaults before connecting Hibernate or creating application beans. */
@Configuration(proxyBeanMethods=false) @Profile("prod")
public class ProductionSettings {
    @Bean static BeanFactoryPostProcessor requireProductionSettings(Environment environment) {
        return factory->validate(environment);
    }
    public static void validate(Environment environment) {
        String user=environment.getProperty("spring.datasource.username","").strip();
        if(user.isEmpty()||user.equalsIgnoreCase("root"))
            throw new IllegalStateException("Production requires a dedicated non-root database account");
        String url=environment.getProperty("spring.datasource.url","");
        if(!url.startsWith("jdbc:mysql://")||url.matches("(?is).*[?&](user|password)=.*"))
            throw new IllegalStateException("Configure a MySQL URL without inline database credentials");
        if(!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto")))
            throw new IllegalStateException("Production Hibernate must use ddl-auto=validate");
        if(environment.getProperty("spring.flyway.enabled",Boolean.class,false))
            throw new IllegalStateException("Run migrations separately with migration credentials");
        try {
            String secret=environment.getProperty("app.jwt.secret","");
            if(Base64.getDecoder().decode(secret.strip()).length<32)throw new IllegalArgumentException();
        } catch(IllegalArgumentException exception) {
            throw new IllegalStateException("Production requires an external Base64 JWT key of at least 32 bytes");
        }
    }
}
