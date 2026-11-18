package br.com.amigosecreto.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class MaxUtf8BytesValidatorTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    record Sample(@MaxUtf8Bytes(4) String value) {}

    @Test
    void countsBytesNotCharacters() {
        assertThat(validator.validate(new Sample("abcd"))).isEmpty();
        assertThat(validator.validate(new Sample("éé"))).isEmpty(); // 4 bytes
        assertThat(validator.validate(new Sample("ééa"))).hasSize(1); // 5 bytes
        assertThat(validator.validate(new Sample("😀a"))).hasSize(1); // 5 bytes
    }

    @Test
    void nullIsLeftToNotNull() {
        assertThat(validator.validate(new Sample(null))).isEmpty();
    }
}
