-- Initial schema for an EMPTY database. Existing verified installations baseline at version 1.
SET @wallet_previous_fk_checks=@@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS=0;
CREATE TABLE `addresses` (
  `is_default` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `phone` varchar(20) NOT NULL,
  `city` varchar(100) NOT NULL,
  `district` varchar(100) NOT NULL,
  `recipient_name` varchar(100) NOT NULL,
  `ward` varchar(100) NOT NULL,
  `street` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1fa36y2oqhao3wgg2rw1pi459` (`user_id`),
  CONSTRAINT `FK1fa36y2oqhao3wgg2rw1pi459` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cart_items` (
  `quantity` int NOT NULL,
  `cart_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpcttvuq4mxppo8sxggjtn5i2c` (`cart_id`),
  KEY `FK1re40cjegsfvw58xrkdp6bac6` (`product_id`),
  CONSTRAINT `FK1re40cjegsfvw58xrkdp6bac6` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKpcttvuq4mxppo8sxggjtn5i2c` FOREIGN KEY (`cart_id`) REFERENCES `carts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `carts` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK64t7ox312pqal3p7fg9o503c2` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `categories` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `description` text,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt8o6pivur7nn124jehx7cygw5` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flash_sale_items` (
  `promotional_price` decimal(19,4) NOT NULL,
  `sold_quantity` int NOT NULL,
  `total_quantity` int NOT NULL,
  `flash_sale_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmr5agn0eu29xqqog30yspa3e3` (`flash_sale_id`),
  KEY `FK5p9e16gsvvhlc28cjyvju7m99` (`product_id`),
  CONSTRAINT `FK5p9e16gsvvhlc28cjyvju7m99` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKmr5agn0eu29xqqog30yspa3e3` FOREIGN KEY (`flash_sale_id`) REFERENCES `flash_sales` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flash_sales` (
  `created_at` datetime(6) NOT NULL,
  `end_time` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `start_time` datetime(6) NOT NULL,
  `name` varchar(150) NOT NULL,
  `status` enum('ENDED','ONGOING','UPCOMING') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invalidated_tokens` (
  `expiry_time` datetime(6) DEFAULT NULL,
  `id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventories` (
  `available_quantity` int NOT NULL,
  `locked_quantity` int NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3bms153u88bt0daekfy4qpj6k` (`product_id`),
  CONSTRAINT `FK8drmqyx629j3oo8ct9jnc5y3y` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_items` (
  `platform_fee` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `price_at_purchase` decimal(19,4) NOT NULL,
  `quantity` int NOT NULL,
  `settlement_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `seller_id` bigint DEFAULT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbioxgbv59vetrxe0ejfubep1w` (`order_id`),
  KEY `FKocimc7dtr037rh4ls4l95nlfi` (`product_id`),
  KEY `fk_order_item_seller` (`seller_id`),
  CONSTRAINT `fk_order_item_seller` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKbioxgbv59vetrxe0ejfubep1w` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `FKocimc7dtr037rh4ls4l95nlfi` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `orders` (
  `cashback_percent` decimal(7,4) NOT NULL DEFAULT '0.0000',
  `discount_amount` decimal(19,4) DEFAULT NULL,
  `fee_percent` decimal(7,4) NOT NULL DEFAULT '0.0000',
  `final_amount` decimal(19,4) NOT NULL,
  `refunded_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `settled_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `settlement_attempts` int NOT NULL DEFAULT '0',
  `total_amount` decimal(19,4) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `return_window_seconds` bigint NOT NULL DEFAULT '604800',
  `settled_at` datetime(6) DEFAULT NULL,
  `settlement_available_at` datetime(6) DEFAULT NULL,
  `settlement_next_attempt_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `voucher_id` bigint DEFAULT NULL,
  `settlement_last_error` varchar(80) DEFAULT NULL,
  `receiver_name` varchar(255) NOT NULL,
  `receiver_phone` varchar(255) NOT NULL,
  `shipping_address` varchar(255) NOT NULL,
  `status` enum('CANCELLED','COMPLETED','PAID','PENDING','REFUNDED','SHIPPED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_orders_status_created_at` (`status`,`created_at`),
  KEY `idx_orders_user_id` (`user_id`),
  KEY `FKdimvsocblb17f45ikjr6xn1wj` (`voucher_id`),
  CONSTRAINT `FKdimvsocblb17f45ikjr6xn1wj` FOREIGN KEY (`voucher_id`) REFERENCES `vouchers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `otp_codes` (
  `is_used` bit(1) NOT NULL,
  `code` varchar(6) NOT NULL,
  `expiration_time` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_categories` (
  `category_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`category_id`,`product_id`),
  KEY `FKlda9rad6s180ha3dl1ncsp8n7` (`product_id`),
  CONSTRAINT `FKd112rx0alycddsms029iifrih` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`),
  CONSTRAINT `FKlda9rad6s180ha3dl1ncsp8n7` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `products` (
  `price` decimal(19,4) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` text,
  `name` varchar(255) NOT NULL,
  `status` enum('ACTIVE','INACTIVE','OUT_OF_STOCK') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_product_seller` (`seller_id`),
  CONSTRAINT `fk_product_seller` FOREIGN KEY (`seller_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `refresh_tokens` (
  `expiry_date` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `token` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKghpmfn23vmxfu3spu3lfg4r2d` (`token`),
  UNIQUE KEY `UK7tdcd6ab5wsgoudnvj7xf1b7l` (`user_id`),
  CONSTRAINT `FK1lih5y2npsf8u5o3vhdb9y0os` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_vouchers` (
  `is_used` bit(1) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `saved_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `voucher_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK40ig7khk2v79rbqaj98mf1g2q` (`voucher_id`),
  CONSTRAINT `FK40ig7khk2v79rbqaj98mf1g2q` FOREIGN KEY (`voucher_id`) REFERENCES `vouchers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `is_active` bit(1) NOT NULL,
  `is_deleted` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token_version` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  `role` enum('ADMIN','CUSTOMER','SELLER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `vouchers` (
  `discount_value` decimal(19,4) NOT NULL,
  `max_discount_amount` decimal(19,4) DEFAULT NULL,
  `min_order_value` decimal(19,4) NOT NULL,
  `total_quantity` int NOT NULL,
  `used_quantity` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_date` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `start_date` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  `code` varchar(50) NOT NULL,
  `discount_type` enum('FIXED_AMOUNT','PERCENTAGE') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK30ftp2biebbvpik8e49wlmady` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_alerts` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `version` bigint NOT NULL,
  `category` varchar(50) NOT NULL,
  `reference` varchar(100) NOT NULL,
  `alert_key` varchar(150) NOT NULL,
  `review_reason` varchar(500) DEFAULT NULL,
  `message` varchar(255) NOT NULL,
  `status` enum('ACKNOWLEDGED','OPEN','RESOLVED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9hqs3vgs9bviukopixtsf5e6r` (`alert_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_bank_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `revoked_at` datetime(6) DEFAULT NULL,
  `wallet_id` bigint NOT NULL,
  `bank_code` varchar(50) NOT NULL,
  `masked_account_number` varchar(50) NOT NULL,
  `provider` varchar(50) NOT NULL,
  `account_holder_name` varchar(150) NOT NULL,
  `account_token` varchar(150) NOT NULL,
  `bank_name` varchar(150) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_bank_token` (`provider`,`account_token`),
  KEY `FKqyy5xsp0oxwgaag5524345ik8` (`wallet_id`),
  CONSTRAINT `FKqyy5xsp0oxwgaag5524345ik8` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_bank_transfers` (
  `attempts` int NOT NULL,
  `finished` bit(1) NOT NULL,
  `suspended` bit(1) NOT NULL DEFAULT b'0',
  `bank_account_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `dispatch_started_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lease_until` datetime(6) DEFAULT NULL,
  `next_attempt_at` datetime(6) NOT NULL,
  `transaction_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  `lease_token` varchar(36) DEFAULT NULL,
  `last_error` varchar(50) DEFAULT NULL,
  `merchant_reference` varchar(100) NOT NULL,
  `instructions_json` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7veq4h22xh9o3m929jdi2ctqn` (`transaction_id`),
  UNIQUE KEY `UKb5kodwprbi716eio697s5ojx7` (`merchant_reference`),
  KEY `idx_bank_transfer_due` (`finished`,`next_attempt_at`,`id`),
  KEY `FKg709e68jw16vy5ofdnuxx7ve4` (`bank_account_id`),
  CONSTRAINT `FKg709e68jw16vy5ofdnuxx7ve4` FOREIGN KEY (`bank_account_id`) REFERENCES `wallet_bank_accounts` (`id`),
  CONSTRAINT `FKq0v7h55n5n84aksvc90a2atm1` FOREIGN KEY (`transaction_id`) REFERENCES `wallet_transactions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_holds` (
  `amount` decimal(19,4) NOT NULL,
  `captured_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `released_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  `wallet_id` bigint NOT NULL,
  `reference_id` varchar(100) NOT NULL,
  `balance_type` enum('BONUS','CASH') NOT NULL,
  `hold_type` enum('ORDER_PAYMENT','WITHDRAWAL') NOT NULL,
  `status` enum('ACTIVE','CAPTURED','CLOSED','PARTIALLY_PROCESSED','RELEASED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_hold_reference` (`wallet_id`,`hold_type`,`reference_id`,`balance_type`),
  UNIQUE KEY `uk_wallet_hold_order` (`wallet_id`,`order_id`,`balance_type`),
  KEY `idx_wallet_hold_status` (`wallet_id`,`status`),
  KEY `fk_wallet_hold_order` (`order_id`),
  CONSTRAINT `FK5rberb7jrra19mks5xau4964l` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`),
  CONSTRAINT `fk_wallet_hold_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `ck_wallet_hold_amounts` CHECK (((`amount` > 0) and (`captured_amount` >= 0) and (`released_amount` >= 0) and ((`captured_amount` + `released_amount`) <= `amount`))),
  CONSTRAINT `ck_wallet_hold_order` CHECK ((((`hold_type` = _utf8mb4'ORDER_PAYMENT') and (`order_id` is not null)) or ((`hold_type` = _utf8mb4'WITHDRAWAL') and (`order_id` is null)))),
  CONSTRAINT `ck_wallet_hold_status` CHECK ((((`status` = _utf8mb4'ACTIVE') and (`captured_amount` = 0) and (`released_amount` = 0)) or ((`status` = _utf8mb4'PARTIALLY_PROCESSED') and ((`captured_amount` + `released_amount`) > 0) and ((`captured_amount` + `released_amount`) < `amount`)) or ((`status` = _utf8mb4'CAPTURED') and (`captured_amount` = `amount`) and (`released_amount` = 0)) or ((`status` = _utf8mb4'RELEASED') and (`released_amount` = `amount`) and (`captured_amount` = 0)) or ((`status` = _utf8mb4'CLOSED') and (`captured_amount` > 0) and (`released_amount` > 0) and ((`captured_amount` + `released_amount`) = `amount`)))),
  CONSTRAINT `ck_wallet_hold_withdraw_cash` CHECK (((`hold_type` <> _utf8mb4'WITHDRAWAL') or (`balance_type` = _utf8mb4'CASH')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_job_cursors` (
  `last_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_journal_entries` (
  `amount` decimal(19,4) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `journal_id` bigint NOT NULL,
  `account` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_journal_account` (`account`,`id`),
  KEY `FKoryxj8b81sp7225pwwonku1ns` (`journal_id`),
  CONSTRAINT `FKoryxj8b81sp7225pwwonku1ns` FOREIGN KEY (`journal_id`) REFERENCES `wallet_journals` (`id`),
  CONSTRAINT `ck_journal_nonzero` CHECK ((`amount` <> 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_journals` (
  `currency` varchar(3) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(80) DEFAULT NULL,
  `event_key` varchar(150) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2sn81wpliqvmfo58s968h68gn` (`event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_limit_reservations` (
  `amount` decimal(19,4) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operation_id` bigint NOT NULL,
  `reserved_at` datetime(6) NOT NULL,
  `wallet_id` bigint NOT NULL,
  `type` enum('CASHBACK','CASHBACK_REVERSAL','DEBT_REPAYMENT','DEPOSIT','PAYMENT','REFUND','RESERVE_FUNDING','SETTLEMENT','WITHDRAW') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKd5el9kipgk6y07myv5pv4upo4` (`operation_id`),
  KEY `idx_wallet_limit_window` (`wallet_id`,`type`,`reserved_at`),
  CONSTRAINT `FK6qpyb3wdtlhpgm4gwi13x87fm` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`),
  CONSTRAINT `FKrj0j1lddp20uwq7wap7gcci6m` FOREIGN KEY (`operation_id`) REFERENCES `wallet_operations` (`id`),
  CONSTRAINT `wallet_limit_reservations_chk_1` CHECK ((`amount` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_notifications` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `read_at` datetime(6) DEFAULT NULL,
  `transaction_id` bigint DEFAULT NULL,
  `version` bigint NOT NULL,
  `wallet_id` bigint NOT NULL,
  `title` varchar(100) NOT NULL,
  `event_key` varchar(150) NOT NULL,
  `message` varchar(500) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK85cic2fyrvq37q9uwjt71m8cu` (`event_key`),
  KEY `idx_wallet_notification` (`wallet_id`,`id`),
  CONSTRAINT `FKd423tviqlpw61ylbiedadb7e1` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_operations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wallet_id` bigint NOT NULL,
  `fingerprint` varchar(64) NOT NULL,
  `request_key` varchar(80) NOT NULL,
  `reference` varchar(100) NOT NULL,
  `type` enum('CASHBACK','CASHBACK_REVERSAL','DEBT_REPAYMENT','DEPOSIT','PAYMENT','REFUND','RESERVE_FUNDING','SETTLEMENT','WITHDRAW') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_operation_key` (`wallet_id`,`request_key`),
  CONSTRAINT `FK828h80ckbjn4hkte32ml5moey` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_ops_audit` (
  `actor_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wallet_id` bigint DEFAULT NULL,
  `action` varchar(50) NOT NULL,
  `request_id` varchar(80) DEFAULT NULL,
  `reference` varchar(100) NOT NULL,
  `reason` varchar(500) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ops_audit_wallet_time` (`wallet_id`,`created_at`),
  KEY `idx_ops_audit_actor_time` (`actor_id`,`created_at`),
  KEY `idx_ops_audit_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_pin_recoveries` (
  `failed_attempts` int NOT NULL,
  `consumed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `security_version` bigint NOT NULL,
  `token_version` bigint NOT NULL,
  `version` bigint NOT NULL,
  `wallet_id` bigint NOT NULL,
  `id` varchar(36) NOT NULL,
  `email` varchar(100) NOT NULL,
  `code_hash` varchar(255) NOT NULL,
  `purpose` enum('RECOVERY_EMAIL','RESET_PIN') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pin_recovery_rate` (`wallet_id`,`created_at`),
  CONSTRAINT `FK2ljafbnty6b4m4pxt0nqb0v9x` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_recovery_debts` (
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `repaid_amount` decimal(19,4) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `refund_case_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  `balance_type` enum('BONUS','CASH') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpab9q0arqp2orxf15pkk2eq7n` (`refund_case_id`),
  KEY `fk_wallet_debt_user` (`user_id`),
  CONSTRAINT `fk_wallet_debt_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKpab9q0arqp2orxf15pkk2eq7n` FOREIGN KEY (`refund_case_id`) REFERENCES `wallet_refund_cases` (`id`),
  CONSTRAINT `wallet_recovery_debts_chk_1` CHECK (((`amount` > 0) and (`repaid_amount` >= 0) and (`repaid_amount` <= `amount`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_refund_cases` (
  `amount` decimal(19,4) NOT NULL,
  `bonus_recovery_amount` decimal(19,4) NOT NULL,
  `fee_amount` decimal(19,4) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `version` bigint NOT NULL,
  `fingerprint` varchar(64) NOT NULL,
  `request_key` varchar(80) NOT NULL,
  `decision_reason` varchar(500) DEFAULT NULL,
  `reason` varchar(500) NOT NULL,
  `status` enum('APPROVED','REJECTED','REQUESTED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_request` (`order_id`,`request_key`),
  CONSTRAINT `fk_wallet_refund_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `wallet_refund_cases_chk_1` CHECK (((`amount` >= 0) and (`fee_amount` >= 0) and (`fee_amount` <= `amount`) and (`bonus_recovery_amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_refund_lines` (
  `amount` decimal(19,4) NOT NULL,
  `fee_amount` decimal(19,4) NOT NULL,
  `quantity` int NOT NULL,
  `case_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_item_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_line` (`case_id`,`order_item_id`),
  KEY `fk_wallet_refund_item` (`order_item_id`),
  CONSTRAINT `FK8lghl7xb6nwosrmeimepib9mq` FOREIGN KEY (`case_id`) REFERENCES `wallet_refund_cases` (`id`),
  CONSTRAINT `fk_wallet_refund_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`),
  CONSTRAINT `wallet_refund_lines_chk_1` CHECK (((`quantity` > 0) and (`amount` >= 0) and (`fee_amount` >= 0) and (`fee_amount` <= `amount`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_refund_reserves` (
  `balance` decimal(19,4) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`currency`),
  CONSTRAINT `wallet_refund_reserves_chk_1` CHECK ((`balance` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_security` (
  `failed_pin_attempts` int NOT NULL DEFAULT '0',
  `pin_changed_at` datetime(6) DEFAULT NULL,
  `pin_locked_until` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  `wallet_id` bigint NOT NULL,
  `withdrawal_blocked_until` datetime(6) DEFAULT NULL,
  `recovery_email` varchar(100) DEFAULT NULL,
  `pin_hash` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`wallet_id`),
  CONSTRAINT `FKh5ne2l15mdf75nwrcajpdemvd` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`),
  CONSTRAINT `ck_wallet_security_attempts` CHECK ((`failed_pin_attempts` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_security_events` (
  `actor_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wallet_id` bigint DEFAULT NULL,
  `code` varchar(80) NOT NULL,
  `request_id` varchar(80) DEFAULT NULL,
  `reference` varchar(100) DEFAULT NULL,
  `action` enum('ACCESS_DENIED','AUTHENTICATION_FAILED','PIN_CHANGE','PIN_LOCKED','PIN_RESET','PIN_RESET_REQUEST','PIN_SETUP','PIN_VERIFY','RECOVERY_DELIVERY','RECOVERY_EMAIL_CHANGE','RECOVERY_EMAIL_REQUEST') NOT NULL,
  `outcome` enum('REJECTED','SUCCESS') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_security_actor_time` (`actor_id`,`created_at`),
  KEY `idx_security_wallet_time` (`wallet_id`,`created_at`),
  KEY `idx_security_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_status_changes` (
  `actor_user_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wallet_id` bigint NOT NULL,
  `reason` varchar(500) NOT NULL,
  `actor_role` enum('ADMIN','CUSTOMER','SELLER') NOT NULL,
  `new_status` enum('ACTIVE','LOCKED') NOT NULL,
  `previous_status` enum('ACTIVE','LOCKED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_wallet_status_change_history` (`wallet_id`,`created_at`),
  KEY `fk_wallet_status_actor` (`actor_user_id`),
  CONSTRAINT `FK91x0j59yhcj3d9eh211fpx7gi` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`),
  CONSTRAINT `fk_wallet_status_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_wallet_status_change_transition` CHECK ((`previous_status` <> `new_status`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallet_transactions` (
  `amount` decimal(19,4) NOT NULL,
  `notification_published` bit(1) NOT NULL DEFAULT b'0',
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `hold_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operation_id` bigint DEFAULT NULL,
  `original_transaction_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  `wallet_id` bigint NOT NULL,
  `provider` varchar(50) DEFAULT NULL,
  `idempotency_key` varchar(100) NOT NULL,
  `reference_id` varchar(100) NOT NULL,
  `destination_account_reference` varchar(150) DEFAULT NULL,
  `provider_transaction_id` varchar(150) DEFAULT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `balance_type` enum('BONUS','CASH') NOT NULL DEFAULT 'CASH',
  `status` enum('CANCELLED','FAILED','PENDING','PROCESSING','SUCCESS') NOT NULL DEFAULT 'PENDING',
  `transaction_type` enum('CASHBACK','CASHBACK_REVERSAL','DEBT_REPAYMENT','DEPOSIT','PAYMENT','REFUND','RESERVE_FUNDING','SETTLEMENT','WITHDRAW') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_transaction_idempotency` (`wallet_id`,`idempotency_key`),
  UNIQUE KEY `uk_wallet_transaction_provider` (`provider`,`provider_transaction_id`),
  KEY `idx_wallet_transaction_history` (`wallet_id`,`created_at`),
  KEY `idx_wallet_transaction_reference` (`reference_id`,`transaction_type`),
  KEY `idx_wallet_transaction_pending` (`status`,`created_at`),
  KEY `FK8fiaxfp2521nn4188743c73lp` (`hold_id`),
  KEY `FK64tsmdvhf46yjnjgh4a376oor` (`operation_id`),
  KEY `FKs9bshs5qxm9qexcxdysst3llr` (`original_transaction_id`),
  CONSTRAINT `FK64tsmdvhf46yjnjgh4a376oor` FOREIGN KEY (`operation_id`) REFERENCES `wallet_operations` (`id`),
  CONSTRAINT `FK8fiaxfp2521nn4188743c73lp` FOREIGN KEY (`hold_id`) REFERENCES `wallet_holds` (`id`),
  CONSTRAINT `FK8seu7b87ifqi09ghhssusmb0x` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`),
  CONSTRAINT `FKs9bshs5qxm9qexcxdysst3llr` FOREIGN KEY (`original_transaction_id`) REFERENCES `wallet_transactions` (`id`),
  CONSTRAINT `ck_wallet_transaction_amount` CHECK ((`amount` > 0)),
  CONSTRAINT `ck_wallet_transaction_bonus_only` CHECK (((`transaction_type` not in (_utf8mb4'CASHBACK',_utf8mb4'CASHBACK_REVERSAL')) or (`balance_type` = _utf8mb4'BONUS'))),
  CONSTRAINT `ck_wallet_transaction_cash_only` CHECK (((`transaction_type` not in (_utf8mb4'DEPOSIT',_utf8mb4'WITHDRAW',_utf8mb4'SETTLEMENT')) or (`balance_type` = _utf8mb4'CASH'))),
  CONSTRAINT `ck_wallet_transaction_destination` CHECK (((`transaction_type` <> _utf8mb4'WITHDRAW') or (`destination_account_reference` is not null))),
  CONSTRAINT `ck_wallet_transaction_hold` CHECK (((`transaction_type` not in (_utf8mb4'PAYMENT',_utf8mb4'WITHDRAW')) or (`hold_id` is not null))),
  CONSTRAINT `ck_wallet_transaction_provider_pair` CHECK ((((`provider` is null) and (`provider_transaction_id` is null)) or ((`provider` is not null) and (`provider_transaction_id` is not null)))),
  CONSTRAINT `ck_wallet_transaction_refund` CHECK (((`transaction_type` not in (_utf8mb4'REFUND',_utf8mb4'CASHBACK_REVERSAL')) or (`original_transaction_id` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wallets` (
  `balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `bonus_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `currency` varchar(3) NOT NULL DEFAULT 'VND',
  `ledger_opened` bit(1) NOT NULL DEFAULT b'0',
  `pending_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `pending_bonus_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  `status` enum('ACTIVE','LOCKED') NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsswfdl9fq40xlkove1y5kc7kv` (`user_id`),
  CONSTRAINT `fk_wallet_account` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_wallet_nonnegative_balances` CHECK (((`balance` >= 0) and (`pending_balance` >= 0) and (`bonus_balance` >= 0) and (`pending_bonus_balance` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS=@wallet_previous_fk_checks;
