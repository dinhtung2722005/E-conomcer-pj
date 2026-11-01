package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.SpringPersistenceUnitInfo;
import org.springframework.test.context.ContextConfiguration;
import com.example.demo.support.IsolatedMySqlDatabase;

@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN","logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers=IsolatedMySqlDatabase.class)
class WalletMigrationIntegrationTests {
    @Autowired DataSource dataSource;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;

    @Test void freshDatabaseMigrationsMatchHibernateAndSecondRunMakesNoChanges()throws Exception {
        withDatabase(target->{
            var migrations=flyway(target);assertEquals(3,migrations.migrate().migrationsExecuted);
            validateSchema(target);
            assertEquals(0,migrations.migrate().migrationsExecuted);
            try(var connection=target.getConnection();var statement=connection.createStatement();
                    var rows=statement.executeQuery("select count(*) from information_schema.TRIGGERS where TRIGGER_SCHEMA=database()")){
                rows.next();assertEquals(12,rows.getInt(1));
            }
        });
    }

    @Test void explicitBaselinePreservesExistingRowsAndBackfillsPendingQuota()throws Exception {
        withDatabase(target->{
            Flyway.configure().dataSource(target).locations("classpath:db/migration").target("1").load().migrate();
            try(var connection=target.getConnection();var statement=connection.createStatement()){
                statement.execute("DROP TABLE wallet_limit_reservations");
                statement.execute("DROP TABLE flyway_schema_history");
                statement.execute("INSERT INTO users(id,username,email,password,role,is_active,is_deleted,token_version) VALUES(1,'legacy','legacy@example.test','test-only','CUSTOMER',1,0,0)");
                statement.execute("INSERT INTO wallets(id,user_id,created_at,updated_at,version) VALUES(1,1,NOW(),NOW(),0)");
                statement.execute("INSERT INTO wallet_operations(id,wallet_id,fingerprint,request_key,reference,type) VALUES(1,1,REPEAT('a',64),'legacy','legacy','DEPOSIT')");
                statement.execute("INSERT INTO wallet_transactions(wallet_id,operation_id,amount,idempotency_key,reference_id,transaction_type,created_at,updated_at,version) VALUES(1,1,25,'legacy','legacy','DEPOSIT','2026-10-31 08:00:00','2026-10-31 08:00:00',0)");
            }
            assertThrows(org.flywaydb.core.api.FlywayException.class,()->flyway(target).migrate());
            flyway(target).baseline();assertEquals(2,flyway(target).migrate().migrationsExecuted);
            validateSchema(target);
            try(var connection=target.getConnection();var statement=connection.createStatement();
                    var rows=statement.executeQuery("select amount,reserved_at from wallet_limit_reservations where operation_id=1")){
                assertTrue(rows.next());assertEquals(0,new java.math.BigDecimal("25").compareTo(rows.getBigDecimal(1)));
                assertEquals("2026-10-31T01:00",rows.getObject(2,java.time.LocalDateTime.class).toString());
            }
        });
    }
    private Flyway flyway(DataSource target){return Flyway.configure().dataSource(target).locations("classpath:db/migration")
        .baselineVersion("1").baselineOnMigrate(false).cleanDisabled(true).placeholders(Map.of("legacyWalletTimeZone","+07:00")).load();}
    private void validateSchema(DataSource target){
        var source=context.getBean("&entityManagerFactory",LocalContainerEntityManagerFactoryBean.class).getPersistenceUnitInfo();
        var unit=new SpringPersistenceUnitInfo(getClass().getClassLoader());unit.setPersistenceUnitName("migration-validation");
        unit.setTransactionType(jakarta.persistence.PersistenceUnitTransactionType.RESOURCE_LOCAL);
        unit.setNonJtaDataSource(target);unit.setExcludeUnlistedClasses(true);
        assertTrue(source.getManagedClassNames().size()>30);source.getManagedClassNames().forEach(unit::addManagedClassName);
        var settings=new HashMap<String,Object>();
        context.getBean(jakarta.persistence.EntityManagerFactory.class).getProperties().forEach((key,value)->{
            if(key.equals("hibernate.physical_naming_strategy")||key.equals("hibernate.implicit_naming_strategy"))settings.put(key,value);
        });
        settings.put("hibernate.hbm2ddl.auto","validate");settings.put("jakarta.persistence.validation.mode","none");
        try(var factory=new org.hibernate.jpa.HibernatePersistenceProvider().createContainerEntityManagerFactory(unit.asStandardPersistenceUnitInfo(),settings)){assertNotNull(factory);}
    }
    @FunctionalInterface interface DatabaseCheck {void run(DataSource target)throws Exception;}
    private void withDatabase(DatabaseCheck check)throws Exception {
        String database="ecommerce_test_"+UUID.randomUUID().toString().replace("-","");
        assertTrue(database.matches("ecommerce_test_[a-f0-9]{32}"));
        String original=environment.getRequiredProperty("spring.datasource.url");
        assertTrue(original.matches(".*?/ecommerce_test_[a-f0-9]{32}(?:\\?.*)?"));
        String url=original.replaceFirst("/ecommerce_test_[a-f0-9]{32}","/"+database);
        try(var connection=dataSource.getConnection();var statement=connection.createStatement()){
            statement.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4");
            try{check.run(new DriverManagerDataSource(url,environment.getRequiredProperty("spring.datasource.username"),environment.getProperty("spring.datasource.password","")));}
            finally{statement.execute("DROP DATABASE `"+database+"`");}
        }
    }
}
