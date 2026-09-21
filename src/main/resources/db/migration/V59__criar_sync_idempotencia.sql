CREATE TABLE sync_idempotencia (
    local_id   VARCHAR(64) PRIMARY KEY,
    tipo       VARCHAR(10) NOT NULL,
    server_id  UUID NOT NULL,
    criado_em  TIMESTAMP NOT NULL DEFAULT now()
);
