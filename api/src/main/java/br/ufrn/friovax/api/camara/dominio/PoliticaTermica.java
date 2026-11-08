package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma versão da política térmica de uma câmara: faixa segura e limiar N de leituras inseguras consecutivas
 * (ADR 0003, seção 2). É imutável: alterar a política gera uma nova versão, e as anteriores ficam no histórico.
 */
public final class PoliticaTermica {

  /** Limiar adotado nas câmaras novas e migradas. Valor de demonstração, não uma recomendação clínica. */
  public static final int LIMIAR_INICIAL = 3;
  public static final int LIMIAR_MINIMO = 1;
  public static final int LIMIAR_MAXIMO = 100;

  private static final BigDecimal LIMITE_INFERIOR_TEMPERATURA = new BigDecimal("-999.9");
  private static final BigDecimal LIMITE_SUPERIOR_TEMPERATURA = new BigDecimal("999.9");

  private final int versao;
  private final BigDecimal temperaturaMinima;
  private final BigDecimal temperaturaMaxima;
  private final int limiar;
  private final UUID autorId;
  private final OffsetDateTime criadaEm;

  private PoliticaTermica(int versao, BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima, int limiar,
                          UUID autorId, OffsetDateTime criadaEm) {
    this.versao = versao;
    this.temperaturaMinima = temperaturaMinima;
    this.temperaturaMaxima = temperaturaMaxima;
    this.limiar = limiar;
    this.autorId = autorId;
    this.criadaEm = criadaEm;
  }

  public static PoliticaTermica inicial(BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                                        UUID autorId, OffsetDateTime agora) {
    return validada(1, temperaturaMinima, temperaturaMaxima, LIMIAR_INICIAL, autorId, agora);
  }

  public static PoliticaTermica reconstituir(int versao, BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                                             int limiar, UUID autorId, OffsetDateTime criadaEm) {
    return new PoliticaTermica(versao, temperaturaMinima, temperaturaMaxima, limiar, autorId, criadaEm);
  }

  public boolean temMesmosValores(BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima, int limiar) {
    return this.temperaturaMinima.compareTo(temperaturaMinima) == 0
      && this.temperaturaMaxima.compareTo(temperaturaMaxima) == 0
      && this.limiar == limiar;
  }

  /** Cria a versão seguinte, validando os novos valores. Não verifica se houve mudança: ver {@link Camara}. */
  PoliticaTermica proximaVersao(BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima, int limiar,
                                UUID autorId, OffsetDateTime agora) {
    return validada(versao + 1, temperaturaMinima, temperaturaMaxima, limiar, autorId, agora);
  }

  static BigDecimal[] faixaNormalizada(BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima) {
    var minima = temperatura("temperaturaMinima", temperaturaMinima);
    var maxima = temperatura("temperaturaMaxima", temperaturaMaxima);
    if (minima.compareTo(maxima) >= 0) {
      throw new ValidacaoDeNegocio("temperaturaMinima", "deve ser menor que temperaturaMaxima");
    }
    return new BigDecimal[] {minima, maxima};
  }

  static int limiarValidado(int limiar) {
    if (limiar < LIMIAR_MINIMO || limiar > LIMIAR_MAXIMO) {
      throw new ValidacaoDeNegocio("limiar", "deve estar entre " + LIMIAR_MINIMO + " e " + LIMIAR_MAXIMO);
    }
    return limiar;
  }

  private static PoliticaTermica validada(int versao, BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                                          int limiar, UUID autorId, OffsetDateTime agora) {
    var faixa = faixaNormalizada(temperaturaMinima, temperaturaMaxima);
    return new PoliticaTermica(versao, faixa[0], faixa[1], limiarValidado(limiar), autorId,
      Objects.requireNonNull(agora, "agora"));
  }

  private static BigDecimal temperatura(String campo, BigDecimal valor) {
    if (valor == null) {
      throw new ValidacaoDeNegocio(campo, "deve ser informada");
    }
    if (valor.compareTo(LIMITE_INFERIOR_TEMPERATURA) < 0 || valor.compareTo(LIMITE_SUPERIOR_TEMPERATURA) > 0) {
      throw new ValidacaoDeNegocio(campo, "deve estar entre -999.9 e 999.9");
    }
    var normalizada = valor.stripTrailingZeros();
    if (normalizada.scale() > 1) {
      throw new ValidacaoDeNegocio(campo, "deve ter no máximo 1 casa decimal");
    }
    return normalizada.setScale(1);
  }

  public int getVersao() {
    return versao;
  }

  public BigDecimal getTemperaturaMinima() {
    return temperaturaMinima;
  }

  public BigDecimal getTemperaturaMaxima() {
    return temperaturaMaxima;
  }

  public int getLimiar() {
    return limiar;
  }

  public Optional<UUID> getAutorId() {
    return Optional.ofNullable(autorId);
  }

  public OffsetDateTime getCriadaEm() {
    return criadaEm;
  }
}
