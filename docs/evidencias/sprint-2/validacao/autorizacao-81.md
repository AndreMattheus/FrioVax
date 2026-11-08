# Validação de autorização — #81

Execução local em **06/11/2026**, fuso `America/Fortaleza`.

| Verificação | Resultado |
|---|---|
| `mise run ci` | Aprovado: Maven `verify`, formatação Go, `go vet`, testes e build Go |
| Suíte Java completa | 625 testes; zero falhas, erros ou ignorados |
| `AutorizacaoHttpMemoriaTest` | 139 cenários: matriz das 12 operações, tokens inválidos/expirados, perfis desconhecidos/ausentes, tentativas de forjar perfil/ator, recursos públicos, guardas futuras e rota sem anotação |
| `AutenticacaoPostgresTest` | 8 testes aprovados, incluindo negações em rotas operacionais sem executar SQL |
| `AutorizacaoRotasTest` | Métodos HTTP de produção com permissão explícita e exceções públicas restritas |
| OpenAPI | Bearer, perfis corretos e 401/403 nas 12 operações de câmaras/lotes; autenticação/status públicos sem exigência Bearer |
| Artefato de produção | `GuardsOperacao` presente; resource e chaves exclusivos de teste ausentes |
| `bash -n docs/scripts/validar-sprint-1.sh` | Sintaxe aprovada; roteiro atualizado para exigir Bearer de administrador |
| `git diff --check` | Sem erros de whitespace |

Os cenários HTTP em memória usam JWT RS256 real e mantêm a segurança ativa. Nas negações, comparam-se os registros de câmaras e lotes, incluindo inativos, antes e depois. O teste PostgreSQL observa a contagem de comandos preparados pelo Hibernate e confirma que ela não cresce em requisições rejeitadas.

As rotas futuras de leitura, política, resolução e trilha ainda não existem em produção. Suas guardas são exercitadas por um resource exclusivo de teste, com contadores simulados de execução/chamada externa. Cada funcionalidade futura deverá aplicar a permissão e comprovar a ausência de efeitos nas próprias rotas e integrações, conforme a [diretriz](../../../autorizacao.md).

O roteiro manual que reinicia o Compose não foi executado nesta validação; a integração com PostgreSQL foi verificada pela suíte automatizada com Dev Services.
