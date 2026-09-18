package org.acme.boundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;

/**
 * System-Test der Blog-API: prueft ausschliesslich das Verhalten von aussen
 * ueber HTTP und JSON. Es werden bewusst KEINE Modellklassen oder internen
 * Methoden angefasst - der Test wuesste nicht einmal, dass es Panache gibt.
 *
 * Der Ablauf ist eine zusammenhaengende Geschichte, deshalb die feste
 * Reihenfolge: Anfangsanzahl lesen, Datensatz anlegen, Anzahl vergleichen,
 * Datensatz ueber den Location-Header wieder abrufen, kommentieren, Fehlerfaelle
 * pruefen und am Ende aufraeumen.
 *
 * Der Zustand zwischen den Schritten liegt in STATIC-Feldern: JUnit legt pro
 * Testmethode eine neue Instanz der Klasse an, ein Instanzfeld waere im
 * naechsten Schritt wieder null.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BlogSystemTest {

    private static final String TOKEN_HEADER = "X-Admin-Token";
    private static final String VALID_TOKEN = "local-dev-token";

    private static int totalCountBefore;
    private static String createdLocation;

    /** Liest den Body einer Antwort als JSON-Objekt (JSON-P). */
    private static JsonObject readObject(String body) {
        return Json.createReader(new StringReader(body)).readObject();
    }

    /** Liest den Body einer Antwort als JSON-Array (JSON-P). */
    private static JsonArray readArray(String body) {
        return Json.createReader(new StringReader(body)).readArray();
    }

    @Test
    @Order(1)
    void schrittEinsAnfangsanzahlLesen() {
        String body = RestAssured
                .when()
                    .get("/blogs")
                .then()
                    .statusCode(Status.OK.getStatusCode())
                    .extract()
                    .body()
                    .asString();

        JsonObject page = readObject(body);
        totalCountBefore = page.getInt("totalCount");

        // Die Startdaten der Anwendung muessen vorhanden sein.
        assertTrue(totalCountBefore >= 2,
                "Erwartet mindestens die zwei Beispiel-Blogs, war: " + totalCountBefore);
        assertTrue(page.containsKey("data"), "Antwort muss die Datensaetze unter 'data' kapseln");
    }

    @Test
    @Order(2)
    void schrittZweiBlogAnlegen() {
        String requestBody = """
                {
                    "title": "System-Test Blog",
                    "content": "Von aussen ueber HTTP angelegt."
                }
                """;

        createdLocation = RestAssured
                .given()
                    .body(requestBody)
                    .contentType(MediaType.APPLICATION_JSON)
                .when()
                    .post("/blogs")
                .then()
                    .statusCode(Status.CREATED.getStatusCode())
                    .extract()
                    .header("Location");

        assertNotNull(createdLocation, "POST muss einen Location-Header liefern");
        assertTrue(createdLocation.contains("/blogs/"),
                "Location muss auf die neue Ressource zeigen, war: " + createdLocation);
    }

    @Test
    @Order(3)
    void schrittDreiAnzahlHatSichErhoeht() {
        String body = RestAssured
                .when()
                    .get("/blogs")
                .then()
                    .statusCode(Status.OK.getStatusCode())
                    .extract()
                    .body()
                    .asString();

        int totalCountAfter = readObject(body).getInt("totalCount");
        assertEquals(totalCountBefore + 1, totalCountAfter,
                "Nach einem POST muss genau ein Datensatz mehr gezaehlt werden");
    }

    @Test
    @Order(4)
    void schrittVierBlogUeberLocationAbrufen() {
        String body = RestAssured
                .when()
                    .get(createdLocation)
                .then()
                    .statusCode(Status.OK.getStatusCode())
                    .extract()
                    .body()
                    .asString();

        JsonObject blog = readObject(body);
        assertEquals("System-Test Blog", blog.getString("title"));
        assertEquals("Von aussen ueber HTTP angelegt.", blog.getString("content"));
        assertTrue(blog.containsKey("id"), "Die vom Server vergebene Id muss mitgeliefert werden");
    }

    @Test
    @Order(5)
    void schrittFuenfKommentarAnhaengenUndLesen() {
        String requestBody = """
                {
                    "author": "Alice",
                    "text": "Sieht gut aus!"
                }
                """;

        RestAssured
                .given()
                    .body(requestBody)
                    .contentType(MediaType.APPLICATION_JSON)
                .when()
                    .post(createdLocation + "/comments")
                .then()
                    .statusCode(Status.CREATED.getStatusCode());

        String body = RestAssured
                .when()
                    .get(createdLocation + "/comments")
                .then()
                    .statusCode(Status.OK.getStatusCode())
                    .extract()
                    .body()
                    .asString();

        JsonArray comments = readArray(body);
        assertEquals(1, comments.size());
        assertEquals("Alice", comments.getJsonObject(0).getString("author"));
        assertEquals("Sieht gut aus!", comments.getJsonObject(0).getString("text"));
    }

    @Test
    @Order(6)
    void schrittSechsValidierungsfehlerWirdGemeldet() {
        String zuKurzerTitel = """
                {
                    "title": "kurz",
                    "content": "Der Titel hat nur vier Zeichen."
                }
                """;

        String body = RestAssured
                .given()
                    .body(zuKurzerTitel)
                    .contentType(MediaType.APPLICATION_JSON)
                .when()
                    .post("/blogs")
                .then()
                    .statusCode(Status.BAD_REQUEST.getStatusCode())
                    .extract()
                    .body()
                    .asString();

        JsonObject error = readObject(body);
        assertEquals(Status.BAD_REQUEST.getStatusCode(), error.getInt("status"));
        assertEquals("Constraint Violation", error.getString("title"));

        JsonArray violations = error.getJsonArray("violations");
        assertEquals(1, violations.size());
        assertEquals("title", violations.getJsonObject(0).getString("field"));
        assertEquals("Titel braucht mindestens 5 Zeichen.",
                violations.getJsonObject(0).getString("message"));
    }

    @Test
    @Order(7)
    void schrittSiebenUnbekannteRessourceMeldetNichtGefunden() {
        RestAssured
                .when()
                    .get("/blogs/999999")
                .then()
                    .statusCode(Status.NOT_FOUND.getStatusCode())
                    .header("info", "Kein Blog mit der Id 999999");
    }

    @Test
    @Order(8)
    void schrittAchtLoeschenBrauchtBerechtigung() {
        // Ohne Header abgewiesen ...
        RestAssured
                .when()
                    .delete(createdLocation)
                .then()
                    .statusCode(Status.UNAUTHORIZED.getStatusCode());

        // ... mit gueltigem Token entfernt, inklusive des Kommentars.
        RestAssured
                .given()
                    .header(TOKEN_HEADER, VALID_TOKEN)
                .when()
                    .delete(createdLocation)
                .then()
                    .statusCode(Status.NO_CONTENT.getStatusCode());

        RestAssured
                .when()
                    .get(createdLocation)
                .then()
                    .statusCode(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    @Order(9)
    void schrittNeunAusgangszustandIstWiederHergestellt() {
        String body = RestAssured
                .when()
                    .get("/blogs")
                .then()
                    .statusCode(Status.OK.getStatusCode())
                    .extract()
                    .body()
                    .asString();

        assertEquals(totalCountBefore, readObject(body).getInt("totalCount"),
                "Der System-Test soll keine Datensaetze hinterlassen");
    }
}
