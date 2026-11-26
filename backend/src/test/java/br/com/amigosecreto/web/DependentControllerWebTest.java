package br.com.amigosecreto.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.amigosecreto.auth.AuthenticatedUser;
import br.com.amigosecreto.auth.CurrentUser;
import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.NotFoundException;
import br.com.amigosecreto.common.ProblemResponseWriter;
import br.com.amigosecreto.config.SecurityConfig;
import br.com.amigosecreto.dependent.DependentController;
import br.com.amigosecreto.dependent.DependentService;
import br.com.amigosecreto.dependent.dto.DependentRequest;
import br.com.amigosecreto.dependent.dto.MyDependent;
import br.com.amigosecreto.wishlist.dto.MyWishlist;
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

@WebMvcTest(controllers = DependentController.class)
@Import({SecurityConfig.class, ProblemResponseWriter.class, CurrentUser.class})
class DependentControllerWebTest {

    private static final AuthenticatedUser ANA = new AuthenticatedUser(UUID.randomUUID(), "Ana", "ana@x.com");
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID LUCAS = UUID.randomUUID();
    private static final String DEPENDENTS = "/api/v1/groups/" + GROUP_ID + "/my-dependents";
    private static final MyDependent DEPENDENT =
            new MyDependent(LUCAS, "Lucas", true, MyWishlist.of(List.of(), true), null);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DependentService dependentService;

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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(ana())
                .with(spaCsrf());
    }

    @Test
    void addingAnswers201WithTheDependentAndNeverAResult() throws Exception {
        when(dependentService.add(eq(ANA.id()), eq(GROUP_ID), any())).thenReturn(DEPENDENT);

        mvc.perform(json(post(DEPENDENTS), "{\"name\":\"  Lucas \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(LUCAS.toString()))
                .andExpect(jsonPath("$.name").value("Lucas"))
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.wishlist.maxItems").value(5))
                .andExpect(jsonPath("$.exitRequest").doesNotExist())
                .andExpect(jsonPath("$.secretFriend").doesNotExist());
        verify(dependentService).add(ANA.id(), GROUP_ID, new DependentRequest("Lucas"));
    }

    @Test
    void renamingAnswers200AndRemovingAnswers204() throws Exception {
        when(dependentService.rename(eq(ANA.id()), eq(GROUP_ID), eq(LUCAS), any()))
                .thenReturn(DEPENDENT);

        mvc.perform(json(put(DEPENDENTS + "/" + LUCAS), "{\"name\":\"Lucas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lucas"));
        mvc.perform(delete(DEPENDENTS + "/" + LUCAS).with(ana()).with(spaCsrf()))
                .andExpect(status().isNoContent());
        verify(dependentService).remove(ANA.id(), GROUP_ID, LUCAS);
    }

    @Test
    void anInvalidNameIs400WithTheFieldError() throws Exception {
        for (String body :
                List.of("{\"name\":\" L \"}", "{\"name\":\"   \"}", "{}", "{\"name\":\"" + "a".repeat(81) + "\"}")) {
            mvc.perform(json(post(DEPENDENTS), body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"))
                    .andExpect(jsonPath("$.errors[0].message").value("Informe um nome de 2 a 80 caracteres."));
        }
        verifyNoInteractions(dependentService);
    }

    @Test
    void serviceErrorsComeAsProblems() throws Exception {
        when(dependentService.add(eq(ANA.id()), eq(GROUP_ID), any()))
                .thenThrow(new ConflictException("dependents-limit", "Você pode adicionar até 5 dependentes."));
        doThrow(new NotFoundException("Dependente não encontrado."))
                .when(dependentService)
                .remove(ANA.id(), GROUP_ID, LUCAS);

        mvc.perform(json(post(DEPENDENTS), "{\"name\":\"Lucas\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/dependents-limit"))
                .andExpect(jsonPath("$.detail").value("Você pode adicionar até 5 dependentes."));
        mvc.perform(delete(DEPENDENTS + "/" + LUCAS).with(ana()).with(spaCsrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void everyRouteRequiresSessionAndXsrf() throws Exception {
        String body = "{\"name\":\"Lucas\"}";
        mvc.perform(post(DEPENDENTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(spaCsrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(DEPENDENTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(ana()))
                .andExpect(status().isForbidden());
        mvc.perform(put(DEPENDENTS + "/" + LUCAS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(ana()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(DEPENDENTS + "/" + LUCAS).with(ana())).andExpect(status().isForbidden());
        verifyNoInteractions(dependentService);
    }
}
