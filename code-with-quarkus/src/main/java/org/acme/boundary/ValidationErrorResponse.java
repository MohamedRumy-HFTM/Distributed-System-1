package org.acme.boundary;

import java.util.List;
import java.util.Set;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path.Node;

/**
 * Antwortformat fuer Validierungsfehler: pro verletztem Feld ein Eintrag mit
 * Feldname und Meldung. Ein Frontend kann die Meldungen damit direkt an den
 * passenden Formularfeldern anzeigen, statt einen Fliesstext zerlegen zu muessen.
 */
@Schema(description = "Sammelantwort fuer verletzte Eingabe-Constraints.")
public class ValidationErrorResponse {

    @Schema(description = "HTTP-Statuscode der Antwort.", examples = { "400" })
    private int status;

    @Schema(description = "Kurzbezeichnung der Fehlerart.", examples = { "Constraint Violation" })
    private String title;

    @Schema(description = "Die einzelnen Verletzungen.")
    private List<Violation> violations;

    public ValidationErrorResponse() {
    }

    public ValidationErrorResponse(int status, String title, List<Violation> violations) {
        this.status = status;
        this.title = title;
        this.violations = violations;
    }

    public static ValidationErrorResponse of(int status, Set<? extends ConstraintViolation<?>> violations) {
        List<Violation> entries = violations.stream()
                .map(v -> new Violation(leafFieldName(v), v.getMessage()))
                .sorted((a, b) -> a.getField().compareTo(b.getField()))
                .toList();
        return new ValidationErrorResponse(status, "Constraint Violation", entries);
    }

    /**
     * Der Property-Pfad einer Verletzung lautet vollstaendig etwa "create.blog.title".
     * Fuer die Anzeige interessiert nur das letzte Glied - der Feldname selbst.
     */
    private static String leafFieldName(ConstraintViolation<?> violation) {
        String field = "";
        for (Node node : violation.getPropertyPath()) {
            if (node.getName() != null) {
                field = node.getName();
            }
        }
        return field;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public void setViolations(List<Violation> violations) {
        this.violations = violations;
    }

    @Schema(name = "Violation", description = "Eine einzelne Constraint-Verletzung.")
    public static class Violation {

        @Schema(description = "Name des betroffenen Feldes.", examples = { "title" })
        private String field;

        @Schema(description = "Die verletzte Regel im Klartext.", examples = { "Titel braucht mindestens 5 Zeichen." })
        private String message;

        public Violation() {
        }

        public Violation(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
