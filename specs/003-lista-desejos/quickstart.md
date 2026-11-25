# Quickstart: validar a feature 003 (lista de desejos e valor do presente)

Pré-requisitos, `.env`, subida, testes e encerramento são **os mesmos** dos quickstarts das
features [001](../001-amigo-secreto-sorteio/quickstart.md) e
[002](../002-grupo-evento-confirmacao/quickstart.md) (Docker no WSL, `docker compose up -d --build`,
`mvn -B verify` e `npm run test:coverage` em containers). API em
[contracts/openapi.yaml](./contracts/openapi.yaml), telas em
[contracts/ui-routes.md](./contracts/ui-routes.md), tabelas em [data-model.md](./data-model.md).

> Dica do roteiro da 002: o WSL desliga quando fica ocioso e reinicia os containers. Durante o
> roteiro, mantenha um terminal WSL aberto (ou `wsl -e sleep 7200` em segundo plano).

## 1. Migração

```bash
docker compose exec db psql -U amigo -d amigosecreto -c "select version, success from flyway_schema_history order by installed_rank desc limit 1;"
```

**Esperado**: `V7` com `success = t`. Grupos existentes continuam iguais, sem valor do presente
e com todas as listas vazias (FR-019).

## 2. Roteiro de validação manual

Três sessões (A = organizador, B e C = participantes), uma a 360px.

| # | Passo | Resultado esperado | Cobre |
|---|-------|--------------------|-------|
| 1 | A cria o grupo com valor do presente R$ 80 | B e C, ao entrar, veem "Valor do presente: R$ 80" | US3-1, FR-021 |
| 2 | A tenta valor 0, 100001 e 50,5 | recusado com "Informe um valor inteiro entre R$ 1 e R$ 100.000." | US3-4 |
| 3 | B confirma; A muda o valor para R$ 100 | todos veem R$ 100; B continua confirmado | US3-2, FR-023 |
| 4 | B cadastra 3 itens (um com link `https://…`), edita um e remove outro | lista na ordem de cadastro, "2 de 5" | US1-1 a US1-3 |
| 5 | B tenta link `javascript:alert(1)` e item sem descrição | recusado com a mensagem do campo | US1-4, FR-003 |
| 6 | B cadastra até 5 itens e tenta o 6º | "A lista tem no máximo 5 sugestões." | US1-5 |
| 7 | B põe `<b>x</b>` na descrição | aparece como texto | FR-008 |
| 8 | A e C abrem o grupo | cada um vê só a própria lista; nada da lista de B | US1-7, FR-009, FR-012 |
| 9 | C confirma; A sorteia | o campo de valor fica desabilitado para A, com a explicação | US3-6, FR-022 |
| 10 | Quem tirou B toca em "Revelar" | nome de B, valor do presente e os itens de B | US2-1, FR-010 |
| 11 | Quem não tirou B revela | nome e lista da própria pessoa tirada; nunca a de B | FR-009, SC-002 |
| 12 | Quem tirou alguém sem itens revela | "Ainda não há sugestões de presente." | US2-2 |
| 13 | B altera um item; quem tirou B revela de novo | versão nova e "Atualizada em dd/mm"; nenhum aviso ao vivo antes | US2-4, FR-015a |
| 14 | Quem tirou B toca no link | modal de site externo; abre em outra aba | US2-5, FR-017 |
| 15 | "Ocultar" | nome e lista somem juntos | US2-3, FR-011 |
| 16 | Num grupo de 4 sorteado, um participante pede para sair e A aprova | quem tirava esse participante não vê mais a lista dele (`409 group-not-drawn`); a lista dele foi apagada | FR-013, FR-018 |
| 17 | Grupo finalizado (simular como no quickstart da 002, §3) | cada um ainda revela nome e lista; a própria lista fica somente leitura | US1-6, US2-7, FR-014 |

## 3. Verificação de sigilo (obrigatória — Constituição I)

1. Repita a verificação das features 001 e 002 (aba Rede, eventos SSE sem nomes, pares
   cifrados, logs limpos).
2. Na aba Rede, confira que `GET /groups/{id}` traz só `myWishlist` (a própria lista) e que
   `my-secret-friend` não traz o id de quem foi tirado.
3. Nenhum evento SSE nem linha de log contém descrição ou link de item:

```bash
docker compose logs backend | grep -c "<trecho de uma descrição cadastrada>"
```

**Esperado**: `0`.

4. Depois do passo 16, quem saiu não tem mais itens:

```bash
docker compose exec db psql -U amigo -d amigosecreto -c "select count(*) from wish_item w left join group_member m on m.group_id = w.group_id and m.user_id = w.user_id where m.user_id is null;"
```

**Esperado**: `0` — nenhum item sem participação.
