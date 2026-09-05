package org.acme.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;

@QuarkusTest
class BlogResourceTest {

    private static final String TOKEN_HEADER = "X-Admin-Token";
    private static final String VALID_TOKEN = "local-dev-token";

    private ExtractableResponse<?> createBlog(String title, String content) {
        return given()
                  .contentType("application/json")
                  .body("{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}")
                .when().post("/blogs")
                .then().statusCode(201)
                .extract();
    }

    // --- Auflisten, Paging, Suche, Sortierung ---------------------------------

    @Test
    void listingWrapsDataAndTotalCount() {
        given()
          .when().get("/blogs")
          .then()
             .statusCode(200)
             .body("data.size()", greaterThanOrEqualTo(2))
             .body("totalCount", greaterThanOrEqualTo(2))
             .body("page", is(0))
             .body("size", is(10));
    }

    @Test
    void pagingLimitsTheNumberOfRecords() {
        given()
              .queryParam("page", 0)
              .queryParam("size", 1)
            .when().get("/blogs")
            .then()
               .statusCode(200)
               .body("data.size()", is(1))
               .body("size", is(1))
               .body("totalCount", greaterThanOrEqualTo(2))
               .body("pageCount", greaterThanOrEqualTo(2));
    }

    @Test
    void searchStringMatchesTitleAndContent() {
        createBlog("Searchable Unicorn", "find me");

        given()
              .queryParam("searchString", "unicorn")
            .when().get("/blogs")
            .then()
               .statusCode(200)
               .body("totalCount", is(1))
               .body("data[0].title", is("Searchable Unicorn"));
    }

    @Test
    void sortingRespectsFieldAndDirection() {
        // Arrange - gemeinsames Praefix, damit die Suche genau diese zwei trifft
        createBlog("zzsort alpha", "erster");
        createBlog("zzsort beta", "zweiter");

        // Act / Assert - absteigend steht beta vorn
        given()
              .queryParam("searchString", "zzsort")
              .queryParam("sortBy", "title")
              .queryParam("orderBy", "desc")
            .when().get("/blogs")
            .then()
               .statusCode(200)
               .body("totalCount", is(2))
               .body("data[0].title", is("zzsort beta"));

        // Act / Assert - aufsteigend steht alpha vorn
        given()
              .queryParam("searchString", "zzsort")
              .queryParam("sortBy", "title")
              .queryParam("orderBy", "asc")
            .when().get("/blogs")
            .then()
               .statusCode(200)
               .body("data[0].title", is("zzsort alpha"));
    }

    @Test
    void unknownSortFieldIsRejected() {
        given()
              .queryParam("sortBy", "title); drop table Blog--")
            .when().get("/blogs")
            .then().statusCode(400);
    }

    @Test
    void invalidPagingParametersAreRejected() {
        given().queryParam("page", -1).when().get("/blogs").then().statusCode(400);
        given().queryParam("size", 0).when().get("/blogs").then().statusCode(400);
        given().queryParam("size", 101).when().get("/blogs").then().statusCode(400);
        given().queryParam("orderBy", "sideways").when().get("/blogs").then().statusCode(400);
    }

    // --- Anlegen -------------------------------------------------------------

    @Test
    void creatingBlogReturnsLocationHeaderAndBody() {
        ExtractableResponse<?> response = createBlog("REST Blog", "created via HTTP");
        Integer id = response.path("id");
        String location = response.header("Location");

        assertTrue(location.endsWith("/blogs/" + id),
                "Location sollte auf die neue Ressource zeigen, war aber: " + location);

        given()
          .when().get("/blogs/" + id)
          .then()
             .statusCode(200)
             .body("content", is("created via HTTP"));
    }

    @Test
    void creatingBlogWithoutTitleIsRejected() {
        given()
              .contentType("application/json")
              .body("{\"content\":\"kein Titel gesetzt\"}")
            .when().post("/blogs")
            .then()
               .statusCode(400)
               .header("info", is("Request-Body oder Titel fehlt"));
    }

    @Test
    void creatingBlogWithBlankTitleIsRejected() {
        given()
              .contentType("application/json")
              .body("{\"title\":\"   \",\"content\":\"nur Leerzeichen\"}")
            .when().post("/blogs")
            .then().statusCode(400);
    }

    // --- Aendern und Loeschen -------------------------------------------------

    @Test
    void updatingBlogReplacesItsFields() {
        Integer id = createBlog("Vorher", "alter Inhalt").path("id");

        given()
              .contentType("application/json")
              .body("{\"title\":\"Nachher\",\"content\":\"neuer Inhalt\"}")
            .when().put("/blogs/" + id)
            .then()
               .statusCode(200)
               .body("title", is("Nachher"));

        given()
          .when().get("/blogs/" + id)
          .then()
             .statusCode(200)
             .body("content", is("neuer Inhalt"));
    }

    @Test
    void updatingUnknownBlogReturnsNotFound() {
        given()
              .contentType("application/json")
              .body("{\"title\":\"Egal\",\"content\":\"Egal\"}")
            .when().put("/blogs/999999")
            .then().statusCode(404);
    }

    @Test
    void unknownBlogReturnsNotFound() {
        given()
          .when().get("/blogs/999999")
          .then()
             .statusCode(404)
             .header("info", is("Kein Blog mit der Id 999999"));
    }

    @Test
    void deletingBlogRequiresToken() {
        Integer id = createBlog("Temp Blog", "short lived").path("id");

        // Ohne Header -> 401
        given().when().delete("/blogs/" + id).then().statusCode(401);

        // Falsches Token -> 403
        given().header(TOKEN_HEADER, "wrong").when().delete("/blogs/" + id).then().statusCode(403);

        // Gueltiges Token -> 204, danach ist der Blog weg
        given().header(TOKEN_HEADER, VALID_TOKEN).when().delete("/blogs/" + id).then().statusCode(204);
        given().when().get("/blogs/" + id).then().statusCode(404);
    }

    // --- Kommentare ----------------------------------------------------------

    @Test
    void addingAndListingComments() {
        Integer blogId = createBlog("Commented Blog", "has comments").path("id");

        given()
              .contentType("application/json")
              .body("{\"author\":\"Alice\",\"text\":\"Nice one\"}")
            .when().post("/blogs/" + blogId + "/comments")
            .then()
               .statusCode(201)
               .body("author", is("Alice"));

        given()
          .when().get("/blogs/" + blogId + "/comments")
          .then()
             .statusCode(200)
             .body("size()", is(1))
             .body("[0].text", is("Nice one"));
    }

    @Test
    void commentWithoutTextIsRejected() {
        Integer blogId = createBlog("Blog ohne Kommentartext", "...").path("id");

        given()
              .contentType("application/json")
              .body("{\"author\":\"Alice\"}")
            .when().post("/blogs/" + blogId + "/comments")
            .then().statusCode(400);
    }

    @Test
    void commentOnUnknownBlogReturnsNotFound() {
        given()
              .contentType("application/json")
              .body("{\"author\":\"Mallory\",\"text\":\"Ghost\"}")
            .when().post("/blogs/999999/comments")
            .then().statusCode(404);
    }
}
