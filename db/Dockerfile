# syntax=docker/dockerfile:1
# PostgreSQL oficial com duas correções de segurança (gate 5: sem HIGH/CRITICAL):
# - pacotes do Alpine atualizados;
# - o binário gosu (compilado com um Go antigo, cheio de CVEs da stdlib) é removido e trocado pelo
#   su-exec do Alpine, que tem a mesma interface ("gosu usuário comando") usada pelo
#   docker-entrypoint.sh. O atalho fica em /usr/bin (também no PATH): apagar o arquivo original,
#   em vez de sobrescrevê-lo, faz os scanners enxergarem a remoção.
FROM postgres:18.7-alpine3.24
RUN apk upgrade --no-cache \
 && apk add --no-cache su-exec \
 && rm -f /usr/local/bin/gosu \
 && ln -s /sbin/su-exec /usr/bin/gosu \
 && gosu nobody true
