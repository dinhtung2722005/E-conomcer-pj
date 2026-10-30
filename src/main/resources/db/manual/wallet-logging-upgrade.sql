-- One-time upgrade from the preceding wallet version. Back up and select the intended database first.
ALTER TABLE wallet_ops_audit ADD COLUMN wallet_id BIGINT NULL, ADD COLUMN request_id VARCHAR(80) NULL,
    ADD INDEX idx_ops_audit_wallet_time (wallet_id,created_at),
    ADD INDEX idx_ops_audit_actor_time (actor_id,created_at), ADD INDEX idx_ops_audit_request (request_id);
ALTER TABLE wallet_journals ADD COLUMN request_id VARCHAR(80) NULL;
CREATE TABLE wallet_security_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NULL, wallet_id BIGINT NULL,
    action ENUM('PIN_SETUP','PIN_CHANGE','PIN_VERIFY','PIN_LOCKED','PIN_RESET_REQUEST','PIN_RESET',
                'RECOVERY_EMAIL_REQUEST','RECOVERY_EMAIL_CHANGE','RECOVERY_DELIVERY',
                'AUTHENTICATION_FAILED','ACCESS_DENIED') NOT NULL,
    outcome ENUM('SUCCESS','REJECTED') NOT NULL,
    code VARCHAR(80) NOT NULL, reference VARCHAR(100) NULL,
    request_id VARCHAR(80) NULL, created_at DATETIME(6) NOT NULL,
    INDEX idx_security_actor_time (actor_id,created_at),
    INDEX idx_security_wallet_time (wallet_id,created_at), INDEX idx_security_request (request_id)
);
