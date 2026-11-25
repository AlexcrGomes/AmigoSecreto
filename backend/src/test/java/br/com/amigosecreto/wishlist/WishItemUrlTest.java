package br.com.amigosecreto.wishlist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Link de um item da lista: só endereço web absoluto, http ou https (research R29 da 003). */
class WishItemUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://loja.com/p?id=1", "http://a.b", "HTTPS://Loja.com.br/x#y", "  https://loja.com  "})
    void acceptsAbsoluteWebLinks(String link) {
        assertThat(WishItemUrl.isValid(link)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "data:text/html,x",
                "ftp://loja.com",
                "//loja.com",
                "loja.com",
                "https://",
                "https://loja .com",
                "mailto:ana@ex.com"
            })
    void rejectsAnythingElse(String link) {
        assertThat(WishItemUrl.isValid(link)).isFalse();
    }

    @Test
    void rejectsLinksLongerThan500Characters() {
        String base = "https://loja.com/";
        assertThat(WishItemUrl.isValid(base + "a".repeat(500 - base.length()))).isTrue();
        assertThat(WishItemUrl.isValid(base + "a".repeat(501 - base.length()))).isFalse();
    }

    @Test
    void emptyMeansNoLink() {
        assertThat(WishItemUrl.isValid(null)).isTrue();
        assertThat(WishItemUrl.isValid("   ")).isTrue();
        assertThat(WishItemUrl.normalize("   ")).isNull();
        assertThat(WishItemUrl.normalize(null)).isNull();
        assertThat(WishItemUrl.normalize("  https://loja.com  ")).isEqualTo("https://loja.com");
    }
}
