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
import br.com.amigosecreto.wishlist.DependentWishlistController;
import br.com.amigosecreto.wishlist.WishlistService;
import br.com.amigosecreto.wishlist.dto.MyWishlist;
import br.com.amigosecreto.wishlist.dto.WishItemRequest;
import br.com.amigosecreto.wishlist.dto.WishItemResponse;
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

@WebMvcTest(controllers = DependentWishlistController.class)
@Import({SecurityConfig.class, ProblemResponseWriter.class, CurrentUser.class})
class DependentWishlistControllerWebTest {

    private static final AuthenticatedUser ANA = new AuthenticatedUser(UUID.randomUUID(), "Ana", "ana@x.com");
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID LUCAS = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final String ITEMS = "/api/v1/groups/" + GROUP_ID + "/my-dependents/" + LUCAS + "/wishlist/items";
    private static final MyWishlist LIST =
            new MyWishlist(List.of(new WishItemResponse(ITEM_ID, "Bola", null)), 5, true);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WishlistService wishlistService;

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
    void theGuardianAddsUpdatesAndRemovesItemsOfTheDependentsList() throws Exception {
        when(wishlistService.addForDependent(eq(ANA.id()), eq(GROUP_ID), eq(LUCAS), any()))
                .thenReturn(LIST);
        when(wishlistService.updateForDependent(eq(ANA.id()), eq(GROUP_ID), eq(LUCAS), eq(ITEM_ID), any()))
                .thenReturn(LIST);

        mvc.perform(json(post(ITEMS), "{\"description\":\"Bola\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].description").value("Bola"))
                .andExpect(jsonPath("$.maxItems").value(5));
        mvc.perform(json(put(ITEMS + "/" + ITEM_ID), "{\"description\":\"Bola\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete(ITEMS + "/" + ITEM_ID).with(ana()).with(spaCsrf())).andExpect(status().isNoContent());

        verify(wishlistService).addForDependent(ANA.id(), GROUP_ID, LUCAS, new WishItemRequest("Bola", null));
        verify(wishlistService).removeForDependent(ANA.id(), GROUP_ID, LUCAS, ITEM_ID);
    }

    @Test
    void validationAndServiceErrorsFollowTheContract() throws Exception {
        mvc.perform(json(post(ITEMS), "{\"description\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));

        when(wishlistService.addForDependent(eq(ANA.id()), eq(GROUP_ID), eq(LUCAS), any()))
                .thenThrow(new ConflictException("wishlist-full", "A lista tem no máximo 5 sugestões."));
        doThrow(new NotFoundException("Dependente não encontrado."))
                .when(wishlistService)
                .removeForDependent(ANA.id(), GROUP_ID, LUCAS, ITEM_ID);

        mvc.perform(json(post(ITEMS), "{\"description\":\"Bola\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/wishlist-full"));
        mvc.perform(delete(ITEMS + "/" + ITEM_ID).with(ana()).with(spaCsrf())).andExpect(status().isNotFound());
    }

    @Test
    void everyRouteRequiresSessionAndXsrf() throws Exception {
        String body = "{\"description\":\"Bola\"}";
        mvc.perform(post(ITEMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(spaCsrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ITEMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(ana()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(ITEMS + "/" + ITEM_ID).with(ana())).andExpect(status().isForbidden());
        verifyNoInteractions(wishlistService);
    }
}
