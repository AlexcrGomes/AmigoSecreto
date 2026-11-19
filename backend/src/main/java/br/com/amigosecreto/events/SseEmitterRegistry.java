package br.com.amigosecreto.events;

import br.com.amigosecreto.events.GroupEvents.DrawUndoneEvent;
import br.com.amigosecreto.events.GroupEvents.GroupDrawnEvent;
import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import br.com.amigosecreto.events.GroupEvents.GroupUpdatedEvent;
import br.com.amigosecreto.events.GroupEvents.MembershipRevokedEvent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.events.GroupEvents.RevocationReason;
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
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
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

    /**
     * No desligamento, encerra todas as conexões: os navegadores reconectam sozinhos (na nova instância).
     * Roda no {@link ContextClosedEvent}, ANTES do graceful shutdown do servidor — que, do contrário, ficaria
     * esperando até 30 s pelas respostas SSE abertas.
     */
    @EventListener(ContextClosedEvent.class)
    public void closeAll() {
        emitters.values()
                .forEach(byUser -> byUser.values()
                        .forEach(set -> set.forEach(emitter -> {
                            try {
                                emitter.complete();
                            } catch (IllegalStateException ignored) {
                                // já encerrada
                            }
                        })));
        emitters.clear();
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupUpdated(GroupUpdatedEvent event) {
        Map<String, Object> data = signalData(event.groupId(), event.status(), event.participantCount());
        data.put("confirmationsReset", event.confirmationsReset());
        broadcast(event.groupId(), "group-updated", jsonMapper.writeValueAsString(data));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDrawUndone(DrawUndoneEvent event) {
        broadcast(event.groupId(), "draw-undone", signal(event.groupId(), GroupStatus.OPEN, event.participantCount()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupFinished(GroupFinishedEvent event) {
        broadcast(
                event.groupId(),
                "group-finished",
                signal(event.groupId(), GroupStatus.FINISHED, event.participantCount()));
    }

    /**
     * Quem deixou de ser membro perde as conexões do grupo. Quem foi removido ou teve a saída aprovada recebe
     * antes o aviso {@code membership-revoked}, com o motivo — e só ele, nunca os demais participantes. Quem
     * saiu por conta própria não recebe aviso.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipRevoked(MembershipRevokedEvent event) {
        Map<UUID, Set<SseEmitter>> byUser = emitters.get(event.groupId());
        Set<SseEmitter> connections = byUser == null ? null : byUser.get(event.userId());
        if (connections == null) {
            return;
        }
        String notice = noticeReason(event.reason());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", event.groupId());
        data.put("reason", notice);
        String json = jsonMapper.writeValueAsString(data);
        for (SseEmitter emitter : Set.copyOf(connections)) {
            if (notice != null) {
                send(
                        event.groupId(),
                        event.userId(),
                        emitter,
                        SseEmitter.event().name("membership-revoked").data(json));
            }
            unregister(event.groupId(), event.userId(), emitter);
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // já encerrada
            }
        }
    }

    /** Comentário a cada 25 s: mantém a conexão viva em proxies e detecta clientes que sumiram. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((groupId, byUser) -> byUser.forEach((userId, set) -> set.forEach(
                emitter -> send(groupId, userId, emitter, SseEmitter.event().comment("ping")))));
    }

    /** O {@code reason} do aviso, ou {@code null} quando a pessoa saiu sozinha e não há aviso. */
    private static String noticeReason(RevocationReason reason) {
        return switch (reason) {
            case LEFT -> null;
            case REMOVED -> "removed";
            case EXIT_APPROVED -> "exit-approved";
        };
    }

    private String signal(UUID groupId, GroupStatus status, int participantCount) {
        return jsonMapper.writeValueAsString(signalData(groupId, status, participantCount));
    }

    private static Map<String, Object> signalData(UUID groupId, GroupStatus status, int participantCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("status", status);
        data.put("participantCount", participantCount);
        return data;
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
