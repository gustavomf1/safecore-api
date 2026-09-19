package com.safecore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sync_idempotencia")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncIdempotencia {

    @Id
    @Column(name = "local_id", length = 64)
    private String localId;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(name = "server_id", nullable = false)
    private UUID serverId;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}
