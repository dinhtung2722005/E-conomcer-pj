package com.example.demo.user.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;

class AdminBootstrapLoggingTests {
    @Test void missingSecretDoesNotCreateAnUnknownAdminPassword() throws Exception {
        var users=mock(UserRepository.class);
        var encoder=mock(PasswordEncoder.class);
        new ApplicationInitConfig(encoder).applicationRunner(users,"").run(null);
        verify(users,never()).save(any());
        verifyNoInteractions(encoder);
    }
    @Test void configuredBootstrapSecretIsHashedAndNeverLogged() throws Exception {
        var users=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
        String secret="private bootstrap password";when(encoder.encode(secret)).thenReturn("hashed-value");
        Logger logger=(Logger)LoggerFactory.getLogger(ApplicationInitConfig.class);
        var events=new ListAppender<ILoggingEvent>();events.start();logger.addAppender(events);
        try{
            new ApplicationInitConfig(encoder).applicationRunner(users,secret).run(null);
            var saved=ArgumentCaptor.forClass(User.class);verify(users).save(saved.capture());
            assertEquals("hashed-value",saved.getValue().getPassword());
            assertFalse(events.list.isEmpty());
            assertTrue(events.list.stream().noneMatch(e->e.getFormattedMessage().contains(secret)));
        }finally{logger.detachAppender(events);events.stop();}
    }
    @Test void existingAdminIsNeverRecreatedOrGivenANewPassword() throws Exception {
        var users=mock(UserRepository.class);when(users.existsByUsername("admin")).thenReturn(true);
        var encoder=mock(PasswordEncoder.class);
        new ApplicationInitConfig(encoder).applicationRunner(users,"configured-secret").run(null);
        verify(users,never()).save(any());verifyNoInteractions(encoder);
    }
}
