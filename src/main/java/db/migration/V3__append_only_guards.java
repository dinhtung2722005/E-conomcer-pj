package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Arrays;
import org.flywaydb.core.api.migration.*;

/** Installs missing guards and preserves triggers already installed on an existing database. */
public class V3__append_only_guards extends BaseJavaMigration {
    private byte[] script() {
        try(var stream=getClass().getClassLoader().getResourceAsStream("db/manual/wallet-audit-append-only.sql")) {
            if(stream==null)throw new IllegalStateException("Missing append-only migration resource");
            return stream.readAllBytes();
        } catch(java.io.IOException exception) { throw new IllegalStateException("Cannot read append-only migration",exception); }
    }
    @Override public Integer getChecksum() { return Arrays.hashCode(script()); }
    @Override public boolean canExecuteInTransaction() { return false; }
    @Override public void migrate(Context context)throws Exception {
        String sql=new String(script(),StandardCharsets.UTF_8).replaceAll("(?m)^--.*$","");
        for(String statement:sql.split(";")) {
            statement=statement.strip();if(statement.isEmpty())continue;
            var matcher=java.util.regex.Pattern.compile("CREATE TRIGGER ([a-z_]+)").matcher(statement);
            if(!matcher.find())throw new IllegalStateException("Unexpected append-only migration statement");
            try(var check=context.getConnection().prepareStatement("select count(*) from information_schema.TRIGGERS where TRIGGER_SCHEMA=database() and TRIGGER_NAME=?")) {
                check.setString(1,matcher.group(1));
                try(var rows=check.executeQuery()){rows.next();if(rows.getInt(1)>0)continue;}
            }
            try(var command=context.getConnection().createStatement()){command.execute(statement);}
        }
    }
}
