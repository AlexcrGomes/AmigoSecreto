package br.com.amigosecreto.group;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Código do grupo (research R5): 8 símbolos de um alfabeto de 31, sem os ambíguos {@code 0 O 1 I L},
 * gerados com {@link SecureRandom} (~39,6 bits). Guardado sem hífen; exibido como {@code XXXX-XXXX}.
 */
@Component
public class GroupCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 8;

    private final SecureRandom random;

    public GroupCodeGenerator(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** {@code K7PQM2XA} → {@code K7PQ-M2XA}. */
    public static String format(String code) {
        return code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2);
    }

    /**
     * Entrada do usuário → código guardado: maiúsculas, sem espaços e sem hífen. Vazio se o resultado não
     * tiver 8 símbolos do alfabeto.
     */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String code = input.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        if (code.length() != LENGTH) {
            return Optional.empty();
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(code);
    }
}
