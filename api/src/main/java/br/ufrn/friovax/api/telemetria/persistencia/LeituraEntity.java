package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.telemetria.dominio.ClassificacaoTermica;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "leituras")
public class LeituraEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "camara_id", nullable = false)
    public Long camaraId;

    @Column(name = "id_externo", nullable = false)
    public String idExterno;

    @Column(nullable = false)
    public OffsetDateTime instante;

    @Column(nullable = false)
    public BigDecimal temperatura;

    @Column(nullable = false)
    public String origem;

    @Column(name = "politica_versao", nullable = false)
    public int politicaVersao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ClassificacaoTermica classificacao;

    @Column(nullable = false)
    public int sequencia;

    @Column(name = "limiar_atingido", nullable = false)
    public boolean limiarAtingido;

    @Column(name = "aceita_em", nullable = false)
    public OffsetDateTime aceitaEm;
}
