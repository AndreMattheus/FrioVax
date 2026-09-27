DROP INDEX idx_camaras_unidade;

CREATE INDEX idx_camaras_unidade_ci ON camaras (lower(unidade));
CREATE INDEX idx_camaras_estado ON camaras (estado);
