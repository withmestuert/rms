package com.rms.backend.billing.repository;

import com.rms.backend.billing.entity.LedgerTransaction;
import com.rms.backend.billing.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    List<LedgerTransaction> findAllByOrderByCreatedAtDescIdDesc();

    List<LedgerTransaction> findByTypeOrderByCreatedAtDescIdDesc(TransactionType type);

    List<LedgerTransaction> findByPropertyIdOrderByCreatedAtDescIdDesc(Long propertyId);

    Optional<LedgerTransaction> findTopByOrderByCreatedAtDescIdDesc();

    Optional<LedgerTransaction> findByReferenceNumber(String referenceNumber);
}
