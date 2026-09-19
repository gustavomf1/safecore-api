package com.safecore.repository;

import com.safecore.entity.SyncIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncIdempotenciaRepository extends JpaRepository<SyncIdempotencia, String> {
}
