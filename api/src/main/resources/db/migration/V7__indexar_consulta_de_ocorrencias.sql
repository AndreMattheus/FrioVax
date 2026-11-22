-- Histórico global, inclusive quando não há filtro por câmara.
CREATE INDEX idx_ocorrencias_abertura ON ocorrencias (aberta_em DESC, id DESC);
CREATE INDEX idx_ocorrencias_estado_abertura ON ocorrencias ((resolvida_em IS NULL), aberta_em DESC, id DESC);
