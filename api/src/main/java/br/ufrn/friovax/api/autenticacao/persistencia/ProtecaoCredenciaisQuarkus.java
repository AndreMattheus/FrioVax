package br.ufrn.friovax.api.autenticacao.persistencia;

import br.ufrn.friovax.api.autenticacao.dominio.ProtecaoCredenciais;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@ApplicationScoped
public class ProtecaoCredenciaisQuarkus implements ProtecaoCredenciais {
    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public String protegerSenha(String senha) { return BcryptUtil.bcryptHash(senha); }

    @Override
    public boolean verificarSenha(String senha, String hash) { return BcryptUtil.matches(senha, hash); }

    @Override
    public String novoRefresh() {
        var bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hashRefresh(String refresh) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(refresh.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
