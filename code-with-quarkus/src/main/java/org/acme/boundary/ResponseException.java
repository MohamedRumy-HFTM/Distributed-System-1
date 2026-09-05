package org.acme.boundary;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Einheitlich aufgebaute Fehlerantwort. Die Meldung wird gleichzeitig als
 * Exception-Message, als "info"-Header und als Body ausgeliefert, damit ein
 * Client sie unabhaengig vom gewaehlten Auswertungsweg findet.
 *
 * Weil die Exception eine fertige Response mitbringt, uebernimmt Quarkus REST
 * sie unveraendert und ersetzt sie nicht durch die Standardbehandlung.
 */
public class ResponseException extends WebApplicationException {

    public ResponseException(Status status, String message) {
        super(message, Response.status(status)
                .type(MediaType.TEXT_PLAIN)
                .header("info", message)
                .entity(message)
                .build());
    }
}
