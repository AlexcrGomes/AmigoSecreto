package br.com.amigosecreto.events;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.amigosecreto.events.GroupEvents.GroupDrawnEvent;
import br.com.amigosecreto.events.GroupEvents.MembershipRevokedEvent;
import br.com.amigosecreto.events.GroupEvents.ParticipantsChangedEvent;
import br.com.amigosecreto.events.GroupEvents.RevocationReason;
import br.com.amigosecreto.group.GroupStatus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

class SseEmitterRegistryTest {

    private static final UUID GROUP = UUID.randomUUID();
    private static final UUID OTHER_GROUP = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    /** Emitter que guarda o texto de cada evento enviado (ou falha, se quebrado). */
    static class RecordingEmitter extends SseEmitter {
        final List<String> sent = new ArrayList<>();
        boolean broken;
        boolean completed;

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (broken) {
                throw new IOException("conexão perdida");
            }
            sent.add(builder.build().stream()
                    .map(part -> String.valueOf(part.getData()))
                    .collect(Collectors.joining()));
        }
    }

    private final List<RecordingEmitter> created = new ArrayList<>();
    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry(JsonMapper.builder().build()) {
            @Override
            SseEmitter createEmitter() {
                RecordingEmitter emitter = new RecordingEmitter();
                created.add(emitter);
                return emitter;
            }
        };
    }

    @Test
    void participantsChangedReachesEveryEmitterOfTheGroupOnly() {
        RecordingEmitter ana = (RecordingEmitter) registry.register(GROUP, ANA);
        RecordingEmitter anaSecondTab = (RecordingEmitter) registry.register(GROUP, ANA);
        RecordingEmitter bruno = (RecordingEmitter) registry.register(GROUP, BRUNO);
        RecordingEmitter otherGroup = (RecordingEmitter) registry.register(OTHER_GROUP, BRUNO);

        registry.onParticipantsChanged(new ParticipantsChangedEvent(GROUP, GroupStatus.OPEN, 7));

        assertThat(ana.sent).hasSize(1);
        assertThat(anaSecondTab.sent).hasSize(1);
        assertThat(bruno.sent).hasSize(1);
        assertThat(otherGroup.sent).isEmpty();

        String event = ana.sent.getFirst();
        assertThat(event).contains("event:participants-changed");
        assertThat(event).contains("data:{\"groupId\":\"" + GROUP + "\",\"status\":\"OPEN\",\"participantCount\":7}");
    }

    @Test
    void eventDataHasOnlyGroupIdStatusAndCountNeverNamesOrUserIds() {
        RecordingEmitter ana = (RecordingEmitter) registry.register(GROUP, ANA);

        registry.onParticipantsChanged(new ParticipantsChangedEvent(GROUP, GroupStatus.OPEN, 3));
        registry.onGroupDrawn(new GroupDrawnEvent(GROUP, 3));

        assertThat(ana.sent).hasSize(2);
        for (String event : ana.sent) {
            String data = event.substring(event.indexOf("data:") + 5).strip();
            assertThat(JsonMapper.builder().build().readTree(data).propertyNames())
                    .containsExactlyInAnyOrder("groupId", "status", "participantCount");
            assertThat(event).doesNotContain(ANA.toString()).doesNotContain("name");
        }
        assertThat(ana.sent.get(1)).contains("event:group-drawn").contains("\"status\":\"DRAWN\"");
    }

    @Test
    void brokenEmitterIsRemoved() {
        RecordingEmitter ana = (RecordingEmitter) registry.register(GROUP, ANA);
        RecordingEmitter bruno = (RecordingEmitter) registry.register(GROUP, BRUNO);
        ana.broken = true;

        registry.onParticipantsChanged(new ParticipantsChangedEvent(GROUP, GroupStatus.OPEN, 2));

        assertThat(registry.connectionCount(GROUP)).isEqualTo(1);
        assertThat(bruno.sent).hasSize(1);
    }

    @Test
    void heartbeatSendsPingComment() {
        RecordingEmitter ana = (RecordingEmitter) registry.register(GROUP, ANA);
        RecordingEmitter bruno = (RecordingEmitter) registry.register(OTHER_GROUP, BRUNO);

        registry.heartbeat();

        assertThat(ana.sent).singleElement().asString().contains(":ping");
        assertThat(bruno.sent).singleElement().asString().contains(":ping");
    }

    @Test
    void completedEmitterIsUnregistered() {
        registry.register(GROUP, ANA);
        assertThat(registry.connectionCount(GROUP)).isEqualTo(1);

        registry.unregister(GROUP, ANA, created.getFirst());

        assertThat(registry.connectionCount(GROUP)).isZero();
        registry.onParticipantsChanged(new ParticipantsChangedEvent(GROUP, GroupStatus.OPEN, 1));
        assertThat(created.getFirst().sent).isEmpty();
    }

    @Test
    void removedUserGetsMembershipRevokedOnlyForThemAndTheirConnectionsAreClosed() {
        RecordingEmitter ana = (RecordingEmitter) registry.register(GROUP, ANA);
        RecordingEmitter bruno = (RecordingEmitter) registry.register(GROUP, BRUNO);
        RecordingEmitter brunoSecondTab = (RecordingEmitter) registry.register(GROUP, BRUNO);

        registry.onMembershipRevoked(new MembershipRevokedEvent(GROUP, BRUNO, RevocationReason.REMOVED));

        for (RecordingEmitter emitter : List.of(bruno, brunoSecondTab)) {
            assertThat(emitter.sent)
                    .singleElement()
                    .asString()
                    .contains("event:membership-revoked")
                    .contains("data:{\"groupId\":\"" + GROUP + "\"}");
            assertThat(emitter.completed).isTrue();
        }
        assertThat(ana.sent).isEmpty();
        assertThat(ana.completed).isFalse();
        assertThat(registry.connectionCount(GROUP)).isEqualTo(1);
    }

    @Test
    void userWhoLeftHasConnectionsClosedWithoutTheRemovalNotice() {
        RecordingEmitter bruno = (RecordingEmitter) registry.register(GROUP, BRUNO);

        registry.onMembershipRevoked(new MembershipRevokedEvent(GROUP, BRUNO, RevocationReason.LEFT));

        assertThat(bruno.sent).isEmpty();
        assertThat(bruno.completed).isTrue();
        assertThat(registry.connectionCount(GROUP)).isZero();
    }

    @Test
    void revokingSomeoneWithoutConnectionsIsHarmless() {
        registry.onMembershipRevoked(new MembershipRevokedEvent(GROUP, BRUNO, RevocationReason.REMOVED));

        assertThat(registry.connectionCount(GROUP)).isZero();
    }

    @Test
    void shutdownClosesEveryConnection() {
        RecordingEmitter ana = (RecordingEmitter) registry.register(GROUP, ANA);
        RecordingEmitter bruno = (RecordingEmitter) registry.register(OTHER_GROUP, BRUNO);

        registry.closeAll();

        assertThat(ana.completed).isTrue();
        assertThat(bruno.completed).isTrue();
        assertThat(registry.connectionCount(GROUP)).isZero();
        assertThat(registry.connectionCount(OTHER_GROUP)).isZero();
    }

    @Test
    void eventsForGroupsWithoutConnectionsAreIgnored() {
        registry.onParticipantsChanged(new ParticipantsChangedEvent(GROUP, GroupStatus.OPEN, 1));

        assertThat(registry.connectionCount(GROUP)).isZero();
    }
}
