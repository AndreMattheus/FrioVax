CREATE TABLE camaras (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nome VARCHAR(100) NOT NULL,
    unidade VARCHAR(100) NOT NULL,
    capacidade INT NOT NULL CHECK (capacidade > 0),
    temperatura_minima NUMERIC(4,1) NOT NULL,
    temperatura_maxima NUMERIC(4,1) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_faixa_termica CHECK (temperatura_minima < temperatura_maxima)
);

CREATE INDEX idx_camaras_unidade ON camaras (unidade);
CREATE INDEX idx_camaras_ativo ON camaras (ativo);