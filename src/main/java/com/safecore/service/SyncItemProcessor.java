package com.safecore.service;

import com.safecore.dto.request.SyncItemRequest;
import com.safecore.entity.SyncIdempotencia;
import com.safecore.repository.SyncIdempotenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SyncItemProcessor {

    private final NaoConformidadeService ncService;
    private final DesvioService desvioService;
    private final SyncIdempotenciaRepository idempotenciaRepository;

    /**
     * Processa um item do batch de sync dentro de sua própria transação.
     * NÃO captura exceções: qualquer falha em {@code ncService.create}/
     * {@code desvioService.create} propaga para o chamador, para que o
     * rollback desta transação complete de forma limpa antes de o erro ser
     * convertido em {@code SyncItemResult} do lado de fora (em
     * {@link SyncService#processar}). Capturar aqui, dentro da fronteira
     * transacional, foi o bug do round 2: a exceção virava resultado "ERRO"
     * mas a transação já estava marcada rollback-only, e o commit implícito
     * ao sair do método lançava UnexpectedRollbackException, derrubando o
     * batch inteiro.
     */
    @Transactional
    public UUID processarItem(SyncItemRequest item) {
        var existente = idempotenciaRepository.findById(item.localId());
        if (existente.isPresent()) {
            return existente.get().getServerId();
        }

        validarCoerenciaTipoPayload(item);

        UUID serverId = switch (item.tipo()) {
            case "NC" -> ncService.create(item.nc()).id();
            case "DESVIO" -> desvioService.create(item.desvio()).id();
            default -> throw new IllegalArgumentException("tipo desconhecido: " + item.tipo());
        };

        idempotenciaRepository.save(SyncIdempotencia.builder()
                .localId(item.localId())
                .tipo(item.tipo())
                .serverId(serverId)
                .build());

        return serverId;
    }

    /**
     * Checagem explícita de coerência tipo↔payload. {@code @Valid} em um campo
     * {@code null} (nc/desvio) é um no-op do Bean Validation, então um payload
     * como {@code {"localId":"x","tipo":"NC","nc":null}} passa direto pela
     * validação do controller. Sem esta checagem, o switch abaixo cairia em
     * {@code ncService.create(null)}/{@code desvioService.create(null)},
     * gerando uma NullPointerException cujo {@code getMessage()} costuma ser
     * {@code null} — isso vira um {@code SyncItemResult(status="ERRO", erro=null)},
     * exibido vazio pro usuário.
     */
    private void validarCoerenciaTipoPayload(SyncItemRequest item) {
        if ("NC".equals(item.tipo()) && item.nc() == null) {
            throw new IllegalArgumentException(
                    "payload 'nc' ausente para item do tipo NC (localId=" + item.localId() + ")");
        }
        if ("DESVIO".equals(item.tipo()) && item.desvio() == null) {
            throw new IllegalArgumentException(
                    "payload 'desvio' ausente para item do tipo DESVIO (localId=" + item.localId() + ")");
        }
    }
}
