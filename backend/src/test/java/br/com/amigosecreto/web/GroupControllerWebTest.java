package br.com.amigosecreto.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import br.com.amigosecreto.config.SecurityConfig;
import br.com.amigosecreto.events.GroupEventsController;
import br.com.amigosecreto.events.SseEmitterRegistry;
import br.com.amigosecreto.group.GroupController;
import br.com.amigosecreto.group.GroupMembershipService;
import br.com.amigosecreto.group.GroupService;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.dto.GroupDetail;
import br.com.amigosecreto.group.dto.GroupSummary;
import br.com.amigosecreto.group.dto.Participant;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {GroupController.class, GroupEventsController.class})
@Import({SecurityConfig.class, ProblemResponseWriter.class, CurrentUser.class})
class GroupControllerWebTest {

    private static final AuthenticatedUser ANA = new AuthenticatedUser(UUID.randomUUID(), "Ana", "ana@x.com");
    private static final UUID GROUP_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private SseEmitterRegistry registry;

    @MockitoBean
    private GroupMembershipService membershipService;

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

    private static MockHttpServletRequestBuilder postJson(String path, String json) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .with(ana())
                .with(spaCsrf());
    }

    private static GroupDetail detail() {
        return new GroupDetail(
                GROUP_ID,
                "Natal 2026",
                "K7PQ-M2XA",
                GroupStatus.OPEN,
                null,
                List.of(new Participant(ANA.id(), "Ana", true)),
                1,
                100,
                true,
                false);
    }

    @Test
    void createWithBlankOrLongNameIs400() throws Exception {
        mvc.perform(postJson("/api/v1/groups", "{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Dê um nome ao grupo (até 60 caracteres)."));
        mvc.perform(postJson("/api/v1/groups", "{\"name\":\"" + "x".repeat(61) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(groupService, never()).create(any(), any());
    }

    @Test
    void createReturns201WithLocationAndDetail() throws Exception {
        when(groupService.create(eq(ANA.id()), any())).thenReturn(detail());

        mvc.perform(postJson("/api/v1/groups", "{\"name\":\"Natal 2026\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/groups/" + GROUP_ID))
                .andExpect(jsonPath("$.code").value("K7PQ-M2XA"))
                .andExpect(jsonPath("$.maxParticipants").value(100))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.drawnAt").isEmpty())
                .andExpect(jsonPath("$.participants[0].name").value("Ana"))
                .andExpect(jsonPath("$.participants[0].organizer").value(true))
                .andExpect(jsonPath("$.canDraw").value(false));
    }

    @Test
    void createRequiresSession() throws Exception {
        mvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Natal\"}")
                        .with(spaCsrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsSummaries() throws Exception {
        when(groupService.listMine(ANA.id()))
                .thenReturn(List.of(new GroupSummary(GROUP_ID, "Natal", GroupStatus.OPEN, 4, true)));

        mvc.perform(get("/api/v1/groups").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$[0].participantCount").value(4))
                .andExpect(jsonPath("$[0].organizer").value(true));
    }

    @Test
    void joinReturns200WithTheGroup() throws Exception {
        when(groupService.join(eq(ANA.id()), any())).thenReturn(detail());

        mvc.perform(postJson("/api/v1/groups/join", "{\"code\":\"k7pq-m2xa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(GROUP_ID.toString()));
    }

    @Test
    void joinWithBlankCodeIs400() throws Exception {
        mvc.perform(postJson("/api/v1/groups/join", "{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Código inválido."));
    }

    @Test
    void joinErrorsFollowTheContract() throws Exception {
        when(groupService.join(eq(ANA.id()), any()))
                .thenThrow(new NotFoundException("Código inválido."))
                .thenThrow(new ConflictException("group-already-drawn", "O sorteio deste grupo já foi realizado."))
                .thenThrow(new ConflictException("group-full", "Este grupo está cheio."))
                .thenThrow(new RateLimitExceededException(600));
        String body = "{\"code\":\"K7PQM2XA\"}";

        mvc.perform(postJson("/api/v1/groups/join", body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Código inválido."));
        mvc.perform(postJson("/api/v1/groups/join", body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/group-already-drawn"));
        mvc.perform(postJson("/api/v1/groups/join", body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/group-full"));
        mvc.perform(postJson("/api/v1/groups/join", body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"));
    }

    @Test
    void getDetailOfMember() throws Exception {
        when(groupService.getDetail(ANA.id(), GROUP_ID)).thenReturn(detail());

        mvc.perform(get("/api/v1/groups/{id}", GROUP_ID).with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Natal 2026"));
    }

    @Test
    void getDetailOfNonMemberIs404() throws Exception {
        when(groupService.getDetail(ANA.id(), GROUP_ID)).thenThrow(new NotFoundException("Grupo não encontrado."));

        mvc.perform(get("/api/v1/groups/{id}", GROUP_ID).with(ana()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Grupo não encontrado."));
    }

    @Test
    void eventsOfNonMemberIs404AndNeverRegisters() throws Exception {
        doThrow(new NotFoundException("Grupo não encontrado."))
                .when(groupService)
                .requireMember(ANA.id(), GROUP_ID);

        mvc.perform(get("/api/v1/groups/{id}/events", GROUP_ID)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .with(ana()))
                .andExpect(status().isNotFound());
        verify(registry, never()).register(any(), any());
    }

    @Test
    void eventsOfMemberRegistersTheSessionUser() throws Exception {
        when(registry.register(GROUP_ID, ANA.id()))
                .thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());

        mvc.perform(get("/api/v1/groups/{id}/events", GROUP_ID)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .with(ana()))
                .andExpect(status().isOk());
        verify(registry).register(GROUP_ID, ANA.id());
    }

    @Test
    void leaveIs204ForTheSessionUser() throws Exception {
        mvc.perform(delete("/api/v1/groups/{id}/participants/me", GROUP_ID)
                        .with(ana())
                        .with(spaCsrf()))
                .andExpect(status().isNoContent());
        verify(membershipService).leave(ANA.id(), GROUP_ID);
    }

    @Test
    void removeIs204AndUsesTheSessionUserAsTheOrganizer() throws Exception {
        UUID carla = UUID.randomUUID();

        mvc.perform(delete("/api/v1/groups/{id}/participants/{userId}", GROUP_ID, carla)
                        .with(ana())
                        .with(spaCsrf()))
                .andExpect(status().isNoContent());
        verify(membershipService).remove(ANA.id(), GROUP_ID, carla);
    }

    @Test
    void membershipErrorsFollowTheContract() throws Exception {
        UUID carla = UUID.randomUUID();
        doThrow(new ForbiddenException("Só o organizador pode remover participantes."))
                .when(membershipService)
                .remove(ANA.id(), GROUP_ID, carla);
        doThrow(new ConflictException("group-already-drawn", "O sorteio deste grupo já foi realizado."))
                .when(membershipService)
                .leave(ANA.id(), GROUP_ID);

        mvc.perform(delete("/api/v1/groups/{id}/participants/{userId}", GROUP_ID, carla)
                        .with(ana())
                        .with(spaCsrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/groups/{id}/participants/me", GROUP_ID)
                        .with(ana())
                        .with(spaCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/group-already-drawn"));
    }

    @Test
    void membershipChangesRequireXsrf() throws Exception {
        mvc.perform(delete("/api/v1/groups/{id}/participants/me", GROUP_ID).with(ana()))
                .andExpect(status().isForbidden());
        verify(membershipService, never()).leave(any(), any());
    }
}
