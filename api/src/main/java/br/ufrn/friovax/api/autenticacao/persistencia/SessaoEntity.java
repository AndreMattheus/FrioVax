package br.ufrn.friovax.api.autenticacao.persistencia;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessoes")
public class SessaoEntity extends PanacheEntityBase {
    @Id @Column(name = "token_hash", length = 64) public String tokenHash;
    @Column(name = "usuario_id", nullable = false) public UUID usuarioId;
    @Column(name = "criada_em", nullable = false) public Instant criadaEm;
    @Column(name = "expira_em", nullable = false) public Instant expiraEm;
    @Column(name = "revogada_em") public Instant revogadaEm;
}
