package br.com.amigosecreto.wishlist;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Regras do link de um item da lista (FR-003, research R29): endereço web absoluto, {@code http} ou
 * {@code https}, com host, até 500 caracteres. Só interpreta o texto — o sistema NUNCA acessa o link (sem
 * pré-visualização, sem SSRF). Vazio significa "sem link".
 */
public final class WishItemUrl {

    public static final int MAX_LENGTH = 500;
    private static final Set<String> SCHEMES = Set.of("http", "https");

    private WishItemUrl() {}

    /** O link sem espaços nas pontas, ou {@code null} se vazio. */
    public static String normalize(String raw) {
        return raw == null || raw.isBlank() ? null : raw.strip();
    }

    public static boolean isValid(String raw) {
        String link = normalize(raw);
        if (link == null) {
            return true;
        }
        if (link.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(link);
            return uri.isAbsolute()
                    && SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
