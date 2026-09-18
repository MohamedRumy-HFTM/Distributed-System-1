package org.acme.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Belegt die Idempotenz der Startdaten-Initialisierung.
 *
 * Beim regulaeren Testlauf wird das Schema neu erzeugt, die Datenbank ist also
 * immer leer und der Ueberspringen-Zweig lief nie - der Coverage-Report wies ihn
 * entsprechend als ungetestet aus. Ein direkter zweiter Aufruf erzeugt genau die
 * Situation, die bei einem Redeploy gegen eine bestehende Datenbank entstuende.
 */
@QuarkusTest
class DataInitializationTest {

    @Inject
    DataInitialization dataInitialization;

    @Inject
    BlogService blogService;

    @Test
    void zweiterDurchlaufLegtKeineDuplikateAn() {
        // Arrange - der Start der Anwendung hat bereits Daten angelegt
        long before = blogService.countBlogs();
        assertTrue(before > 0, "Voraussetzung: die Startdaten muessen vorhanden sein");

        // Act - der Parameter wird im Rumpf nicht ausgewertet, null genuegt
        dataInitialization.init(null);

        // Assert
        assertEquals(before, blogService.countBlogs(),
                "Ein zweiter Lauf der Initialisierung darf nichts hinzufuegen");
    }
}
