package org.acme.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Set;

import org.acme.entity.Blog;
import org.junit.jupiter.api.Test;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.ws.rs.core.Response;

/**
 * Prueft die Verzweigungen des Exception-Mappers, die ueber HTTP NICHT
 * erreichbar sind: Eine Rueckgabe- oder Service-Verletzung kann nur entstehen,
 * wenn die Boundary bereits passiert wurde. Der Coverage-Report hatte genau
 * diese Zweige als ungetestet ausgewiesen.
 *
 * Bewusst kein System-Test: Hier geht es um das Verhalten einer einzelnen
 * Komponente, nicht um das der API von aussen.
 */
@QuarkusTest
class ExceptionMappersTest {

    @Inject
    Validator validator;

    private final ExceptionMappers mappers = new ExceptionMappers();

    /** Traegerklasse, um eine echte Rueckgabe-Verletzung erzeugen zu koennen. */
    static class BlogFactory {
        @Valid
        public Blog create(Blog blog) {
            return blog;
        }
    }

    @Test
    void eingabeVerletzungWirdZuBadRequest() {
        Set<ConstraintViolation<Blog>> violations = validator.validate(new Blog("kurz", "Inhalt"));
        ResteasyReactiveViolationException exception = new ResteasyReactiveViolationException(violations);

        Response response = mappers.handleViolationException(exception);

        assertEquals(400, response.getStatus());
        assertTrue(response.getEntity() instanceof ValidationErrorResponse,
                "Der Body muss die strukturierte Verletzungsliste sein");

        ValidationErrorResponse body = (ValidationErrorResponse) response.getEntity();
        assertEquals("Constraint Violation", body.getTitle());
        assertEquals(1, body.getViolations().size());
        assertEquals("title", body.getViolations().get(0).getField());
        assertEquals("Titel braucht mindestens 5 Zeichen.", body.getViolations().get(0).getMessage());
        assertTrue(response.getHeaderString("info").contains("Titel braucht mindestens 5 Zeichen."));
    }

    @Test
    void rueckgabeVerletzungWirdZuServerFehler() throws Exception {
        Method create = BlogFactory.class.getMethod("create", Blog.class);
        Set<ConstraintViolation<BlogFactory>> violations = validator.forExecutables()
                .validateReturnValue(new BlogFactory(), create, new Blog("kurz", "Inhalt"));

        assertTrue(violations.size() > 0, "Voraussetzung: es muss eine Rueckgabe-Verletzung geben");
        ResteasyReactiveViolationException exception = new ResteasyReactiveViolationException(violations);

        Response response = mappers.handleViolationException(exception);

        // Eine verletzte Rueckgabe ist ein Fehler des Servers, nicht des Aufrufers.
        assertEquals(500, response.getStatus());
        assertEquals("Server hat eine ungueltige Antwort erzeugt", response.getEntity());
    }

    @Test
    void serviceVerletzungWirdZuServerFehler() {
        Set<ConstraintViolation<Blog>> violations = validator.validate(new Blog("kurz", ""));
        ConstraintViolationException exception = new ConstraintViolationException(violations);

        Response response = mappers.handleServiceViolation(exception);

        assertEquals(500, response.getStatus());
        assertEquals("Serverseitige Validierung fehlgeschlagen", response.getEntity());
        assertEquals("Serverseitige Validierung fehlgeschlagen", response.getHeaderString("info"));
    }
}
