package br.com.amigosecreto.draw;

import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.draw.dto.DrawResult;
import br.com.amigosecreto.draw.dto.SecretFriendResponse;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas do sorteio (contracts/openapi.yaml, tag draw). O usuário vem SEMPRE da sessão. */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class DrawController {

    private final DrawService drawService;
    private final SecretFriendService secretFriendService;
    private final CurrentUser currentUser;

    public DrawController(DrawService drawService, SecretFriendService secretFriendService, CurrentUser currentUser) {
        this.drawService = drawService;
        this.secretFriendService = secretFriendService;
        this.currentUser = currentUser;
    }

    @PostMapping("/draw")
    public DrawResult draw(@PathVariable UUID groupId) {
        return drawService.draw(currentUser.id(), groupId);
    }

    /** Sem parâmetro de usuário: qualquer {@code ?userId=} é ignorado. Nunca em cache (no-store). */
    @GetMapping("/my-secret-friend")
    public ResponseEntity<SecretFriendResponse> mySecretFriend(@PathVariable UUID groupId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(secretFriendService.getMine(currentUser.id(), groupId));
    }
}
