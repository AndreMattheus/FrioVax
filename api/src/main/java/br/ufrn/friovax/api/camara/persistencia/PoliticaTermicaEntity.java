package br.ufrn.friovax.api.camara.persistencia;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Uma versão da política térmica de uma câmara. As linhas só são inseridas, nunca alteradas.
@Entity
@Table(name = "politicas_termicas")
public class PoliticaTermicaEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "camara_id", nullable = false)
    public Long camaraId;

    @Column(nullable = false)
    public int versao;

    @Column(name = "temperatura_minima", nullable = false)
    public BigDecimal temperaturaMinima;

    @Column(name = "temperatura_maxima", nullable = false)
    public BigDecimal temperaturaMaxima;

    @Column(nullable = false)
    public int limiar;

    @Column(name = "autor_id")
    public UUID autorId;

    @Column(name = "criada_em", nullable = false)
    public OffsetDateTime criadaEm;
}
