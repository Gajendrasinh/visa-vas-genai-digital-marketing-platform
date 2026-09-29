package com.vasmarketing.customer.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MerchantJpaRepository extends JpaRepository<MerchantEntity, UUID> {}
