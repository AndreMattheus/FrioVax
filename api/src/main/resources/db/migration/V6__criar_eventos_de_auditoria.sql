-- Trilha de auditoria (US10, S2-18). A API só insere e consulta: o papel runtime recebe SELECT e INSERT
-- (docs/scripts/runtime-grants.sql). Sem chave estrangeira para usuarios: o evento copia login e perfil e
-- continua legível mesmo se o usuário mudar depois.
CREATE TABLE eventos_auditoria (
    id BIGSERIAL PRIMARY KEY,
    instante TIMESTAMPTZ NOT NULL,
    ator_id UUID,
    ator_login VARCHAR(100) NOT NULL CONSTRAINT chk_auditoria_ator_login CHECK (length(btrim(ator_login)) > 0),
    ator_perfil VARCHAR(20) NOT NULL
        CONSTRAINT chk_auditoria_ator_perfil CHECK (ator_perfil IN ('ADMINISTRADOR', 'OPERADOR', 'AUDITOR', 'SISTEMA')),
    recurso VARCHAR(30) NOT NULL,
    recurso_id BIGINT NOT NULL CONSTRAINT chk_auditoria_recurso_id CHECK (recurso_id > 0),
    operacao VARCHAR(40) NOT NULL,
    descricao VARCHAR(500) NOT NULL CONSTRAINT chk_auditoria_descricao CHECK (length(btrim(descricao)) > 0),
    correlacao_id VARCHAR(128) NOT NULL
        CONSTRAINT chk_auditoria_correlacao CHECK (correlacao_id ~ '^[A-Za-z0-9._:-]{1,128}$'),
    -- Só o ator SISTEMA não tem id; todo usuário tem.
    CONSTRAINT chk_auditoria_ator_sistema CHECK ((ator_perfil = 'SISTEMA') = (ator_id IS NULL))
);

CREATE INDEX idx_auditoria_instante ON eventos_auditoria (instante);
CREATE INDEX idx_auditoria_ator ON eventos_auditoria (ator_id, id);
CREATE INDEX idx_auditoria_recurso ON eventos_auditoria (recurso, recurso_id, id);
