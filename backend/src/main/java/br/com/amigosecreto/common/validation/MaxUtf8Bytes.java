package br.com.amigosecreto.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limite em bytes UTF-8 (não em caracteres). Usado na senha: o BCrypt considera só os primeiros 72
 * bytes, então uma senha maior teria uma parte ignorada em silêncio (research R3). {@code null} é válido.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Texto longo demais.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
