package org.acme.boundary;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path.Node;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import io.quarkus.logging.Log;

/**
 * Uebersetzt Verletzungen von Bean-Validation-Constraints in Antworten dieses
 * Projekts.
 *
 * Gegenueber der eingebauten Quarkus-Antwort kommen zwei Dinge dazu:
 * der projektweite "info"-Header, damit Validierungsfehler dieselbe Form haben
 * wie eine ResponseException, und die Unterscheidung zwischen Eingabe- und
 * Rueckgabe-Verletzungen.
 */
public class ExceptionMappers {

    @ServerExceptionMapper(ResteasyReactiveViolationException.class)
    public Response handleViolationException(ResteasyReactiveViolationException exception) {
        if (hasReturnValueViolation(exception)) {
            // Eine verletzte RUECKGABE ist kein Fehler des Aufrufers, sondern ein
            // Fehler im Server. 400 waere hier eine Falschaussage.
            Log.error("Rueckgabewert verletzt ein Constraint: " + exception.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.TEXT_PLAIN)
                    .header("info", "Server hat eine ungueltige Antwort erzeugt")
                    .entity("Server hat eine ungueltige Antwort erzeugt")
                    .build();
        }

        int status = Response.Status.BAD_REQUEST.getStatusCode();
        ValidationErrorResponse body = ValidationErrorResponse.of(status, exception.getConstraintViolations());

        String summary = body.getViolations().stream()
                .map(v -> v.getField() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Ungueltige Eingabe");

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .header("info", summary)
                .entity(body)
                .build();
    }

    /**
     * Auffangnetz fuer Verletzungen, die NICHT an der REST-Grenze entstehen,
     * sondern bei der Methodenvalidierung im Service. Der Interceptor wirft dort
     * eine einfache ConstraintViolationException, die der eingebaute Mapper
     * weiterwirft - ohne diesen Mapper bekaeme der Client einen nackten 500er
     * ohne jede Erklaerung.
     *
     * Der Status bleibt bewusst 500: Wenn die Boundary korrekt mit @Valid prueft,
     * kann dieser Fall nur durch einen Programmierfehler entstehen, nicht durch
     * eine schlechte Eingabe. Details gehoeren ins Log, nicht zum Client.
     */
    @ServerExceptionMapper(ConstraintViolationException.class)
    public Response handleServiceViolation(ConstraintViolationException exception) {
        Log.error("Constraint ausserhalb der REST-Grenze verletzt: " + exception.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN)
                .header("info", "Serverseitige Validierung fehlgeschlagen")
                .entity("Serverseitige Validierung fehlgeschlagen")
                .build();
    }

    private boolean hasReturnValueViolation(ResteasyReactiveViolationException exception) {
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            for (Node node : violation.getPropertyPath()) {
                if (node.getKind() == ElementKind.RETURN_VALUE) {
                    return true;
                }
            }
        }
        return false;
    }
}
