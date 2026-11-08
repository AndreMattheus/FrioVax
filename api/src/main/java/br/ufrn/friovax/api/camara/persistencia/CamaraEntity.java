package br.ufrn.friovax.api.camara.persistencia;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoTermico;
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

@Entity
@Table(name = "camaras")
public class CamaraEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String codigo;

    @Column(nullable = false)
    public String nome;

    @Column(nullable = false)
    public String unidade;

    @Column(nullable = false)
    public int capacidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_termico", nullable = false)
    public EstadoTermico estadoTermico;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EstadoCamara estado;

    @Column(nullable = false)
    public boolean ativo;

    @Column(name = "criado_em", nullable = false)
    public OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    public OffsetDateTime atualizadoEm;
}
