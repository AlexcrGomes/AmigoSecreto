package br.com.amigosecreto.events;

import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.group.GroupService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** {@code GET /api/v1/groups/{groupId}/events}: sinais em tempo real só para membros (404 para os demais). */
@RestController
public class GroupEventsController {

    private final GroupService groupService;
    private final SseEmitterRegistry registry;
    private final CurrentUser currentUser;

    public GroupEventsController(GroupService groupService, SseEmitterRegistry registry, CurrentUser currentUser) {
        this.groupService = groupService;
        this.registry = registry;
        this.currentUser = currentUser;
    }

    @GetMapping(path = "/api/v1/groups/{groupId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID groupId) {
        UUID userId = currentUser.id();
        groupService.requireMember(userId, groupId);
        return registry.register(groupId, userId);
    }
}
