package com.example.demo.application.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** External bank calls cannot occupy the threads responsible for money settlement or session cleanup. */
@Configuration(proxyBeanMethods=false)
public class SchedulingConfiguration {
    @Bean("taskScheduler") ThreadPoolTaskScheduler maintenance() { return scheduler("maintenance-",2); }
    @Bean("walletBankScheduler") ThreadPoolTaskScheduler bank() { return scheduler("bank-poll-",1); }
    @Bean("walletSettlementScheduler") ThreadPoolTaskScheduler settlement() { return scheduler("settlement-",1); }
    @Bean("walletNotificationScheduler") ThreadPoolTaskScheduler notifications() { return scheduler("notifications-",1); }
    @Bean("walletAuditScheduler") ThreadPoolTaskScheduler audit() { return scheduler("wallet-audit-",2); }
    private ThreadPoolTaskScheduler scheduler(String prefix,int size) {
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(size);scheduler.setThreadNamePrefix(prefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);scheduler.setAwaitTerminationSeconds(5);return scheduler;
    }
}
