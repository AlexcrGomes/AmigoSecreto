package br.com.amigosecreto.exitrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.exitrequest.dto.ExitRequestNotice;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Avisos guardados da decisão sobre o pedido de saída (FR-026, research R20), e as regras da entidade. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExitRequestNoticeServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final UUID BRUNO = UUID.randomUUID();
    private static final UUID GROUP = UUID.randomUUID();

    @Mock
    private ExitRequestRepository exitRequests;

    private ExitRequestNoticeService serviceAt(Instant instant) {
        return new ExitRequestNoticeService(exitRequests, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private ExitRequest decided(ExitRequestStatus status) {
        ExitRequest request = new ExitRequest(GROUP, BRUNO, NOW.minusSeconds(7200));
        switch (status) {
            case APPROVED -> request.approve(NOW.minusSeconds(3600));
            case REJECTED -> request.reject(NOW.minusSeconds(3600));
            case CANCELLED -> request.cancel(NOW.minusSeconds(3600), true);
            case PENDING -> {}
        }
        when(exitRequests.findByIdAndRequestedBy(request.getId(), BRUNO)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void listsTheUnseenDecisionsOfTheSessionUser() {
        List<ExitRequestNotice> notices = List.of(new ExitRequestNotice(
                UUID.randomUUID(), GROUP, "Natal", ExitRequestStatus.APPROVED, NOW.minusSeconds(60)));
        when(exitRequests.findUnseenNotices(BRUNO)).thenReturn(notices);

        assertThat(serviceAt(NOW).unseen(BRUNO)).isEqualTo(notices);
    }

    @Test
    void acknowledgingMarksTheNoticeAsSeenOnlyOnce() {
        ExitRequest request = decided(ExitRequestStatus.REJECTED);

        serviceAt(NOW).acknowledge(BRUNO, request.getId());
        assertThat(request.getDecisionSeenAt()).isEqualTo(NOW);

        serviceAt(NOW.plusSeconds(3600)).acknowledge(BRUNO, request.getId());
        assertThat(request.getDecisionSeenAt()).isEqualTo(NOW);
    }

    @Test
    void someoneElsesRequestOrAPendingOneIsNotFound() {
        UUID someoneElses = UUID.randomUUID();
        when(exitRequests.findByIdAndRequestedBy(someoneElses, BRUNO)).thenReturn(Optional.empty());
        ExitRequest pending = decided(ExitRequestStatus.PENDING);

        assertThatThrownBy(() -> serviceAt(NOW).acknowledge(BRUNO, someoneElses))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> serviceAt(NOW).acknowledge(BRUNO, pending.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(pending.getDecisionSeenAt()).isNull();
    }

    // ---- entidade ----

    @Test
    void eachDecisionRecordsWhenAndWhetherThereIsANotice() {
        ExitRequest approved = decided(ExitRequestStatus.APPROVED);
        assertThat(approved.getStatus()).isEqualTo(ExitRequestStatus.APPROVED);
        assertThat(approved.getDecidedAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(approved.getDecisionSeenAt()).isNull();

        ExitRequest cancelledAutomatically = decided(ExitRequestStatus.CANCELLED);
        assertThat(cancelledAutomatically.getDecisionSeenAt()).isNull();

        ExitRequest cancelledByTheRequester = new ExitRequest(GROUP, BRUNO, NOW);
        cancelledByTheRequester.cancel(NOW, false);
        assertThat(cancelledByTheRequester.getDecisionSeenAt()).isEqualTo(NOW);
    }

    @Test
    void onlyAPendingRequestCanBeDecided() {
        ExitRequest request = decided(ExitRequestStatus.REJECTED);

        assertThatThrownBy(() -> request.approve(NOW))
                .isInstanceOfSatisfying(
                        ConflictException.class, ex -> assertThat(ex.getType()).isEqualTo("exit-request-not-pending"));
        assertThatThrownBy(() -> request.cancel(NOW, true)).isInstanceOf(ConflictException.class);
        assertThat(request.getStatus()).isEqualTo(ExitRequestStatus.REJECTED);
    }

    @Test
    void aPendingRequestIsNeverMarkedAsSeenAndToStringHasNoPersonalData() {
        ExitRequest request = new ExitRequest(GROUP, BRUNO, NOW);
        request.markSeen(NOW);

        assertThat(request.getDecisionSeenAt()).isNull();
        assertThat(request.isNew()).isTrue();
        assertThat(request.toString()).doesNotContain(BRUNO.toString()).contains("PENDING");

        GroupExitBan ban = new GroupExitBan(GROUP, BRUNO, NOW);
        assertThat(ban.getId()).isEqualTo(new GroupExitBanId(GROUP, BRUNO));
        assertThat(ban.getCreatedAt()).isEqualTo(NOW);
        assertThat(ban.isNew()).isTrue();
        ban.markNotNew();
        request.markNotNew();
        assertThat(ban.isNew()).isFalse();
        assertThat(request.isNew()).isFalse();
    }

    // ---- feature 004: o aviso do pedido de um dependente vai para o responsável (research R37) ----

    @Test
    void theGuardianSeesAndDismissesTheDependentsNotice() {
        UUID lucas = UUID.randomUUID();
        ExitRequest request = new ExitRequest(GROUP, lucas, BRUNO, NOW.minusSeconds(7200));
        request.approve(NOW.minusSeconds(3600));
        when(exitRequests.findByIdAndRequestedBy(request.getId(), BRUNO)).thenReturn(Optional.of(request));
        when(exitRequests.findUnseenNotices(BRUNO)).thenReturn(List.of());

        serviceAt(NOW).unseen(BRUNO);
        serviceAt(NOW).acknowledge(BRUNO, request.getId());

        verify(exitRequests).findUnseenNotices(BRUNO);
        assertThat(request.getDecisionSeenAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> serviceAt(NOW).acknowledge(lucas, request.getId()))
                .isInstanceOf(NotFoundException.class);
    }
}
