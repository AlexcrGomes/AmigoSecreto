package br.com.amigosecreto.events;

import br.com.amigosecreto.events.GroupEvents.GroupDrawnEvent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.group.GroupStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conexões SSE abertas, por grupo e por usuário (várias abas contam como várias conexões). Recebe os
 * eventos de domínio após o commit e os repassa como sinais, conforme contracts/sse-events.md.
 */
@Component
public class SseEmitterRegistry {

    static final long TIMEOUT_MS = Duration.ofHours(1).toMillis();
    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    /** grupo → usuário → conexões. */
    private final Map<UUID, Map<UUID, Set<SseEmitter>>> emitters = new ConcurrentHashMap<>();

    private final JsonMapper jsonMapper;

    public SseEmitterRegistry(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Abre uma conexão do usuário (já verificado como membro) no grupo. */
    public SseEmitter register(UUID groupId, UUID userId) {
        SseEmitter emitter = createEmitter();
        emitters.computeIfAbsent(groupId, g -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, u -> ConcurrentHashMap.newKeySet())
                .add(emitter);
        emitter.onCompletion(() -> unregister(groupId, userId, emitter));
        emitter.onTimeout(() -> {
            unregister(groupId, userId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> unregister(groupId, userId, emitter));
        return emitter;
    }

    SseEmitter createEmitter() {
        return new SseEmitter(TIMEOUT_MS);
    }

    void unregister(UUID groupId, UUID userId, SseEmitter emitter) {
        emitters.computeIfPresent(groupId, (g, byUser) -> {
            byUser.computeIfPresent(userId, (u, set) -> {
                set.remove(emitter);
                return set.isEmpty() ? null : set;
            });
            return byUser.isEmpty() ? null : byUser;
        });
    }

    /** Quantidade de conexões abertas no grupo. */
    public int connectionCount(UUID groupId) {
        Map<UUID, Set<SseEmitter>> byUser = emitters.get(groupId);
        return byUser == null ? 0 : byUser.values().stream().mapToInt(Set::size).sum();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onParticipantsChanged(ParticipantsChangedEvent event) {
        broadcast(
                event.groupId(),
                "participants-changed",
                signal(event.groupId(), event.status(), event.participantCount()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupDrawn(GroupDrawnEvent event) {
        broadcast(event.groupId(), "group-drawn", signal(event.groupId(), GroupStatus.DRAWN, event.participantCount()));
    }

    /** Comentário a cada 25 s: mantém a conexão viva em proxies e detecta clientes que sumiram. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((groupId, byUser) -> byUser.forEach((userId, set) -> set.forEach(
                emitter -> send(groupId, userId, emitter, SseEmitter.event().comment("ping")))));
    }

    private String signal(UUID groupId, GroupStatus status, int participantCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("status", status);
        data.put("participantCount", participantCount);
        return jsonMapper.writeValueAsString(data);
    }

    private void broadcast(UUID groupId, String name, String json) {
        Map<UUID, Set<SseEmitter>> byUser = emitters.get(groupId);
        if (byUser == null) {
            return;
        }
        byUser.forEach((userId, set) -> set.forEach(emitter ->
                send(groupId, userId, emitter, SseEmitter.event().name(name).data(json))));
    }

    private void send(UUID groupId, UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // Cliente desconectado: descarta a conexão sem derrubar as demais
            log.debug("Conexão SSE encerrada no grupo {}", groupId);
            unregister(groupId, userId, emitter);
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // já encerrada
            }
        }
    }
}
