package org.acme.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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

    @Test
    void openApiDocumentListsAllQueryParameters() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("paths.'/blogs'.get.parameters.name",
                       hasItems("searchString", "sortBy", "orderBy", "page", "size"));
    }

    @Test
    void openApiDocumentDeclaresBearerAuth() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("components.securitySchemes.bearerAuth.type", is("http"))
               .body("components.securitySchemes.bearerAuth.scheme", is("bearer"))
               .body("components.securitySchemes.bearerAuth.bearerFormat", is("JWT"));
    }

    /**
     * Die benoetigten Rollen stehen pro Operation im Dokument. Damit ist das
     * Berechtigungskonzept nicht nur im Code und in der README beschrieben,
     * sondern maschinenlesbar - und diese Zusicherung bricht, wenn jemand eine
     * Annotation entfernt.
     */
    @Test
    void openApiDocumentStatesRequiredRolesPerOperation() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("paths.'/blogs'.post.security[0].bearerAuth", hasItem("author"))
               .body("paths.'/blogs/{id}'.put.security[0].bearerAuth", hasItems("author", "admin"))
               .body("paths.'/blogs/{id}'.delete.security[0].bearerAuth", hasItems("author", "admin"))
               .body("paths.'/blogs/{id}/comments'.post.security[0].bearerAuth", hasItem("reader"))
               .body("paths.'/blogs'.get.security", nullValue());
    }

    @Test
    void openApiDocumentCarriesRequestBodyExample() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("paths.'/blogs'.post.requestBody.content.'application/json'.examples.'Neuer Blog'.value",
                       notNullValue());
    }

    @Test
    void openApiDocumentDocumentsEntitySchemas() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("components.schemas.Blog.description", notNullValue())
               .body("components.schemas.Comment.description", notNullValue())
               .body("components.schemas.Blog.required", hasItem("title"));
    }

    /**
     * Die 400-Antworten sollen nicht nur beschrieben, sondern mit dem konkreten
     * Fehlerformat verknuepft sein - sonst weiss ein Client zwar, DASS ein 400
     * kommen kann, aber nicht, wie er es auswertet.
     */
    @Test
    void openApiDocumentLinksTheValidationErrorSchema() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("paths.'/blogs'.post.responses.'400'.content.'application/json'.schema.$ref",
                       is("#/components/schemas/ValidationErrorResponse"))
               .body("paths.'/blogs/validate'.post.responses.'400'.content.'application/json'.schema.$ref",
                       is("#/components/schemas/ValidationErrorResponse"))
               .body("components.schemas.ValidationErrorResponse.properties.violations.items.$ref",
                       is("#/components/schemas/Violation"));
    }

    /**
     * Der generische Wrapper wird von SmallRye zur konkreten Auspraegung
     * PagedResultBlog aufgeloest. Wichtig ist, dass data typisiert auf Blog
     * verweist - sonst waere die Dokumentation fuer einen Client wertlos.
     */
    @Test
    void openApiDocumentTypesThePagedWrapper() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("paths.'/blogs'.get.responses.'200'.content.'application/json'.schema.$ref",
                       is("#/components/schemas/PagedResultBlogResponse"))
               .body("components.schemas.PagedResultBlogResponse.properties.data.items.$ref",
                       is("#/components/schemas/BlogResponse"))
               .body("components.schemas.PagedResultBlogResponse.properties.totalCount.description", notNullValue());
    }
}
