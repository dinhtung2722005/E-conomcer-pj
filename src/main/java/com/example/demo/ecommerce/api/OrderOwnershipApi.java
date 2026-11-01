package com.example.demo.ecommerce.api;
import java.util.Optional;
/** Read-only identifier lookup used by validation; never returns a persistent entity. */
public interface OrderOwnershipApi { Optional<Long> ownerId(Long orderId); }
