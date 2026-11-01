-- Extend an existing verified version-1 schema; fresh installs already have this table.
-- Existing LocalDateTime records must be converted from the deployment's OLD JVM timezone.
CREATE TABLE IF NOT EXISTS `wallet_limit_reservations` (
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

INSERT INTO wallet_limit_reservations (wallet_id,operation_id,type,amount,reserved_at)
SELECT o.wallet_id,o.id,o.type,COALESCE(SUM(t.amount),0),
       COALESCE(CONVERT_TZ(MIN(t.created_at),'${legacyWalletTimeZone}','+00:00'),UTC_TIMESTAMP(6))
FROM wallet_operations o
LEFT JOIN wallet_transactions t ON t.operation_id=o.id
WHERE o.type IN ('DEPOSIT','WITHDRAW','PAYMENT')
  AND NOT EXISTS (SELECT 1 FROM wallet_limit_reservations r WHERE r.operation_id=o.id)
GROUP BY o.wallet_id,o.id,o.type;
