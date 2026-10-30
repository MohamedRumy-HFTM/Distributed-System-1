package org.acme.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;

/**
 * Prueft das Berechtigungskonzept: welche Rolle darf was, und greift die
 * Eigentuemer-Regel.
 *
 * Die Identitaet kommt von @TestSecurity, nicht von einem echten Token. Dadurch
 * laufen diese Tests ohne Keycloak - geprueft wird die Zugriffslogik der
 * Anwendung, nicht Keycloak selbst. Dass echte Tokens funktionieren, ist im
 * README mit HTTPie belegt.
 */
@QuarkusTest
class BlogAuthTest {

    /** Sucht einen Blog des angegebenen Autors in der Liste. */
    private Integer blogIdOwnedBy(String author) {
        Integer id = given()
                  .queryParam("size", 100)
                .when().get("/blogs")
                .then().statusCode(200)
                .extract().path("data.find { it.author == '" + author + "' }.id");
        assertNotNull(id, "Kein Blog von " + author + " gefunden - Startdaten fehlen?");
        return id;
    }

    private String blogBody(String title) {
        return """
                {
                    "title": "%s",
                    "content": "Irgendein Inhalt."
                }
                """.formatted(title);
    }

    // --- ohne Login -----------------------------------------------------------

    @Test
    void lesenBrauchtKeinLogin() {
        given().when().get("/blogs").then().statusCode(200);

        Integer id = given().when().get("/blogs").then().statusCode(200)
                .extract().path("data[0].id");

        given().when().get("/blogs/" + id).then().statusCode(200);
        given().when().get("/blogs/" + id + "/comments").then().statusCode(200);
    }

    @Test
    void schreibenOhneLoginIstUnauthorized() {
        given().contentType("application/json").body(blogBody("Ohne Login"))
            .when().post("/blogs").then().statusCode(401);

        given().contentType("application/json").body(blogBody("Ohne Login"))
            .when().put("/blogs/1").then().statusCode(401);

        given().when().delete("/blogs/1").then().statusCode(401);

        given().contentType("application/json")
              .body("""
                      {
                          "author": "Anonym",
                          "text": "Ohne Login"
                      }
                      """)
            .when().post("/blogs/1/comments").then().statusCode(401);
    }

    // --- reader ---------------------------------------------------------------

    @Test
    @TestSecurity(user = "reader1", roles = { "reader" })
    void readerDarfKommentierenAberNichtSchreiben() {
        Integer id = blogIdOwnedBy("author1");

        given().contentType("application/json")
              .body("""
                      {
                          "author": "reader1",
                          "text": "Darf ich."
                      }
                      """)
            .when().post("/blogs/" + id + "/comments")
            .then().statusCode(201);

        given().contentType("application/json").body(blogBody("Reader darf das nicht"))
            .when().post("/blogs").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "reader1", roles = { "reader" })
    void readerDarfNichtLoeschen() {
        given().when().delete("/blogs/" + blogIdOwnedBy("author1")).then().statusCode(403);
    }

    // --- author: Eigentuemerschaft --------------------------------------------

    @Test
    @TestSecurity(user = "author1", roles = { "reader", "author" })
    void authorDarfEigenenBlogAendern() {
        Integer own = blogIdOwnedBy("author1");

        given().contentType("application/json").body(blogBody("Eigener Blog geaendert"))
            .when().put("/blogs/" + own)
            .then()
               .statusCode(200)
               .body("title", is("Eigener Blog geaendert"))
               .body("author", is("author1"));
    }

    @Test
    @TestSecurity(user = "author1", roles = { "reader", "author" })
    void authorDarfFremdenBlogNichtAendern() {
        given().contentType("application/json").body(blogBody("Fremder Blog"))
            .when().put("/blogs/" + blogIdOwnedBy("admin1"))
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "author1", roles = { "reader", "author" })
    void authorDarfFremdenBlogNichtLoeschen() {
        given().when().delete("/blogs/" + blogIdOwnedBy("admin1")).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "author1", roles = { "reader", "author" })
    void fehlendeIdSchlaegtVorDerEigentuemerPruefungZu() {
        // 404 statt 403: ueber einen nicht existierenden Blog verraet die API
        // nichts, auch nicht indirekt.
        given().when().delete("/blogs/999999").then().statusCode(404);
    }

    // --- admin ----------------------------------------------------------------

    @Test
    @TestSecurity(user = "admin1", roles = { "reader", "author", "admin" })
    void adminDarfFremdenBlogAendern() {
        given().contentType("application/json").body(blogBody("Admin greift durch"))
            .when().put("/blogs/" + blogIdOwnedBy("author1"))
            .then()
               .statusCode(200)
               .body("title", is("Admin greift durch"));
    }

    @Test
    @TestSecurity(user = "admin1", roles = { "reader", "author", "admin" })
    void adminDarfFremdenBlogLoeschen() {
        // Erst einen fremden Blog anlegen lassen, damit nichts Gesaetes stirbt.
        Integer foreign = given()
                  .contentType("application/json").body(blogBody("Von admin zu loeschen"))
                .when().post("/blogs")
                .then().statusCode(201)
                .extract().path("id");

        given().when().delete("/blogs/" + foreign).then().statusCode(204);
    }
}
