package br.com.amigosecreto.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import br.com.amigosecreto.wishlist.WishlistController;
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

/** As rotas da própria lista de desejos (contracts/openapi.yaml da 003, tag wishlist). */
@WebMvcTest(controllers = WishlistController.class)
@Import({SecurityConfig.class, ProblemResponseWriter.class, CurrentUser.class})
class WishlistControllerWebTest {

    private static final AuthenticatedUser BRUNO = new AuthenticatedUser(UUID.randomUUID(), "Bruno", "bruno@x.com");
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final String ITEMS = "/api/v1/groups/" + GROUP_ID + "/wishlist/mine/items";
    private static final MyWishlist LIST =
            new MyWishlist(List.of(new WishItemResponse(ITEM_ID, "Livro", "https://loja.com/livro")), 5, true);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WishlistService wishlistService;

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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(bruno())
                .with(spaCsrf());
    }

    @Test
    void addingAnswers201WithTheSessionUsersList() throws Exception {
        when(wishlistService.add(eq(BRUNO.id()), eq(GROUP_ID), any())).thenReturn(LIST);

        mvc.perform(json(post(ITEMS), "{\"description\":\"Livro\",\"url\":\"https://loja.com/livro\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].id").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.items[0].description").value("Livro"))
                .andExpect(jsonPath("$.items[0].url").value("https://loja.com/livro"))
                .andExpect(jsonPath("$.maxItems").value(5))
                .andExpect(jsonPath("$.editable").value(true));
        verify(wishlistService).add(BRUNO.id(), GROUP_ID, new WishItemRequest("Livro", "https://loja.com/livro"));
    }

    @Test
    void updatingAnswers200AndRemovingAnswers204() throws Exception {
        when(wishlistService.update(eq(BRUNO.id()), eq(GROUP_ID), eq(ITEM_ID), any()))
                .thenReturn(LIST);

        mvc.perform(json(put(ITEMS + "/" + ITEM_ID), "{\"description\":\"Livro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].description").value("Livro"));
        mvc.perform(delete(ITEMS + "/" + ITEM_ID).with(bruno()).with(spaCsrf())).andExpect(status().isNoContent());
        verify(wishlistService).remove(BRUNO.id(), GROUP_ID, ITEM_ID);
    }

    @Test
    void anInvalidItemIs400WithTheFieldErrors() throws Exception {
        mvc.perform(json(post(ITEMS), "{\"description\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"))
                .andExpect(jsonPath("$.errors[0].message").value("Descreva a sugestão (até 120 caracteres)."));
        mvc.perform(json(post(ITEMS), "{\"description\":\"" + "a".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        mvc.perform(json(post(ITEMS), "{\"description\":\"Livro\",\"url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"))
                .andExpect(
                        jsonPath("$.errors[0].message").value("Informe um link que comece com http:// ou https://."));
        verifyNoInteractions(wishlistService);
    }

    @Test
    void serviceErrorsComeAsProblems() throws Exception {
        when(wishlistService.add(eq(BRUNO.id()), eq(GROUP_ID), any()))
                .thenThrow(new ConflictException("wishlist-full", "A lista tem no máximo 5 sugestões."));
        when(wishlistService.update(eq(BRUNO.id()), eq(GROUP_ID), eq(ITEM_ID), any()))
                .thenThrow(new NotFoundException("Sugestão não encontrada."));

        mvc.perform(json(post(ITEMS), "{\"description\":\"Livro\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/problems/wishlist-full"));
        mvc.perform(json(put(ITEMS + "/" + ITEM_ID), "{\"description\":\"Livro\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void everyRouteRequiresSessionAndXsrf() throws Exception {
        String body = "{\"description\":\"Livro\"}";
        mvc.perform(post(ITEMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(bruno()))
                .andExpect(status().isForbidden());
        mvc.perform(put(ITEMS + "/" + ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(bruno()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(ITEMS + "/" + ITEM_ID).with(bruno())).andExpect(status().isForbidden());
        mvc.perform(post(ITEMS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(spaCsrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(wishlistService);
    }
}
