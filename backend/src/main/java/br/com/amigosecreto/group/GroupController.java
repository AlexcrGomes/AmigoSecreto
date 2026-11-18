package br.com.amigosecreto.group;

import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.group.dto.CreateGroupRequest;
import br.com.amigosecreto.group.dto.GroupDetail;
import br.com.amigosecreto.group.dto.GroupSummary;
import br.com.amigosecreto.group.dto.JoinGroupRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas de grupos (contracts/openapi.yaml, tag groups). O usuário vem sempre da sessão. */
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;
    private final CurrentUser currentUser;

    public GroupController(GroupService groupService, CurrentUser currentUser) {
        this.groupService = groupService;
        this.currentUser = currentUser;
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

    @GetMapping("/{groupId}")
    public GroupDetail get(@PathVariable UUID groupId) {
        return groupService.getDetail(currentUser.id(), groupId);
    }
}
