package br.com.amigosecreto.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GroupCodeGeneratorTest {

    private final GroupCodeGenerator generator = new GroupCodeGenerator(new SecureRandom());

    @Test
    void generatesEightCharactersFromTheUnambiguousAlphabet() {
        for (int i = 0; i < 1_000; i++) {
            String code = generator.generate();

            assertThat(code).hasSize(8).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}");
            assertThat(code).doesNotContain("0", "O", "1", "I", "L");
        }
    }

    @Test
    void allThirtyOneSymbolsShowUpIn10000Codes() {
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            for (char c : generator.generate().toCharArray()) {
                seen.add(c);
            }
        }

        assertThat(seen).hasSize(31);
        assertThat(GroupCodeGenerator.ALPHABET).hasSize(31);
    }

    @Test
    void usesTheInjectedSecureRandom() {
        SecureRandom alwaysFirst = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };

        assertThat(new GroupCodeGenerator(alwaysFirst).generate()).isEqualTo("AAAAAAAA");
    }

    @Test
    void formatsWithAHyphenInTheMiddle() {
        assertThat(GroupCodeGenerator.format("K7PQM2XA")).isEqualTo("K7PQ-M2XA");
    }

    @Test
    void normalizesCaseSpacesAndHyphen() {
        assertThat(GroupCodeGenerator.normalize(" k7pq-m2xa ")).contains("K7PQM2XA");
        assertThat(GroupCodeGenerator.normalize("K7PQ M2XA")).contains("K7PQM2XA");
        assertThat(GroupCodeGenerator.normalize("K7PQM2XA")).contains("K7PQM2XA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "K7PQM2X", "K7PQM2XAB", "K7PQ-M2X0", "K7PQ-M2XI", "K7PQ_M2XA", "K7PQ-M2XÁ"})
    void invalidInputNormalizesToEmpty(String input) {
        assertThat(GroupCodeGenerator.normalize(input)).isEmpty();
    }

    @Test
    void nullNormalizesToEmpty() {
        assertThat(GroupCodeGenerator.normalize(null)).isEmpty();
    }
}
