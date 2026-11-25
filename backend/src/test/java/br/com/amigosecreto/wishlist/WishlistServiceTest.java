package br.com.amigosecreto.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.events.GroupEvents.GroupFinishedEvent;
import br.com.amigosecreto.exitrequest.ExitRequestRepository;
import br.com.amigosecreto.group.EventDetails;
import br.com.amigosecreto.group.EventSchedule;
import br.com.amigosecreto.group.GroupFinishedException;
import br.com.amigosecreto.group.GroupLock;
import br.com.amigosecreto.group.GroupMember;
import br.com.amigosecreto.group.GroupMemberId;
import br.com.amigosecreto.group.GroupMemberRepository;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.SecretGroup;
import br.com.amigosecreto.group.SecretGroupRepository;
import br.com.amigosecreto.wishlist.dto.MyWishlist;
import br.com.amigosecreto.wishlist.dto.WishItemRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** A própria lista de desejos (US1 da 003, FR-001 a FR-007, research R28). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WishlistServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final UUID ANA = UUID.randomUUID(); // organizadora
    private static final UUID BRUNO = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();

    @Mock
    private SecretGroupRepository groups;

    @Mock
    private GroupMemberRepository members;

    @Mock
    private WishItemRepository items;

    @Mock
    private ApplicationEventPublisher events;

    private SecretGroup group;
    private GroupMember bruno;
    private WishlistService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        group = new SecretGroup("Natal", "K7PQM2XA", ANA, NOW);
        group.updateEventDetails(
                new EventDetails(null, LocalDate.of(2026, 12, 19), LocalTime.of(19, 0), "Casa da Ana"),
                new EventSchedule(clock, ZoneId.of("America/Sao_Paulo")));
        bruno = new GroupMember(group.getId(), BRUNO, NOW);
        when(groups.findByIdForUpdate(group.getId())).thenReturn(Optional.of(group));
        when(members.findById(new GroupMemberId(group.getId(), BRUNO))).thenReturn(Optional.of(bruno));
        when(members.countByIdGroupId(group.getId())).thenReturn(3L);
        when(items.save(any(WishItem.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new WishlistService(
                new GroupLock(groups, members, mock(ExitRequestRepository.class), clock, events),
                members,
                items,
                clock);
    }

    private WishItem itemOf(UUID owner, String description) {
        WishItem item = new WishItem(group.getId(), owner, description, null, NOW.minusSeconds(60));
        when(items.findByIdAndGroupIdAndUserId(item.getId(), group.getId(), owner))
                .thenReturn(Optional.of(item));
        return item;
    }

    private void givenListOf(UUID owner, WishItem... list) {
        when(items.findByGroupIdAndUserIdOrderByCreatedAtAscIdAsc(group.getId(), owner))
                .thenReturn(List.of(list));
        when(items.countByGroupIdAndUserId(group.getId(), owner)).thenReturn((long) list.length);
    }

    @Test
    void addingSavesTheItemForTheSessionUserAndMarksTheListAsChanged() {
        WishItem existing = itemOf(BRUNO, "Livro");
        givenListOf(BRUNO, existing);

        service.add(BRUNO, group.getId(), new WishItemRequest("  Caneca  ", " https://loja.com/caneca "));

        ArgumentCaptor<WishItem> saved = ArgumentCaptor.forClass(WishItem.class);
        verify(items).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(BRUNO);
        assertThat(saved.getValue().getGroupId()).isEqualTo(group.getId());
        assertThat(saved.getValue().getDescription()).isEqualTo("Caneca");
        assertThat(saved.getValue().getUrl()).isEqualTo("https://loja.com/caneca");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(bruno.getWishlistUpdatedAt()).isEqualTo(NOW);
        verify(groups).findByIdForUpdate(group.getId());
    }

    @Test
    void returnsTheSessionUsersListInOrder() {
        WishItem livro = itemOf(BRUNO, "Livro");
        WishItem caneca = itemOf(BRUNO, "Caneca");
        givenListOf(BRUNO, livro, caneca);

        MyWishlist list = service.add(BRUNO, group.getId(), new WishItemRequest("Meia", null));

        assertThat(list.items()).extracting("description").containsExactly("Livro", "Caneca");
        assertThat(list.maxItems()).isEqualTo(5);
        assertThat(list.editable()).isTrue();
    }

    @Test
    void theSixthItemIsRefused() {
        givenListOf(
                BRUNO,
                itemOf(BRUNO, "1"),
                itemOf(BRUNO, "2"),
                itemOf(BRUNO, "3"),
                itemOf(BRUNO, "4"),
                itemOf(BRUNO, "5"));

        assertThatThrownBy(() -> service.add(BRUNO, group.getId(), new WishItemRequest("6", null)))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getType()).isEqualTo("wishlist-full");
                    assertThat(ex.getMessage()).isEqualTo("A lista tem no máximo 5 sugestões.");
                });
        verify(items, never()).save(any());
        assertThat(bruno.getWishlistUpdatedAt()).isNull();
    }

    @Test
    void updatingChangesOnlyTheSessionUsersItem() {
        WishItem livro = itemOf(BRUNO, "Livro");
        givenListOf(BRUNO, livro);

        service.update(BRUNO, group.getId(), livro.getId(), new WishItemRequest("Livro de receitas", "https://x.com"));

        assertThat(livro.getDescription()).isEqualTo("Livro de receitas");
        assertThat(livro.getUrl()).isEqualTo("https://x.com");
        assertThat(bruno.getWishlistUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void removingDeletesTheItemAndMarksTheListAsChanged() {
        WishItem livro = itemOf(BRUNO, "Livro");

        service.remove(BRUNO, group.getId(), livro.getId());

        verify(items).delete(livro);
        assertThat(bruno.getWishlistUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void someoneElsesItemOrAnUnknownOneIsTheSameNotFound() {
        WishItem carlas = itemOf(CARLA, "Perfume");
        when(members.findById(new GroupMemberId(group.getId(), CARLA)))
                .thenReturn(Optional.of(new GroupMember(group.getId(), CARLA, NOW)));

        assertThatThrownBy(() -> service.update(BRUNO, group.getId(), carlas.getId(), new WishItemRequest("x", null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Sugestão não encontrada.");
        assertThatThrownBy(() -> service.remove(BRUNO, group.getId(), carlas.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Sugestão não encontrada.");
        assertThatThrownBy(() -> service.remove(BRUNO, group.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Sugestão não encontrada.");
        assertThat(carlas.getDescription()).isEqualTo("Perfume");
        verify(items, never()).delete(any());
    }

    @Test
    void nonMembersAndUnknownGroupsGetNotFound() {
        UUID outsider = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.add(outsider, group.getId(), new WishItemRequest("x", null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Grupo não encontrado.");
        assertThatThrownBy(() -> service.add(BRUNO, unknown, new WishItemRequest("x", null)))
                .isInstanceOf(NotFoundException.class);
        verify(items, never()).save(any());
    }

    @Test
    void theListCanChangeAfterTheDraw() {
        group.markDrawn(NOW);
        givenListOf(BRUNO);

        MyWishlist list = service.add(BRUNO, group.getId(), new WishItemRequest("Livro", null));

        verify(items).save(any(WishItem.class));
        assertThat(list.editable()).isTrue();
    }

    @Test
    void aFinishedGroupIsReadOnly() {
        WishItem livro = itemOf(BRUNO, "Livro");
        group.markDrawn(NOW);
        ReflectionTestUtils.setField(group, "status", GroupStatus.FINISHED);

        assertThatThrownBy(() -> service.add(BRUNO, group.getId(), new WishItemRequest("x", null)))
                .isInstanceOf(GroupFinishedException.class);
        assertThatThrownBy(() -> service.update(BRUNO, group.getId(), livro.getId(), new WishItemRequest("x", null)))
                .isInstanceOf(GroupFinishedException.class);
        assertThatThrownBy(() -> service.remove(BRUNO, group.getId(), livro.getId()))
                .isInstanceOf(GroupFinishedException.class);
        verify(items, never()).save(any());
        verify(items, never()).delete(any());
        assertThat(livro.getDescription()).isEqualTo("Livro");
    }

    @Test
    void aDueGroupIsFinishedByTheAttemptAndRefused() {
        group.markDrawn(NOW);
        ReflectionTestUtils.setField(group, "finalizesAt", NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.add(BRUNO, group.getId(), new WishItemRequest("x", null)))
                .isInstanceOf(GroupFinishedException.class);

        assertThat(group.getStatus()).isEqualTo(GroupStatus.FINISHED);
        verify(events).publishEvent(new GroupFinishedEvent(group.getId(), 3));
    }

    @Test
    void editingTheListNeverPublishesASignal() {
        WishItem livro = itemOf(BRUNO, "Livro");
        givenListOf(BRUNO, livro);

        service.add(BRUNO, group.getId(), new WishItemRequest("Caneca", null));
        service.update(BRUNO, group.getId(), livro.getId(), new WishItemRequest("Livro 2", null));
        service.remove(BRUNO, group.getId(), livro.getId());

        verify(events, never()).publishEvent(any());
    }
}
