-- Generate grants for a NEW runtime role/account in the selected database.
-- Replace wallet_runtime with the deployment's role name, review, then execute the output as DBA.
-- Do not give this role/account database-wide/global DML or DDL privileges: MySQL grants are additive.
SELECT CONCAT(
    'GRANT SELECT, INSERT',
    CASE WHEN TABLE_NAME IN ('wallet_ops_audit','wallet_security_events','wallet_status_changes',
                             'wallet_journals','wallet_journal_entries','wallet_limit_reservations') THEN '' ELSE ', UPDATE, DELETE' END,
    ' ON `', REPLACE(TABLE_SCHEMA,'`','``'), '`.`', REPLACE(TABLE_NAME,'`','``'), '` TO ''wallet_runtime'';'
) AS runtime_grant
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME <> 'flyway_schema_history'
ORDER BY TABLE_NAME;
