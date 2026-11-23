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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import br.com.amigosecreto.events.GroupEventsController;
import br.com.amigosecreto.events.SseEmitterRegistry;
import br.com.amigosecreto.group.ConfirmationStatus;
import br.com.amigosecreto.group.GroupConfirmationService;
import br.com.amigosecreto.group.GroupController;
import br.com.amigosecreto.group.GroupMembershipService;
import br.com.amigosecreto.group.GroupService;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.dto.ConfirmationRequest;
import br.com.amigosecreto.group.dto.EventDetailsResponse;
import br.com.amigosecreto.group.dto.GroupDetail;
import br.com.amigosecreto.group.dto.GroupSummary;
import br.com.amigosecreto.group.dto.Participant;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
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
    private static final String VALID_CREATE =
            "{\"name\":\"Natal 2026\",\"eventDate\":\"2026-12-19\",\"eventTime\":\"19:00\",\"location\":\"Casa\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private SseEmitterRegistry registry;

    @MockitoBean
    private GroupMembershipService membershipService;

    @MockitoBean
    private GroupConfirmationService confirmationService;

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
                List.of(new Participant(ANA.id(), "Ana", true, true)),
                1,
                100,
                true,
                false,
                new EventDetailsResponse("Traga um prato", LocalDate.of(2026, 12, 19), "19:00", "Casa da Ana"),
                false,
                false,
                1,
                1,
                ConfirmationStatus.CONFIRMED,
                "not-enough-participants",
                null);
    }

    /** O corpo válido com um campo trocado ({@code null} remove o campo). */
    private static String createWith(String field, String jsonValue) {
        String base = VALID_CREATE.substring(1, VALID_CREATE.length() - 1);
        StringBuilder body = new StringBuilder("{");
        for (String pair : base.split(",")) {
            String name = pair.substring(1, pair.indexOf('"', 1));
            if (!name.equals(field)) {
                body.append(body.length() > 1 ? "," : "").append(pair);
            }
        }
        if (jsonValue != null) {
            body.append(",\"").append(field).append("\":").append(jsonValue);
        }
        return body.append("}").toString();
    }

    @Test
    void createWithBlankOrLongNameIs400() throws Exception {
        mvc.perform(postJson("/api/v1/groups", createWith("name", "\"   \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Dê um nome ao grupo (até 60 caracteres)."));
        mvc.perform(postJson("/api/v1/groups", createWith("name", "\"" + "x".repeat(61) + "\"")))
                .andExpect(status().isBadRequest());
        verify(groupService, never()).create(any(), any());
    }

    @Test
    void createWithoutEventDateOrLocationIs400() throws Exception {
        mvc.perform(postJson("/api/v1/groups", createWith("eventDate", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("eventDate"))
                .andExpect(jsonPath("$.errors[0].message").value("Informe a data do evento."));
        mvc.perform(postJson("/api/v1/groups", createWith("location", "\"  \"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("location"))
                .andExpect(jsonPath("$.errors[0].message").value("Informe o local do evento (até 120 caracteres)."));
        mvc.perform(postJson("/api/v1/groups", createWith("location", "\"" + "x".repeat(121) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("location"));
        verify(groupService, never()).create(any(), any());
    }

    @Test
    void createWithLongDescriptionOrBadTimeIs400() throws Exception {
        mvc.perform(postJson("/api/v1/groups", createWith("description", "\"" + "x".repeat(501) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"))
                .andExpect(jsonPath("$.errors[0].message").value("A descrição pode ter até 500 caracteres."));
        for (String time : new String[] {"\"7pm\"", "\"24:00\"", "\"19:60\"", "\"9:00\""}) {
            mvc.perform(postJson("/api/v1/groups", createWith("eventTime", time)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("eventTime"))
                    .andExpect(jsonPath("$.errors[0].message").value("Informe o horário no formato HH:mm."));
        }
        mvc.perform(postJson("/api/v1/groups", createWith("eventDate", "\"20/12/2026\"")))
                .andExpect(status().isBadRequest());
        verify(groupService, never()).create(any(), any());
    }

    @Test
    void createAcceptsAMissingTimeAndDescriptionOf500() throws Exception {
        when(groupService.create(eq(ANA.id()), any())).thenReturn(detail());

        mvc.perform(postJson("/api/v1/groups", createWith("eventTime", null))).andExpect(status().isCreated());
        mvc.perform(postJson("/api/v1/groups", createWith("description", "\"" + "x".repeat(500) + "\"")))
                .andExpect(status().isCreated());
    }

    @Test
    void updateEventDetailsReturnsTheGroup() throws Exception {
        when(groupService.updateEventDetails(eq(ANA.id()), eq(GROUP_ID), any())).thenReturn(detail());

        mvc.perform(put("/api/v1/groups/{id}/event-details", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventDate\":\"2026-12-19\",\"location\":\"Casa\"}")
                        .with(ana())
                        .with(spaCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventDetails.location").value("Casa da Ana"))
                .andExpect(jsonPath("$.eventDetails.eventDate").value("2026-12-19"))
                .andExpect(jsonPath("$.eventDetails.eventTime").value("19:00"))
                .andExpect(jsonPath("$.eventDetailsMissing").value(false))
                .andExpect(jsonPath("$.detailsVersion").value(1));
    }

    @Test
    void updateEventDetailsValidatesAndNeedsCsrf() throws Exception {
        mvc.perform(put("/api/v1/groups/{id}/event-details", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventDate\":\"2026-12-19\",\"location\":\"Casa\"}")
                        .with(ana()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/groups/{id}/event-details", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"Casa\"}")
                        .with(ana())
                        .with(spaCsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("eventDate"));
        verify(groupService, never()).updateEventDetails(any(), any(), any());
    }

    @Test
    void updateEventDetailsErrorsFollowTheContract() throws Exception {
        when(groupService.updateEventDetails(eq(ANA.id()), eq(GROUP_ID), any()))
                .thenThrow(new ForbiddenException("Só o organizador pode alterar os detalhes do evento."))
                .thenThrow(new ConflictException("group-finished", "Este grupo já foi finalizado."))
                .thenThrow(new UnprocessableException(
                        "event-date-in-past", "A data do evento precisa ser hoje ou no futuro."));
        var request = put("/api/v1/groups/{id}/event-details", GROUP_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventDate\":\"2026-12-19\",\"location\":\"Casa\"}")
                .with(ana())
                .with(spaCsrf());

        mvc.perform(request).andExpect(status().isForbidden());
        mvc.perform(request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/group-finished"));
        mvc.perform(request)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type").value("/problems/event-date-in-past"))
                .andExpect(jsonPath("$.detail").value("A data do evento precisa ser hoje ou no futuro."));
    }

    @Test
    void createReturns201WithLocationAndDetail() throws Exception {
        when(groupService.create(eq(ANA.id()), any())).thenReturn(detail());

        mvc.perform(postJson("/api/v1/groups", VALID_CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/groups/" + GROUP_ID))
                .andExpect(jsonPath("$.code").value("K7PQ-M2XA"))
                .andExpect(jsonPath("$.maxParticipants").value(100))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.drawnAt").isEmpty())
                .andExpect(jsonPath("$.participants[0].name").value("Ana"))
                .andExpect(jsonPath("$.participants[0].organizer").value(true))
                .andExpect(jsonPath("$.canDraw").value(false))
                .andExpect(jsonPath("$.eventDetails.description").value("Traga um prato"))
                .andExpect(jsonPath("$.eventDetailsMissing").value(false));
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
                .thenReturn(List.of(new GroupSummary(
                        GROUP_ID, "Natal", GroupStatus.OPEN, 4, true, LocalDate.of(2026, 12, 19), null)));

        mvc.perform(get("/api/v1/groups").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(GROUP_ID.toString()))
                .andExpect(jsonPath("$[0].participantCount").value(4))
                .andExpect(jsonPath("$[0].organizer").value(true))
                .andExpect(jsonPath("$[0].eventDate").value("2026-12-19"))
                .andExpect(jsonPath("$[0].eventTime").isEmpty());
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

    // ---- confirmação (US2 da 002) ----

    @Test
    void confirmUsesTheSessionUserAndReturnsTheGroup() throws Exception {
        when(confirmationService.confirm(ANA.id(), GROUP_ID, new ConfirmationRequest(2)))
                .thenReturn(detail());

        mvc.perform(postJson("/api/v1/groups/" + GROUP_ID + "/confirmation", "{\"detailsVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].confirmed").value(true))
                .andExpect(jsonPath("$.confirmedCount").value(1))
                .andExpect(jsonPath("$.myConfirmation").value("CONFIRMED"))
                .andExpect(jsonPath("$.drawBlockedReason").value("not-enough-participants"));
        verify(confirmationService).confirm(ANA.id(), GROUP_ID, new ConfirmationRequest(2));
    }

    @Test
    void confirmWithoutAValidVersionIs400() throws Exception {
        String path = "/api/v1/groups/" + GROUP_ID + "/confirmation";
        mvc.perform(postJson(path, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("detailsVersion"));
        mvc.perform(postJson(path, "{\"detailsVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("detailsVersion"));
        mvc.perform(post(path).with(ana()).with(spaCsrf())).andExpect(status().isBadRequest());
        verify(confirmationService, never()).confirm(any(), any(), any());
    }

    @Test
    void confirmRequiresXsrf() throws Exception {
        mvc.perform(post("/api/v1/groups/{id}/confirmation", GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detailsVersion\":1}")
                        .with(ana()))
                .andExpect(status().isForbidden());
        verify(confirmationService, never()).confirm(any(), any(), any());
    }

    @Test
    void detailsChangedIs409() throws Exception {
        when(confirmationService.confirm(ANA.id(), GROUP_ID, new ConfirmationRequest(1)))
                .thenThrow(new ConflictException(
                        "details-changed", "Os detalhes do evento mudaram. Confira e confirme de novo."));

        mvc.perform(postJson("/api/v1/groups/" + GROUP_ID + "/confirmation", "{\"detailsVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/details-changed"))
                .andExpect(jsonPath("$.detail").value("Os detalhes do evento mudaram. Confira e confirme de novo."));
    }
}
