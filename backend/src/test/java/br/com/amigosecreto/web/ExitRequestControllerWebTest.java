package br.com.amigosecreto.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import br.com.amigosecreto.config.SecurityConfig;
import br.com.amigosecreto.exitrequest.ExitRequestController;
import br.com.amigosecreto.exitrequest.ExitRequestNoticeService;
import br.com.amigosecreto.exitrequest.ExitRequestService;
import br.com.amigosecreto.exitrequest.ExitRequestStatus;
import br.com.amigosecreto.exitrequest.dto.ExitRequestNotice;
import br.com.amigosecreto.exitrequest.dto.ExitRequestResponse;
import br.com.amigosecreto.group.ConfirmationStatus;
import br.com.amigosecreto.group.GroupStatus;
import br.com.amigosecreto.group.dto.EventDetailsResponse;
import br.com.amigosecreto.group.dto.GroupDetail;
import br.com.amigosecreto.group.dto.Participant;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** As seis rotas de pedido de saída (contracts/openapi.yaml da 002, tag exit-requests). */
@WebMvcTest(controllers = ExitRequestController.class)
@Import({SecurityConfig.class, ProblemResponseWriter.class, CurrentUser.class})
class ExitRequestControllerWebTest {

    private static final AuthenticatedUser BRUNO = new AuthenticatedUser(UUID.randomUUID(), "Bruno", "bruno@x.com");
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final String BASE = "/api/v1/groups/" + GROUP_ID + "/exit-requests";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ExitRequestService exitRequestService;

    @MockitoBean
    private ExitRequestNoticeService noticeService;

    private static RequestPostProcessor bruno() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(BRUNO, null, List.of()));
    }

    private static RequestPostProcessor spaCsrf() {
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", "token-de-teste"));
            request.addHeader("X-XSRF-TOKEN", "token-de-teste");
            return request;
        };
    }

    private static GroupDetail openDetail() {
        return new GroupDetail(
                GROUP_ID,
                "Natal 2026",
                "K7PQ-M2XA",
                GroupStatus.OPEN,
                null,
                List.of(new Participant(UUID.randomUUID(), "Ana", true, true)),
                3,
                100,
                true,
                true,
                new EventDetailsResponse(null, LocalDate.of(2026, 12, 19), "19:00", "Casa da Ana"),
                false,
                false,
                1,
                3,
                ConfirmationStatus.CONFIRMED,
                null,
                null,
                true,
                null,
                List.of());
    }

    private static void assertNoPairs(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString().toLowerCase(Locale.ROOT);
        for (String key : List.of("receiver", "secretfriend", "assignment", "giver")) {
            assertThat(body).doesNotContain(key);
        }
    }

    @Test
    void requestingAnswers201WithTheRequestOfTheSessionUser() throws Exception {
        when(exitRequestService.request(BRUNO.id(), GROUP_ID))
                .thenReturn(new ExitRequestResponse(REQUEST_ID, BRUNO.id(), "Bruno", NOW));

        MvcResult result = mvc.perform(post(BASE).with(bruno()).with(spaCsrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/" + REQUEST_ID))
                .andExpect(jsonPath("$.id").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.name").value("Bruno"))
                .andExpect(jsonPath("$.requestedAt").value("2026-12-01T12:00:00Z"))
                .andReturn();
        assertNoPairs(result);
    }

    @Test
    void conflictsComeAsProblems() throws Exception {
        when(exitRequestService.request(BRUNO.id(), GROUP_ID))
                .thenThrow(new ConflictException(
                        "exit-request-already-pending", "Você já tem um pedido de saída aguardando o organizador."));

        mvc.perform(post(BASE).with(bruno()).with(spaCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/exit-request-already-pending"));
    }

    @Test
    void cancellingAnswers204() throws Exception {
        mvc.perform(delete(BASE + "/mine").with(bruno()).with(spaCsrf())).andExpect(status().isNoContent());

        verify(exitRequestService).cancel(BRUNO.id(), GROUP_ID);
    }

    @Test
    void approvingAndRejectingReturnTheGroupWithoutPairs() throws Exception {
        when(exitRequestService.approve(BRUNO.id(), GROUP_ID, REQUEST_ID)).thenReturn(openDetail());
        when(exitRequestService.reject(BRUNO.id(), GROUP_ID, REQUEST_ID)).thenReturn(openDetail());

        MvcResult approved = mvc.perform(
                        post(BASE + "/" + REQUEST_ID + "/approve").with(bruno()).with(spaCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.drawUndone").value(true))
                .andExpect(jsonPath("$.pendingExitRequests").isArray())
                .andReturn();
        assertNoPairs(approved);
        mvc.perform(post(BASE + "/" + REQUEST_ID + "/reject").with(bruno()).with(spaCsrf()))
                .andExpect(status().isOk());
    }

    @Test
    void aNonOrganizerDecidingGets403AndARequestOfAnotherGroup404() throws Exception {
        when(exitRequestService.approve(BRUNO.id(), GROUP_ID, REQUEST_ID))
                .thenThrow(new ForbiddenException("Só o organizador pode decidir os pedidos de saída."));
        UUID otherRequest = UUID.randomUUID();
        when(exitRequestService.reject(BRUNO.id(), GROUP_ID, otherRequest))
                .thenThrow(new NotFoundException("Pedido de saída não encontrado."));

        mvc.perform(post(BASE + "/" + REQUEST_ID + "/approve").with(bruno()).with(spaCsrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + otherRequest + "/reject").with(bruno()).with(spaCsrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void noticesListTheSessionUsersDecisionsAndAckAnswers204() throws Exception {
        when(noticeService.unseen(BRUNO.id()))
                .thenReturn(List.of(
                        new ExitRequestNotice(REQUEST_ID, GROUP_ID, "Natal 2026", ExitRequestStatus.APPROVED, NOW)));

        MvcResult notices = mvc.perform(get("/api/v1/exit-request-notices").with(bruno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupName").value("Natal 2026"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].decidedAt").value("2026-12-01T12:00:00Z"))
                .andReturn();
        assertNoPairs(notices);

        mvc.perform(post("/api/v1/exit-request-notices/" + REQUEST_ID + "/ack")
                        .with(bruno())
                        .with(spaCsrf()))
                .andExpect(status().isNoContent());
        verify(noticeService).acknowledge(BRUNO.id(), REQUEST_ID);
    }

    @Test
    void everyWriteRequiresTheXsrfToken() throws Exception {
        mvc.perform(post(BASE).with(bruno())).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/mine").with(bruno())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + REQUEST_ID + "/approve").with(bruno())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + REQUEST_ID + "/reject").with(bruno())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/exit-request-notices/" + REQUEST_ID + "/ack").with(bruno()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(exitRequestService);
        verify(noticeService, never()).acknowledge(any(), any());
    }

    @Test
    void everyRouteRequiresASession() throws Exception {
        mvc.perform(get("/api/v1/exit-request-notices")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).with(spaCsrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/mine").with(spaCsrf())).andExpect(status().isUnauthorized());

        verifyNoInteractions(exitRequestService, noticeService);
    }
}
