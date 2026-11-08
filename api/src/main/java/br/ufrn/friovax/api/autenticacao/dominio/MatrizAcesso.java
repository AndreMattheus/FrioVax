package br.ufrn.friovax.api.autenticacao.dominio;

import java.util.Map;
import java.util.Set;

import static br.ufrn.friovax.api.autenticacao.dominio.Perfil.*;

/** Matriz da US09, compartilhada por todas as operações atuais e futuras. */
public final class MatrizAcesso {
    public static final String CONSULTAR_OPERACAO = "consultar-operacao";
    public static final String GERENCIAR_CADASTRO = "gerenciar-cadastro";
    public static final String REGISTRAR_BAIXA = "registrar-baixa";
    public static final String DESCARTAR_LOTE = "descartar-lote";
    public static final String REGISTRAR_LEITURA = "registrar-leitura";
    public static final String ALTERAR_POLITICA = "alterar-politica";
    public static final String RESOLVER_INCIDENTE = "resolver-incidente";
    public static final String CONSULTAR_TRILHA = "consultar-trilha";

    private static final Map<String, Set<Perfil>> PERMISSOES = Map.of(
            CONSULTAR_OPERACAO, Set.of(ADMINISTRADOR, OPERADOR, AUDITOR),
            GERENCIAR_CADASTRO, Set.of(ADMINISTRADOR),
            REGISTRAR_BAIXA, Set.of(ADMINISTRADOR, OPERADOR),
            DESCARTAR_LOTE, Set.of(ADMINISTRADOR),
            REGISTRAR_LEITURA, Set.of(ADMINISTRADOR, OPERADOR),
            ALTERAR_POLITICA, Set.of(ADMINISTRADOR),
            RESOLVER_INCIDENTE, Set.of(ADMINISTRADOR),
            CONSULTAR_TRILHA, Set.of(ADMINISTRADOR, AUDITOR));

    private MatrizAcesso() {
    }

    public static Set<String> permissoes() {
        return PERMISSOES.keySet();
    }

    public static boolean permite(String permissao, Set<String> perfis) {
        return PERMISSOES.getOrDefault(permissao, Set.of()).stream()
                .anyMatch(perfil -> perfis.contains(perfil.name()));
    }
}
