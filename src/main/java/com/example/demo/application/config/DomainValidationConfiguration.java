package com.example.demo.application.config;

import jakarta.validation.ValidatorFactory;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Use Spring's validator factory for JPA callbacks as well as MVC/method validation. */
@Configuration(proxyBeanMethods=false)
public class DomainValidationConfiguration {
    @Bean
    HibernatePropertiesCustomizer springManagedJpaValidation(ValidatorFactory validators){
        return properties->properties.put("jakarta.persistence.validation.factory",validators);
    }
}
