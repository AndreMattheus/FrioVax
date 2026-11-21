package br.ufrn.friovax.api.auditoria.persistencia;

import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "eventos_auditoria")
public class EventoDeAuditoriaEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, updatable = false)
    public OffsetDateTime instante;

    @Column(name = "ator_id", updatable = false)
    public UUID atorId;

    @Column(name = "ator_login", nullable = false, updatable = false)
    public String atorLogin;

    @Column(name = "ator_perfil", nullable = false, updatable = false)
    public String atorPerfil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    public RecursoAuditado recurso;

    @Column(name = "recurso_id", nullable = false, updatable = false)
    public long recursoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    public OperacaoAuditada operacao;

    @Column(nullable = false, updatable = false)
    public String descricao;

    @Column(name = "correlacao_id", nullable = false, updatable = false)
    public String correlacaoId;
}
