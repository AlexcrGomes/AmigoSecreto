package br.com.amigosecreto.wishlist;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Link opcional de item da lista, pelas regras de {@link WishItemUrl}. {@code null} e vazio são válidos. */
@Documented
@Constraint(validatedBy = WebLink.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface WebLink {

    String message() default "Informe um link que comece com http:// ou https://.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Validador do {@link WebLink}. */
    class Validator implements ConstraintValidator<WebLink, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return WishItemUrl.isValid(value);
        }
    }
}
