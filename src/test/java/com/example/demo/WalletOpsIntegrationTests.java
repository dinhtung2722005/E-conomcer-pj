package com.example.demo;
import com.example.demo.wallet.api.AlertStatus;
import com.example.demo.user.api.UserRole;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.*;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.*;
import com.example.demo.wallet.service.*;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.api.dto.*;
import tools.jackson.databind.ObjectMapper;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"debug=false","logging.level.org.springframework=WARN","logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers=IsolatedMySqlDatabase.class)
class WalletOpsIntegrationTests {
    @Autowired UserRepository users; @Autowired JwtUtils jwt; @Autowired ObjectMapper json;
    @Autowired WalletAlertService alerts; @Autowired WalletAlertRepository alertRows;
    @Autowired WalletOpsService ops; @Autowired WalletOpsAuditRepository audit;
    @Autowired WalletJobCursorService cursors;
    @Value("${local.server.port}") int port;
    private User user(UserRole role) {String n="ops_"+UUID.randomUUID().toString().replace("-",""); return users.saveAndFlush(User.builder().username(n).email(n+"@example.test").password("test-only").role(role).build());}
    private HttpResponse<String> http(String path,String token,String body)throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(body==null?b.GET().build():b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void allDashboardListsEnforceAdminAndPagination()throws Exception {
        var shell=http("/wallet-ops.html",null,null); assertEquals(200,shell.statusCode()); assertTrue(shell.body().contains("Vận hành ví"));
        String admin=jwt.generateToken(user(UserRole.ADMIN)),customer=jwt.generateToken(user(UserRole.CUSTOMER));
        for(String name:List.of("summary","transfers","holds","refunds","settlements","alerts","audit","security-events","debts")) {
            String path="/api/admin/wallet-ops/"+name;
            assertEquals(401,http(path,null,null).statusCode()); assertEquals(403,http(path,customer,null).statusCode());
            var response=http(path,admin,null); assertEquals(200,response.statusCode(),response.body());
            if(!name.equals("summary"))assertEquals(400,http(path+"?size=101",admin,null).statusCode());
        }
    }
    @Test void alertsDeduplicateAndReviewWritesAudit()throws Exception {
        String reference=UUID.randomUUID().toString(); alerts.record("TEST",reference,"Needs review"); alerts.record("TEST",reference,"Needs review");
        var row=alertRows.findAll().stream().filter(a->a.getReference().equals(reference)).findFirst().orElseThrow();
        assertEquals(1,alertRows.findAll().stream().filter(a->a.getReference().equals(reference)).count());
        User admin=user(UserRole.ADMIN); long before=audit.count();
        var response=http("/api/admin/wallet-ops/alerts/"+row.getId()+"/review",jwt.generateToken(admin),"{\"status\":\"ACKNOWLEDGED\",\"reason\":\"Investigating\"}");
        assertEquals(200,response.statusCode(),response.body()); assertEquals(before+1,audit.count());
        assertEquals(admin.getId(),alertRows.findById(row.getId()).orElseThrow().getReviewedBy());
        alerts.record("TEST",reference,"Still pending"); assertEquals(AlertStatus.ACKNOWLEDGED,alertRows.findById(row.getId()).orElseThrow().getStatus());
    }
    @Test void checkpointContinuesAcrossCallersAndWrapsAfterEnd() {
        String name="test-"+UUID.randomUUID().toString().substring(0,8);
        assertEquals(List.of(1L,2L),cursors.claim(name,(after,page)->{assertEquals(0,after);return List.of(1L,2L);}));
        assertEquals(List.of(3L),cursors.claim(name,(after,page)->{assertEquals(2,after);return List.of(3L);}));
        assertTrue(cursors.claim(name,(after,page)->{assertEquals(3,after);return List.of();}).isEmpty());
        cursors.claim(name,(after,page)->{assertEquals(0,after);return List.of();});
    }
}
