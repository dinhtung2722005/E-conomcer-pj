package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.*;
import com.example.demo.user.exception.*;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.*;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.entity.WalletSecurityEvent.*;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.service.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "debug=false","logging.level.org.springframework=WARN","logging.level.org.hibernate=WARN",
    "app.logging.directory=target/wallet-audit-test-logs","wallet.audit-test=true"})
@ContextConfiguration(initializers=IsolatedMySqlDatabase.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletAuditLoggingIntegrationTests {
    @Autowired DataSource source; @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users; @Autowired WalletRepository wallets;
    @Autowired WalletSecurityEventRepository securityEvents; @Autowired WalletSecurityRepository security;
    @Autowired WalletOpsAuditRepository audit; @Autowired WalletService walletService; @Autowired WalletPinService pins;
    @Autowired WalletLedgerService ledger; @Autowired WalletJournalRepository journals;
    @Autowired PasswordEncoder encoder; @Autowired PlatformTransactionManager manager;
    @Autowired JwtUtils jwt; @Autowired ObjectMapper json; @Value("${local.server.port}") int port;
    private User owner; private Long walletId;

    @BeforeAll void protectTables() {
        new ResourceDatabasePopulator(new ClassPathResource("db/manual/wallet-audit-append-only.sql")).execute(source);
    }
    @BeforeEach void setup() {
        String name="logging_"+UUID.randomUUID().toString().replace("-","");
        owner=users.saveAndFlush(User.builder().username(name).email(name+"@example.test")
                .password(encoder.encode("password123")).role(UserRole.CUSTOMER).build());
        authenticate(owner);walletId=walletService.initializeMyWallet().getId();
        MDC.put("requestId","test-request-"+UUID.randomUUID());
        var request=new SetupPinRequest();request.setCurrentPassword("password123");request.setNewPin("123456");request.setConfirmPin("123456");pins.setupPin(request);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();MDC.clear();}
    private void authenticate(User user){var p=new UserPrincipal(user);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    private List<WalletSecurityEvent> ownEvents(){return securityEvents.findAll().stream().filter(e->owner.getId().equals(e.getActorId())).toList();}

    @Test void deniedPinAttemptsSurviveCallerRollbackAndLockHasItsOwnEvent(){
        var tx=new TransactionTemplate(manager);
        assertThrows(AppException.class,()->tx.executeWithoutResult(s->pins.authorize("000000","test")));
        for(int i=0;i<4;i++)assertThrows(AppException.class,()->pins.authorize("000000","test"));
        assertEquals(5,security.findById(walletId).orElseThrow().getFailedPinAttempts());
        var events=ownEvents();
        assertEquals(5,events.stream().filter(e->e.getAction()==Action.PIN_VERIFY&&e.getOutcome()==Outcome.REJECTED).count());
        assertEquals(1,events.stream().filter(e->e.getAction()==Action.PIN_LOCKED).count());
        String serialized=json.writeValueAsString(events);
        assertFalse(serialized.contains("000000"));assertFalse(serialized.contains("password123"));
        assertTrue(events.stream().allMatch(e->MDC.get("requestId").equals(e.getRequestId())));
    }

    @Test void financialSuccessIsLoggedOnlyAfterCommitAndCorrelatesJournal(){
        Logger logger=(Logger)LoggerFactory.getLogger(WalletLedgerService.class);
        var captured=new ListAppender<ILoggingEvent>();captured.start();logger.addAppender(captured);
        try{
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                var wallet=wallets.findByIdForUpdate(walletId).orElseThrow();wallet.setBalance(new BigDecimal("20"));ledger.open(wallet);
            });
            captured.list.clear();
            String rolledBack="test-rollback:"+UUID.randomUUID();
            assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).executeWithoutResult(tx->{
                ledger.post(rolledBack,"VND",Map.of("test:a",BigDecimal.ONE,"test:b",BigDecimal.ONE.negate()));
                assertTrue(captured.list.isEmpty());throw new IllegalStateException("rollback");
            }));
            assertTrue(captured.list.isEmpty());
            String committed="test-commit:"+UUID.randomUUID();
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                ledger.post(committed,"VND",Map.of("test:a",BigDecimal.ONE,"test:b",BigDecimal.ONE.negate()));
                assertTrue(captured.list.isEmpty());
            });
            assertEquals(1,captured.list.size());
            assertEquals(committed,captured.list.getFirst().getMDCPropertyMap().get("eventKey"));
            var journal=journals.findAll().stream().filter(j->j.getEventKey().equals(committed)).findFirst().orElseThrow();
            assertEquals(MDC.get("requestId"),journal.getRequestId());
            assertTrue(journals.findAll().stream().noneMatch(j->j.getEventKey().equals(rolledBack)));
        }finally{logger.detachAppender(captured);captured.stop();}
    }

    @Test void rawSqlCannotUpdateOrDeleteAnyProtectedAuditTable(){
        audit.saveAndFlush(WalletOpsAudit.builder().actorId(owner.getId()).walletId(walletId).action("TEST").reference("test").reason("Review").build());
        var freeze=new WalletFreezeRequest();freeze.setReason("Review");walletService.freezeMyWallet(freeze);
        new TransactionTemplate(manager).executeWithoutResult(tx->{var w=wallets.findByIdForUpdate(walletId).orElseThrow();w.setBalance(BigDecimal.TEN);ledger.open(w);});
        Map<String,String> fields=Map.of("wallet_ops_audit","reason","wallet_security_events","code",
                "wallet_status_changes","reason","wallet_journals","currency","wallet_journal_entries","account");
        fields.forEach((table,column)->{
            Long id=jdbc.queryForObject("select min(id) from "+table,Long.class);assertNotNull(id);
            var update=assertThrows(org.springframework.dao.DataAccessException.class,
                    ()->jdbc.update("update "+table+" set "+column+"=? where id=?","XXX",id));
            assertTrue(update.getMostSpecificCause().getMessage().contains("append-only"));
            var delete=assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from "+table+" where id=?",id));
            assertTrue(delete.getMostSpecificCause().getMessage().contains("append-only"));
            assertEquals(1,jdbc.queryForObject("select count(*) from "+table+" where id=?",Integer.class,id));
        });
    }

    @Test void filteredAuditAndSecurityApiAreAdminOnlyAndReturnRequestId()throws Exception{
        var admin=users.saveAndFlush(User.builder().username("auditadmin_"+UUID.randomUUID()).email(UUID.randomUUID()+"@example.test").password("test-only").role(UserRole.ADMIN).build());
        String reference="audit-"+UUID.randomUUID();
        audit.saveAndFlush(WalletOpsAudit.builder().actorId(owner.getId()).walletId(walletId).action("TEST").reference(reference).reason("Review").build());
        for(String endpoint:List.of("audit","security-events")){
            String path="/api/admin/wallet-ops/"+endpoint;
            var unauth=http(path,null,"anonymous-request");assertEquals(401,unauth.statusCode());
            assertEquals("anonymous-request",unauth.headers().firstValue("X-Request-Id").orElseThrow());
            var forbidden=http(path,jwt.generateToken(owner),"forbidden-request");assertEquals(403,forbidden.statusCode());
            String query="?actorId="+owner.getId()+"&walletId="+walletId+"&requestId="+MDC.get("requestId")
                    +"&from="+LocalDateTime.now().minusDays(1).withNano(0)+"&to="+LocalDateTime.now().plusDays(1).withNano(0)
                    +(endpoint.equals("audit")?"&action=TEST&reference="+reference:"&action=PIN_SETUP&outcome=SUCCESS");
            var result=http(path+query,jwt.generateToken(admin),"filtered-request");assertEquals(200,result.statusCode(),result.body());
            var data=json.readTree(result.body()).get("result");assertEquals(1,data.get("totalElements").asInt());
            assertEquals(walletId.longValue(),data.get("data").get(0).get("walletId").asLong());
            assertEquals(400,http(path+"?walletId=-1",jwt.generateToken(admin),null).statusCode());
            assertEquals(400,http(path+"?from=2026-10-30T00:00:00&to=2026-10-29T00:00:00",jwt.generateToken(admin),null).statusCode());
        }
        assertTrue(securityEvents.findAll().stream().anyMatch(e->e.getAction()==Action.ACCESS_DENIED&&owner.getId().equals(e.getActorId())&&"forbidden-request".equals(e.getRequestId())));
    }

    @Test void restrictedDatabaseAccountCanAppendButCannotUpdateDeleteOrDropAudit()throws Exception{
        String username="audit_test_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        String password=UUID.randomUUID().toString();
        String database=jdbc.queryForObject("select database()",String.class);
        assertTrue(database.matches("ecommerce_test_[a-f0-9]{32}"));
        String account="'"+username+"'@'localhost'";
        jdbc.execute("create user "+account+" identified by '"+password+"'");
        try{
            jdbc.execute("grant select, insert on `"+database+"`.wallet_ops_audit to "+account);
            String url;try(var original=source.getConnection()){url=original.getMetaData().getURL();}
            try(var connection=java.sql.DriverManager.getConnection(url,username,password);var statement=connection.createStatement()){
                assertEquals(1,statement.executeUpdate("insert into wallet_ops_audit(actor_id,action,reference,reason,created_at) values ("
                        +owner.getId()+",'TEST','restricted','Append allowed',NOW(6))"));
                for(String command:List.of("update wallet_ops_audit set reason='changed'",
                        "delete from wallet_ops_audit","truncate table wallet_ops_audit","drop table wallet_ops_audit",
                        "drop trigger wallet_ops_audit_no_update")){
                    assertThrows(java.sql.SQLException.class,()->statement.execute(command));
                }
            }
        }finally{jdbc.execute("drop user "+account);}
    }
    private HttpResponse<String> http(String path,String token,String requestId)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(requestId!=null)builder.header("X-Request-Id",requestId);
        return HttpClient.newHttpClient().send(builder.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
}
