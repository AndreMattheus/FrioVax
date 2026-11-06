package br.ufrn.friovax.api.autenticacao.persistencia;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
public class UsuarioEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(nullable = false, unique = true, length = 100) public String login;
    @Column(name = "senha_hash", nullable = false, length = 60) public String senhaHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) public Perfil perfil;
}
