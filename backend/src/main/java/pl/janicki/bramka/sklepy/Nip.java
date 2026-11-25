package pl.janicki.bramka.sklepy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = Nip.Walidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface Nip {

    String message() default "nieprawidłowy NIP";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Walidator implements ConstraintValidator<Nip, String> {

        private static final int[] WAGI = { 6, 5, 7, 2, 3, 4, 5, 6, 7 };

        @Override
        public boolean isValid(String nip, ConstraintValidatorContext kontekst) {
            return nip == null || poprawny(nip);
        }

        public static boolean poprawny(String nip) {
            if (!nip.matches("^[0-9]{10}$")) {
                return false;
            }
            int suma = 0;
            for (int i = 0; i < WAGI.length; i++) {
                suma += WAGI[i] * (nip.charAt(i) - '0');
            }
            int kontrolna = suma % 11;
            return kontrolna != 10 && kontrolna == nip.charAt(9) - '0';
        }
    }
}
