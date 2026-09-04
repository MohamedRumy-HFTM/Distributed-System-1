package org.acme.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Prueft die automatisch erzeugte OpenAPI-Definition. Die Swagger UI unter
 * /q/swagger-ui laesst sich hier NICHT testen: sie ist standardmaessig nur im
 * Dev-Modus registriert, ein @QuarkusTest laeuft aber im Test-Profil.
 */
@QuarkusTest
class OpenApiTest {

    @Test
    void openApiDocumentDescribesAllHttpMethods() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("paths.'/blogs'.get", notNullValue())
               .body("paths.'/blogs'.post", notNullValue())
               .body("paths.'/blogs/{id}'.get", notNullValue())
               .body("paths.'/blogs/{id}'.put", notNullValue())
               .body("paths.'/blogs/{id}'.delete", notNullValue());
    }

    @Test
    void openApiDocumentCarriesProjectMetadata() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("info.title", is("Blog API"))
               .body("info.version", is("1.0.0"));
    }

    @Test
    void openApiDocumentDescribesCommentSubResource() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("paths.'/blogs/{id}/comments'.get", notNullValue())
               .body("paths.'/blogs/{id}/comments'.post", notNullValue());
    }
}
