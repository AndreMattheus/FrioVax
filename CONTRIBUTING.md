# Como contribuir

1. Escolha um item priorizado no GitHub Projects sem exceder o WIP.
2. Crie uma branch curta: `feat/<issue>-descricao`, `fix/<issue>-descricao` ou `docs/<issue>-descricao`.
3. Faça commits pequenos e compreensíveis. Pair programming deve registrar `Co-authored-by`.
4. Execute `mise run ci` antes de abrir o pull request.
5. Vincule a issue no PR com `Closes #<numero>` e preencha o checklist.
6. Solicite a revisão definida em [docs/processo.md](docs/processo.md).
7. Só faça merge com critérios de aceitação atendidos, CI verde e uma aprovação.

Não versione segredos, tokens, credenciais ou arquivos `.env`.

