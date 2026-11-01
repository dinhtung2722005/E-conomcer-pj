-- Fresh schema only: run AFTER Hibernate creates tables (or include in the real schema migration).
-- Existing schemas already have these foreign keys from the previous JPA associations: retain them.
-- Runtime validation must not remove these constraints. Java modules reference other modules by ID.
ALTER TABLE wallets ADD CONSTRAINT fk_wallet_account FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE wallet_status_changes ADD CONSTRAINT fk_wallet_status_actor FOREIGN KEY (actor_user_id) REFERENCES users(id);
ALTER TABLE wallet_recovery_debts ADD CONSTRAINT fk_wallet_debt_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE wallet_holds ADD CONSTRAINT fk_wallet_hold_order FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE wallet_refund_cases ADD CONSTRAINT fk_wallet_refund_order FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE wallet_refund_lines ADD CONSTRAINT fk_wallet_refund_item FOREIGN KEY (order_item_id) REFERENCES order_items(id);
ALTER TABLE products ADD CONSTRAINT fk_product_seller FOREIGN KEY (seller_id) REFERENCES users(id);
ALTER TABLE order_items ADD CONSTRAINT fk_order_item_seller FOREIGN KEY (seller_id) REFERENCES users(id);
