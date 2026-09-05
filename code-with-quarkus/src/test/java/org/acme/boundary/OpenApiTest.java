package org.acme.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
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
    void openApiDocumentListsTheHeaderParameter() {
        given()
              .accept("application/json")
            .when().get("/q/openapi")
            .then()
               .statusCode(200)
               .body("paths.'/blogs/{id}'.delete.parameters.name", hasItem("X-Admin-Token"));
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
                       is("#/components/schemas/PagedResultBlog"))
               .body("components.schemas.PagedResultBlog.properties.data.items.$ref",
                       is("#/components/schemas/Blog"))
               .body("components.schemas.PagedResultBlog.properties.totalCount.description", notNullValue());
    }
}
