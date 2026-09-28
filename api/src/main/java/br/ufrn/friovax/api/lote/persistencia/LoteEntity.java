package br.ufrn.friovax.api.lote.persistencia;

import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "lotes")
public class LoteEntity extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String codigo;

    @Column(nullable = false)
    public String imunobiologico;

    @Column(nullable = false)
    public String fabricante;

    @Column(nullable = false)
    public LocalDate validade;

    @Column(nullable = false)
    public int quantidade;

    // Só o id: lote não depende do mapeamento de câmara (ADR 0001).
    @Column(name = "camara_id", nullable = false)
    public long camaraId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EstadoLote estado;

    @Column(nullable = false)
    public boolean ativo;

    @Column(name = "criado_em", nullable = false)
    public OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    public OffsetDateTime atualizadoEm;
}
