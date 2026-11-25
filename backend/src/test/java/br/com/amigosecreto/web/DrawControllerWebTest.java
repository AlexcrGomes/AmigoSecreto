package br.com.amigosecreto.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.amigosecreto.auth.AuthenticatedUser;
import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.ForbiddenException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.common.ProblemResponseWriter;
import br.com.amigosecreto.common.RateLimitExceededException;
import br.com.amigosecreto.common.UnprocessableException;
import br.com.amigosecreto.config.SecurityConfig;
import br.com.amigosecreto.draw.DrawController;
import br.com.amigosecreto.draw.DrawService;
import br.com.amigosecreto.draw.SecretFriendService;
import br.com.amigosecreto.draw.dto.DrawResult;
import br.com.amigosecreto.draw.dto.SecretFriendResponse;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.wishlist.dto.FriendWishItem;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = DrawController.class)
@Import({SecurityConfig.class, ProblemResponseWriter.class, CurrentUser.class})
class DrawControllerWebTest {

    private static final AuthenticatedUser ANA = new AuthenticatedUser(UUID.randomUUID(), "Ana", "ana@x.com");
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-12-20T15:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DrawService drawService;

    @MockitoBean
    private SecretFriendService secretFriendService;

    private static RequestPostProcessor ana() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(ANA, null, List.of()));
    }

    private static RequestPostProcessor spaCsrf() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", "token-de-teste"));
            request.addHeader("X-XSRF-TOKEN", "token-de-teste");
            return request;
        };
    }

    @Test
    void drawReturnsExactlyGroupIdStatusAndDrawnAt() throws Exception {
        when(drawService.draw(ANA.id(), GROUP_ID)).thenReturn(new DrawResult(GROUP_ID, GroupStatus.DRAWN, NOW));

        mvc.perform(post("/api/v1/groups/{id}/draw", GROUP_ID).with(ana()).with(spaCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.groupId").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$.status").value("DRAWN"))
                .andExpect(jsonPath("$.drawnAt").value("2026-12-20T15:00:00Z"));
    }

    @Test
    void drawErrorsFollowTheContract() throws Exception {
        when(drawService.draw(ANA.id(), GROUP_ID))
                .thenThrow(new ForbiddenException("Só o organizador pode realizar o sorteio."))
                .thenThrow(new NotFoundException("Grupo não encontrado."))
                .thenThrow(new ConflictException("group-already-drawn", "O sorteio deste grupo já foi realizado."))
                .thenThrow(new UnprocessableException(
                        "not-enough-participants", "São necessários pelo menos 3 participantes."))
                .thenThrow(new RateLimitExceededException(60));

        mvc.perform(post("/api/v1/groups/{id}/draw", GROUP_ID).with(ana()).with(spaCsrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/groups/{id}/draw", GROUP_ID).with(ana()).with(spaCsrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/groups/{id}/draw", GROUP_ID).with(ana()).with(spaCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/group-already-drawn"));
        mvc.perform(post("/api/v1/groups/{id}/draw", GROUP_ID).with(ana()).with(spaCsrf()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type").value("/problems/not-enough-participants"));
        mvc.perform(post("/api/v1/groups/{id}/draw", GROUP_ID).with(ana()).with(spaCsrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void pendingParticipantsAreListedInThe422() throws Exception {
        when(drawService.draw(ANA.id(), GROUP_ID))
                .thenThrow(new UnprocessableException(
                        "participants-pending",
                        "Faltam 2 confirmar: Bruno, Carla.",
                        Map.of("pending", List.of("Bruno", "Carla"))));

        mvc.perform(post("/api/v1/groups/{id}/draw", GROUP_ID).with(ana()).with(spaCsrf()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type").value("/problems/participants-pending"))
                .andExpect(jsonPath("$.detail").value("Faltam 2 confirmar: Bruno, Carla."))
                .andExpect(jsonPath("$.pending[0]").value("Bruno"))
                .andExpect(jsonPath("$.pending[1]").value("Carla"));
    }

    @Test
    void drawRequiresXsrf() throws Exception {
        mvc.perform(post("/api/v1/groups/{id}/draw", GROUP_ID).with(ana())).andExpect(status().isForbidden());
    }

    @Test
    void mySecretFriendReturnsTheNameAndTheirWishlistWithoutIdsAndNoStore() throws Exception {
        when(secretFriendService.getMine(ANA.id(), GROUP_ID))
                .thenReturn(new SecretFriendResponse(
                        "Carla",
                        List.of(
                                new FriendWishItem("Livro", "https://loja.com/livro"),
                                new FriendWishItem("Caneca", null)),
                        Instant.parse("2026-12-21T15:00:00Z")));

        mvc.perform(get("/api/v1/groups/{id}/my-secret-friend", GROUP_ID).with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.name").value("Carla"))
                .andExpect(jsonPath("$.wishlist[0].description").value("Livro"))
                .andExpect(jsonPath("$.wishlist[0].url").value("https://loja.com/livro"))
                .andExpect(jsonPath("$.wishlist[0]", aMapWithSize(2)))
                .andExpect(jsonPath("$.wishlist[1].url").value(nullValue()))
                .andExpect(jsonPath("$.wishlistUpdatedAt").value("2026-12-21T15:00:00Z"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void mySecretFriendIgnoresAnyUserParameter() throws Exception {
        when(secretFriendService.getMine(any(), any())).thenReturn(new SecretFriendResponse("Carla"));

        mvc.perform(get("/api/v1/groups/{id}/my-secret-friend", GROUP_ID)
                        .param("userId", UUID.randomUUID().toString())
                        .param("giverId", UUID.randomUUID().toString())
                        .with(ana()))
                .andExpect(status().isOk());
        verify(secretFriendService).getMine(ANA.id(), GROUP_ID);
    }

    @Test
    void mySecretFriendBeforeTheDrawIs409() throws Exception {
        when(secretFriendService.getMine(ANA.id(), GROUP_ID))
                .thenThrow(new ConflictException("group-not-drawn", "O sorteio deste grupo ainda não foi realizado."));

        mvc.perform(get("/api/v1/groups/{id}/my-secret-friend", GROUP_ID).with(ana()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/group-not-drawn"));
    }

    @Test
    void mySecretFriendRequiresSession() throws Exception {
        mvc.perform(get("/api/v1/groups/{id}/my-secret-friend", GROUP_ID)).andExpect(status().isUnauthorized());
    }
}
