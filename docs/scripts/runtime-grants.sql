-- Executado pelo papel de migracao apos Flyway, nunca pela API.
BEGIN;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'friovax_runtime') THEN
        RAISE EXCEPTION 'Papel runtime ausente';
END IF;
    IF EXISTS (
        SELECT 1 FROM pg_roles
        WHERE rolname = 'friovax_runtime'
          AND (NOT rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole
               OR rolreplication OR rolbypassrls)
    ) OR pg_has_role('friovax_runtime', 'neon_superuser', 'MEMBER') THEN
        RAISE EXCEPTION 'Papel runtime com privilegios incompatíveis';
END IF;
EXECUTE format('GRANT CONNECT ON DATABASE %I TO friovax_runtime', current_database());
END $$;
GRANT USAGE ON SCHEMA public TO friovax_runtime;
GRANT SELECT, INSERT, UPDATE ON public.camaras, public.lotes, public.sessoes TO friovax_runtime;
GRANT SELECT, INSERT ON public.usuarios TO friovax_runtime;
GRANT SELECT, INSERT ON public.politicas_termicas TO friovax_runtime;
-- V5: leituras, evidências e lotes afetados só são inseridos; a ocorrência recebe UPDATE na resolução.
GRANT SELECT, INSERT ON public.leituras, public.ocorrencia_evidencias, public.ocorrencia_lotes TO friovax_runtime;
GRANT SELECT, INSERT, UPDATE ON public.ocorrencias TO friovax_runtime;
GRANT USAGE, SELECT ON SEQUENCE public.camaras_id_seq, public.lotes_id_seq,
  public.politicas_termicas_id_seq, public.leituras_id_seq, public.ocorrencias_id_seq TO friovax_runtime;
DO $$
BEGIN
    IF has_schema_privilege('friovax_runtime', 'public', 'CREATE')
       OR has_table_privilege('friovax_runtime', 'public.flyway_schema_history', 'INSERT')
       OR has_table_privilege('friovax_runtime', 'public.flyway_schema_history', 'UPDATE')
       OR has_table_privilege('friovax_runtime', 'public.flyway_schema_history', 'DELETE') THEN
        RAISE EXCEPTION 'Runtime pode alterar estrutura ou historico Flyway';
END IF;
END $$;
COMMIT;
