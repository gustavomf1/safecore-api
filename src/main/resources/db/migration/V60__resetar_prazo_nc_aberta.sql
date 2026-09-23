UPDATE nao_conformidade
SET data_limite_resolucao = NULL,
    vencida = 'N'
WHERE status = 'ABERTA';
