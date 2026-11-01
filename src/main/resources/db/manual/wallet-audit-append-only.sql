-- Run with the migration account, after creating/upgrading the tables, in the selected schema.
-- Does not delete or rewrite audit data. Runtime account must not have ALTER/DROP/TRIGGER privileges.
CREATE TRIGGER wallet_ops_audit_no_update BEFORE UPDATE ON wallet_ops_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_ops_audit_no_delete BEFORE DELETE ON wallet_ops_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_security_events_no_update BEFORE UPDATE ON wallet_security_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_security_events_no_delete BEFORE DELETE ON wallet_security_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_status_changes_no_update BEFORE UPDATE ON wallet_status_changes FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_status_changes_no_delete BEFORE DELETE ON wallet_status_changes FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_journals_no_update BEFORE UPDATE ON wallet_journals FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_journals_no_delete BEFORE DELETE ON wallet_journals FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_journal_entries_no_update BEFORE UPDATE ON wallet_journal_entries FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_journal_entries_no_delete BEFORE DELETE ON wallet_journal_entries FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are append-only';
CREATE TRIGGER wallet_limit_reservations_no_update BEFORE UPDATE ON wallet_limit_reservations FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Quota reservations are append-only';
CREATE TRIGGER wallet_limit_reservations_no_delete BEFORE DELETE ON wallet_limit_reservations FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Quota reservations are append-only';
