package br.ufrn.friovax.api.auditoria.dominio;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Quem fez a alteração, sempre derivado pelo servidor: do JWT validado ({@code sub}, {@code upn} e perfil) ou, quando
 * não há usuário autenticado (inicialização, tarefas internas), o ator {@link #SISTEMA}. Nunca vem do body.
 * @param id     id interno do usuário (o {@code sub} do JWT); nulo somente para o {@link #SISTEMA}
 * @param login  login do usuário, ou {@code SISTEMA}
 * @param perfil {@code ADMINISTRADOR}, {@code OPERADOR}, {@code AUDITOR} ou {@code SISTEMA}
 */
public record Ator(UUID id, String login, String perfil) {

    public static final String PERFIL_SISTEMA = "SISTEMA";
    public static final int LOGIN_MAXIMO = 100;
    private static final Set<String> PERFIS = Set.of(Perfil.ADMINISTRADOR.name(), Perfil.OPERADOR.name(),
            Perfil.AUDITOR.name(), PERFIL_SISTEMA);

    public static final Ator SISTEMA = new Ator(null, "SISTEMA", PERFIL_SISTEMA);

    public Ator {
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(perfil, "perfil");
        if (login.isBlank() || login.length() > LOGIN_MAXIMO) {
            throw new IllegalArgumentException("O login do ator deve ter de 1 a " + LOGIN_MAXIMO + " caracteres");
        }
        if (!PERFIS.contains(perfil)) {
            throw new IllegalArgumentException("Perfil de ator desconhecido: " + perfil);
        }
        if (PERFIL_SISTEMA.equals(perfil) != (id == null)) {
            throw new IllegalArgumentException("Só o ator SISTEMA não tem id, e todo usuário tem id");
        }
    }

    public static Ator usuario(UUID id, String login, Perfil perfil) {
        return new Ator(Objects.requireNonNull(id, "id"), login, Objects.requireNonNull(perfil, "perfil").name());
    }

    public boolean isSistema() {
        return id == null;
    }
}
