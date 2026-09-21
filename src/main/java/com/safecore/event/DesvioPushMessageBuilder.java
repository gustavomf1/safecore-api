package com.safecore.event;

import com.safecore.entity.Desvio;
import com.safecore.entity.StatusDesvio;
import com.safecore.event.kafka.DesvioKafkaEvent;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.safecore.entity.StatusDesvio.*;

@Component
public class DesvioPushMessageBuilder {

    public DesvioKafkaEvent resolver(Desvio desvio, StatusDesvio statusAnterior, StatusDesvio statusNovo) {
        UUID criadorId = desvio.getUsuarioCriacao() != null ? desvio.getUsuarioCriacao().getId() : null;
        UUID responsavelDesvioId = desvio.getResponsavelDesvio() != null ? desvio.getResponsavelDesvio().getId() : null;
        UUID responsavelTratativaId = desvio.getResponsavelTratativa() != null ? desvio.getResponsavelTratativa().getId() : null;

        String tipo;
        Set<UUID> destinatarios = new LinkedHashSet<>();

        if (statusAnterior == ABERTO && statusNovo == AGUARDANDO_TRATATIVA) {
            tipo = "DESVIO_ATIVADO";
            addIfPresent(destinatarios, responsavelTratativaId);
            addIfPresent(destinatarios, responsavelDesvioId);
        } else if (statusAnterior == AGUARDANDO_TRATATIVA && statusNovo == AGUARDANDO_APROVACAO) {
            tipo = "DESVIO_TRATATIVA_SUBMETIDA";
            addIfPresent(destinatarios, responsavelDesvioId);
            addIfPresent(destinatarios, criadorId);
        } else if (statusAnterior == AGUARDANDO_APROVACAO && statusNovo == CONCLUIDO) {
            tipo = "DESVIO_APROVADO";
            addIfPresent(destinatarios, responsavelTratativaId);
            addIfPresent(destinatarios, criadorId);
        } else if (statusAnterior == AGUARDANDO_APROVACAO && statusNovo == AGUARDANDO_TRATATIVA) {
            tipo = "DESVIO_REPROVADO";
            addIfPresent(destinatarios, responsavelTratativaId);
            addIfPresent(destinatarios, criadorId);
        } else {
            return null;
        }

        if (destinatarios.isEmpty()) return null;

        return new DesvioKafkaEvent(
                UUID.randomUUID(),
                tipo,
                desvio.getId(),
                List.copyOf(destinatarios),
                montarTitulo(desvio),
                montarCorpo(desvio, tipo)
        );
    }

    private String montarTitulo(Desvio desvio) {
        String codigo = formatCodigo(desvio.getNumeroSequencial());
        return codigo == null ? desvio.getTitulo() : codigo + " - " + desvio.getTitulo();
    }

    private String formatCodigo(Long numeroSequencial) {
        return numeroSequencial == null ? null : "DESV-" + String.format("%04d", numeroSequencial);
    }

    private String montarCorpo(Desvio desvio, String tipo) {
        String titulo = desvio.getTitulo();
        return switch (tipo) {
            case "DESVIO_ATIVADO" -> "\"" + titulo + "\" está aguardando sua tratativa.";
            case "DESVIO_TRATATIVA_SUBMETIDA" -> "Tratativa do Desvio \"" + titulo + "\" submetida para aprovação.";
            case "DESVIO_APROVADO" -> "Desvio \"" + titulo + "\" foi aprovado e concluído.";
            case "DESVIO_REPROVADO" -> "Tratativa do Desvio \"" + titulo + "\" foi reprovada.";
            default -> titulo;
        };
    }

    private void addIfPresent(Set<UUID> set, UUID id) {
        if (id != null) set.add(id);
    }
}
