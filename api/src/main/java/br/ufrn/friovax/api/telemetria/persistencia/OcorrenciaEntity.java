package br.ufrn.friovax.api.telemetria.persistencia;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ocorrência térmica. Evidências e lotes afetados ficam em tabelas próprias ({@code ocorrencia_evidencias} e
 * {@code ocorrencia_lotes}), gravadas uma única vez, na abertura.
 */
@Entity
@Table(name = "ocorrencias")
public class OcorrenciaEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "camara_id", nullable = false)
    public Long camaraId;

    @Column(name = "politica_versao", nullable = false)
    public int politicaVersao;

    @Column(name = "leitura_abertura_id", nullable = false)
    public Long leituraAberturaId;

    @Column(name = "aberta_em", nullable = false)
    public OffsetDateTime abertaEm;

    @Column(name = "resolvida_em")
    public OffsetDateTime resolvidaEm;

    @Column(name = "resolvida_por")
    public UUID resolvidaPor;

    @Column
    public String justificativa;

    @Column(name = "leitura_resolucao_id")
    public Long leituraResolucaoId;
}
