CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    login VARCHAR(100) NOT NULL CONSTRAINT uk_usuarios_login UNIQUE,
    senha_hash VARCHAR(60) NOT NULL CONSTRAINT chk_usuarios_senha_hash CHECK (senha_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'),
    perfil VARCHAR(20) NOT NULL CONSTRAINT chk_usuarios_perfil CHECK (perfil IN ('ADMINISTRADOR', 'OPERADOR', 'AUDITOR'))
);

CREATE TABLE sessoes (
    token_hash VARCHAR(64) PRIMARY KEY CONSTRAINT chk_sessoes_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    usuario_id UUID NOT NULL CONSTRAINT fk_sessoes_usuario REFERENCES usuarios(id),
    criada_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL CONSTRAINT chk_sessoes_expiracao CHECK (expira_em > criada_em),
    revogada_em TIMESTAMPTZ,
    CONSTRAINT chk_sessoes_revogacao CHECK (revogada_em IS NULL OR revogada_em >= criada_em)
);

CREATE INDEX idx_sessoes_usuario ON sessoes(usuario_id);
CREATE INDEX idx_sessoes_expiracao ON sessoes(expira_em);
