# Autorização por operação — US09 / #81

Todas as rotas de câmaras e lotes exigem `Authorization: Bearer <accessToken>`. A matriz central está em `autenticacao/dominio/MatrizAcesso.java`; `autenticacao/api/GuardsOperacao.java` conecta cada permissão ao `SecurityIdentity` validado pelo Quarkus, sem consultar o banco.

| Permissão | Administrador | Operador | Auditor | Aplicação |
|---|:---:|:---:|:---:|---|
| `consultar-operacao` | Sim | Sim | Sim | GET de câmaras e lotes; futuras consultas operacionais |
| `gerenciar-cadastro` | Sim | Não | Não | POST, PUT e DELETE de câmaras e lotes |
| `registrar-baixa` | Sim | Sim | Não | POST de baixas |
| `descartar-lote` | Sim | Não | Não | POST de descarte |
| `registrar-leitura` | Sim | Sim | Não | Futuro registro de leitura |
| `alterar-politica` | Sim | Não | Não | Futura alteração de política |
| `resolver-incidente` | Sim | Não | Não | Futura resolução de incidente |
| `consultar-trilha` | Sim | Não | Sim | Futura consulta da trilha |

As guardas futuras já têm testes HTTP em resources exclusivos de teste; as respectivas funcionalidades devem aplicar essas guardas e testar suas rotas reais quando forem implementadas. Os contadores de execução e chamada externa nesses resources são simulados; nenhuma chamada Go real é feita por eles.

Ausência de Bearer ou token inválido/expirado retorna **401**. JWT válido sem perfil permitido (inclusive sem grupos ou com grupo desconhecido) retorna **403**. A rejeição acontece antes do caso de uso, da persistência e de chamadas externas. Campos extras como `perfil`, `ator` e `usuarioId` são ignorados pelos DTOs atuais e nunca participam da autorização. Ao implementar auditoria, derive o ator do `sub` do JWT validado.

## Rotas públicas

`GET /api/status`, `/q/health` e suas sondas, `/q/openapi` e Swagger UI permanecem públicos. `POST /api/auth/login` valida login e senha; `/renovar` e `/logout` validam o refresh enviado, sem exigir access válido. `GET /api/auth/identidade` exige autenticação.

Publicar a documentação não libera as operações exibidas. No Swagger UI, faça login, copie o `accessToken` e use **Authorize** com esse token. O **Try it out** envia Bearer e passa pelas mesmas guardas. Não há regra `permit` abrangendo `/api/*` ou ignorando segurança por origem/Referer.

## Regra obrigatória para novas rotas

1. Selecione a permissão da matriz conforme a operação. Declare-a diretamente no método:

   ```java
   @POST
   @PermissionsAllowed(MatrizAcesso.REGISTRAR_LEITURA)
   public Response registrarLeitura(@Valid LeituraRequest body) {
       // O caso de uso recebe o ator da identidade autenticada quando necessário.
       return Response.ok(service.registrar(body)).build();
   }
   ```

2. Mantenha `quarkus.security.jaxrs.deny-unannotated-endpoints=true`: a ausência de anotação nega acesso a todos. Não declare permissão de consulta na classe, pois isso poderia liberar mutações futuras. `AutorizacaoRotasTest` verifica os métodos HTTP de produção e restringe as exceções públicas.
3. Derive identidade e perfil somente do JWT validado. A autorização deve preceder execução e chamada Go; nenhum valor do cliente pode elevar privilégios ou substituir o ator.
4. Documente Bearer JWT, perfis e respostas 401/403 na OpenAPI e atualize os exemplos.
5. Teste todas as identidades na rota real, tanto sucesso quanto negação, e confirme que a negação não altera dados nem chama integrações. Uma funcionalidade não fecha só por reutilizar a guarda.

## Suporte e validação

`AutenticacaoHttpSupport.autenticado(Perfil.OPERADOR)` cria uma requisição com JWT RS256 assinado pelas chaves exclusivas de teste. `given()` desse helper usa administrador para os testes de negócio existentes. Use `io.restassured.RestAssured.given()` diretamente para testar anônimos; não configure autenticação global nem desative a segurança no perfil.

```bash
mise exec -- mvn --batch-mode -f api/pom.xml "-Dtest=AutorizacaoHttpMemoriaTest,AutorizacaoRotasTest" test
mise run test-http
mise run ci
```

`AutorizacaoHttpMemoriaTest` percorre cada operação existente com administrador, operador, auditor, anônimo, JWT sem grupos e grupo desconhecido; compara os registros antes/depois das negações, tenta forjar perfil/ator e verifica que saúde/documentação não permitem bypass. Também testa a negação de uma rota nova sem anotação e as guardas reservadas às próximas funcionalidades.
