package br.com.amigosecreto.group;

import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.group.dto.ConfirmationRequest;
import br.com.amigosecreto.group.dto.CreateGroupRequest;
import br.com.amigosecreto.group.dto.EventDetailsRequest;
import br.com.amigosecreto.group.dto.GroupDetail;
import br.com.amigosecreto.group.dto.GroupSummary;
import br.com.amigosecreto.group.dto.JoinGroupRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas de grupos (contracts/openapi.yaml, tag groups). O usuário vem sempre da sessão. */
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;
    private final GroupMembershipService membershipService;
    private final GroupConfirmationService confirmationService;
    private final CurrentUser currentUser;

    public GroupController(
            GroupService groupService,
            GroupMembershipService membershipService,
            GroupConfirmationService confirmationService,
            CurrentUser currentUser) {
        this.groupService = groupService;
        this.membershipService = membershipService;
        this.confirmationService = confirmationService;
        this.currentUser = currentUser;
    }

    /** O usuário da sessão sai do grupo (antes do sorteio). */
    @DeleteMapping("/{groupId}/participants/me")
    public ResponseEntity<Void> leave(@PathVariable UUID groupId) {
        membershipService.leave(currentUser.id(), groupId);
        return ResponseEntity.noContent().build();
    }

    /** O organizador (usuário da sessão) remove um participante (antes do sorteio). */
    @DeleteMapping("/{groupId}/participants/{userId}")
    public ResponseEntity<Void> remove(@PathVariable UUID groupId, @PathVariable UUID userId) {
        membershipService.remove(currentUser.id(), groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<GroupSummary> list() {
        return groupService.listMine(currentUser.id());
    }

    @PostMapping
    public ResponseEntity<GroupDetail> create(@Valid @RequestBody CreateGroupRequest body) {
        GroupDetail group = groupService.create(currentUser.id(), body);
        return ResponseEntity.created(URI.create("/api/v1/groups/" + group.id()))
                .body(group);
    }

    @PostMapping("/join")
    public GroupDetail join(@Valid @RequestBody JoinGroupRequest body) {
        return groupService.join(currentUser.id(), body);
    }

    /** O organizador (usuário da sessão) altera descrição, data, horário e local do evento. */
    @PutMapping("/{groupId}/event-details")
    public GroupDetail updateEventDetails(@PathVariable UUID groupId, @Valid @RequestBody EventDetailsRequest body) {
        return groupService.updateEventDetails(currentUser.id(), groupId, body);
    }

    /** O usuário da sessão confirma a própria participação, na versão dos detalhes que viu (FR-009, FR-035). */
    @PostMapping("/{groupId}/confirmation")
    public GroupDetail confirm(@PathVariable UUID groupId, @Valid @RequestBody ConfirmationRequest body) {
        return confirmationService.confirm(currentUser.id(), groupId, body);
    }

    @GetMapping("/{groupId}")
    public GroupDetail get(@PathVariable UUID groupId) {
        return groupService.getDetail(currentUser.id(), groupId);
    }
}
