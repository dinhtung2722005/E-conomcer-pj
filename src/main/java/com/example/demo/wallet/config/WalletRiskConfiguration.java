package com.example.demo.wallet.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(WalletLimitProperties.class)
public class WalletRiskConfiguration {
    @Bean("walletRiskClock") Clock walletRiskClock() { return Clock.systemUTC(); }
}
