package br.ufrn.friovax.api.arquitetura;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;

/** Verifica as fronteiras do ADR 0002 no bytecode de produção, sem iniciar o Quarkus. */
@AnalyzeClasses(packages = "br.ufrn.friovax.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    private static final String DOMINIO = "br.ufrn.friovax.api.*.dominio..";
    private static final String API = "br.ufrn.friovax.api.*.api..";
    private static final String APLICACAO = "br.ufrn.friovax.api.*.aplicacao..";
    private static final String PERSISTENCIA = "br.ufrn.friovax.api.*.persistencia..";
    private static final String INTEGRACAO = "br.ufrn.friovax.api.*.integracao..";
    private static final String[] TRANSPORTE_GRPC = {"io.grpc..", "com.google.protobuf..", "io.quarkus.grpc..",
            "br.ufrn.friovax.telemetry.."};

    @ArchTest
    static final ArchRule dominio_independente = classes().that().resideInAPackage(DOMINIO)
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMINIO)
            .because("o domínio deve depender apenas de Java e de outros tipos de domínio (ADR 0002)");

    @ArchTest
    static final ArchRule dominio_sem_jdbc = noClasses().that().resideInAPackage(DOMINIO)
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..")
            .because("JDBC é infraestrutura, mesmo quando pertence à biblioteca padrão Java");

    @ArchTest
    static final ArchRule sentido_das_dependencias = layeredArchitecture()
            .consideringAllDependencies()
            .layer("API").definedBy(API, "br.ufrn.friovax.api")
            .layer("Aplicacao").definedBy(APLICACAO)
            .layer("Dominio").definedBy(DOMINIO)
            .layer("Persistencia").definedBy(PERSISTENCIA)
            .layer("Integracao").definedBy(INTEGRACAO)
            .whereLayer("API").mayNotBeAccessedByAnyLayer()
            .whereLayer("Aplicacao").mayOnlyBeAccessedByLayers("API")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers("API", "Aplicacao", "Persistencia", "Integracao")
            .whereLayer("Persistencia").mayNotBeAccessedByAnyLayer()
            .whereLayer("Integracao").mayNotBeAccessedByAnyLayer()
            .because("API chama aplicação; aplicação e adaptadores dependem do domínio (ADR 0002)");

    @ArchTest
    static final ArchRule api_nao_acessa_repositorios = noClasses().that().resideInAnyPackage(API, "br.ufrn.friovax.api")
            .should().dependOnClassesThat(resideInAPackage(DOMINIO).and(simpleNameEndingWith("Repository")))
            .because("somente os casos de uso devem acessar as interfaces de repositório");

    @ArchTest
    static final ArchRule aplicacao_sem_http_ou_jpa = noClasses().that().resideInAPackage(APLICACAO)
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.ws.rs..", "jakarta.persistence..",
                    "javax.ws.rs..", "javax.persistence..", "org.hibernate..", "io.quarkus.hibernate..",
                    "java.sql..", "javax.sql..")
            .because("a aplicação usa interfaces do domínio e não conhece HTTP ou persistência concreta");

    @ArchTest
    static final ArchRule dominio_e_aplicacao_sem_grpc = noClasses().that().resideInAnyPackage(DOMINIO, APLICACAO)
            .should().dependOnClassesThat().resideInAnyPackage(TRANSPORTE_GRPC)
            .because("a análise térmica é usada pela porta do domínio; gRPC e tipos gerados ficam no adaptador "
                    + "(ADR 0003, seção 1)");

    @ArchTest
    static final ArchRule grpc_somente_no_adaptador = noClasses().that().resideOutsideOfPackage(INTEGRACAO)
            .should().dependOnClassesThat().resideInAnyPackage(TRANSPORTE_GRPC)
            .because("somente o adaptador de integração traduz Protobuf e status gRPC");

    @ArchTest
    static final ArchRule persistencia_sem_http = noClasses().that().resideInAPackage(PERSISTENCIA)
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.ws.rs..", "javax.ws.rs..")
            .because("adaptadores de persistência não produzem respostas HTTP");
}
