package org.acme.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;

/**
 * Blackbox-Test gegen das GEPACKTE Artefakt.
 *
 * Unterschied zu {@link BlogSystemTest}: dort laeuft die Anwendung in derselben
 * JVM, und {@code @TestSecurity} setzt die Identitaet kuenstlich. Hier laeuft
 * ein eigener Prozess im Prod-Profil, gegen eine echte MySQL und ein echtes
 * Keycloak - der Test kennt nur HTTP und holt sich ein echtes Bearer-Token.
 *
 * Damit prueft dieser Test Dinge, die ein @QuarkusTest nicht kann: dass die
 * Prod-Konfiguration stimmt, dass das Artefakt ueberhaupt startet, dass die
 * Token-Validierung gegen einen echten Issuer funktioniert und dass die
 * Rollen aus realm_access.roles tatsaechlich ankommen.
 *
 * Laufen lassen mit: ./mvnw verify -DskipITs=false
 */
@QuarkusIntegrationTest
@WithTestResource(value = BlackboxEnvironment.class, scope = TestResourceScope.RESTRICTED_TO_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BlogBlackboxIT {

    private static String keycloakUrl() {
        String url = System.getProperty(BlackboxEnvironment.KEYCLOAK_URL_PROPERTY);
        assertNotNull(url, "Keycloak-Adresse fehlt - lief BlackboxEnvironment?");
        return url;
    }

    /** Holt ein echtes Access-Token ueber den Password-Grant. */
    private static String tokenFor(String user) {
        return given()
                  .baseUri(keycloakUrl())
                  .contentType("application/x-www-form-urlencoded")
                  .formParam("grant_type", "password")
                  .formParam("client_id", "blog-api")
                  .formParam("username", user)
                  .formParam("password", user)
                .when().post("/realms/blog/protocol/openid-connect/token")
                .then().statusCode(200)
                .extract().path("access_token");
    }

    private static String blogBody(String title) {
        return """
                {
                    "title": "%s",
                    "content": "Aus dem Blackbox-Test."
                }
                """.formatted(title);
    }

    @Test
    @Order(1)
    void artefaktLaeuftUndStartdatenSindDa() {
        // Belegt gleichzeitig, dass die %prod-Konfiguration fuer Datenbank und
        // Schema stimmt - ohne sie waere der Prozess nicht hochgekommen.
        given()
          .when().get("/blogs")
          .then()
             .statusCode(200)
             .body("totalCount", greaterThanOrEqualTo(2));

        given().when().get("/hello").then().statusCode(200).body(is("Hello from Quarkus REST"));
    }

    @Test
    @Order(2)
    void echtesTokenTraegtDieRollen() {
        String token = tokenFor("author1");
        assertNotNull(token, "Kein Token von Keycloak erhalten");

        given()
              .header("Authorization", "Bearer " + token)
              .contentType("application/json")
              .body(blogBody("Mit echtem Token angelegt"))
            .when().post("/blogs")
            .then()
               .statusCode(201)
               // Der Autor kommt aus dem Token, nicht aus dem Body.
               .body("author", is("author1"));
    }

    @Test
    @Order(3)
    void ohneTokenKeinSchreibzugriff() {
        given()
              .contentType("application/json")
              .body(blogBody("Ohne Token"))
            .when().post("/blogs")
            .then().statusCode(401);
    }

    @Test
    @Order(4)
    void falscheRolleWirdAbgewiesen() {
        given()
              .header("Authorization", "Bearer " + tokenFor("reader1"))
              .contentType("application/json")
              .body(blogBody("Reader darf nicht"))
            .when().post("/blogs")
            .then().statusCode(403);
    }

    @Test
    @Order(5)
    void fremderBlogIstGeschuetzt() {
        // Die Startdaten legen "Second Blog" unter admin1 an.
        Integer fremder = given()
                  .queryParam("searchString", "Second")
                .when().get("/blogs")
                .then().statusCode(200)
                .extract().path("data[0].id");

        given()
              .header("Authorization", "Bearer " + tokenFor("author1"))
              .contentType("application/json")
              .body(blogBody("Fremder Blog"))
            .when().put("/blogs/" + fremder)
            .then().statusCode(403);

        // Ein admin darf denselben Blog aendern.
        given()
              .header("Authorization", "Bearer " + tokenFor("admin1"))
              .contentType("application/json")
              .body(blogBody("Admin greift durch"))
            .when().put("/blogs/" + fremder)
            .then().statusCode(200);
    }

    @Test
    @Order(6)
    void validierungGreiftAuchImPaket() {
        given()
              .header("Authorization", "Bearer " + tokenFor("author1"))
              .contentType("application/json")
              .body(blogBody("kurz"))
            .when().post("/blogs")
            .then().statusCode(400)
               .body("title", is("Constraint Violation"));
    }

    @Test
    @Order(7)
    void openApiDokumentIstVerfuegbar() {
        // Swagger UI gibt es im Prod-Profil nicht, das Dokument schon.
        RestAssured.given().accept("application/json")
            .when().get("/q/openapi")
            .then().statusCode(200)
               .body("info.title", is("Blog API"));

        RestAssured.when().get("/q/swagger-ui").then().statusCode(404);
    }
}
