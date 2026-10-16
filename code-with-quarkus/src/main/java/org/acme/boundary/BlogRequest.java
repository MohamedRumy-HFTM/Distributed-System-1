package org.acme.boundary;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Eingabe-Vertrag fuer Blogs. Enthaelt bewusst KEINE id: die vergibt der Server.
 * Ein Client kann damit auch keine fremde id unterschieben.
 */
@Schema(name = "BlogRequest", description = "Nutzdaten zum Anlegen oder Aendern eines Blogs.")
public record BlogRequest(

        @Schema(required = true, examples = { "Mein erster Blogbeitrag" })
        @NotBlank(message = "Titel darf nicht leer sein.")
        @Size(min = 5, message = "Titel braucht mindestens 5 Zeichen.")
        String title,

        @Schema(required = true, examples = { "Das ist der Inhalt meines Beitrags." })
        @NotBlank(message = "Inhalt darf nicht leer sein.")
        String content) {
}
