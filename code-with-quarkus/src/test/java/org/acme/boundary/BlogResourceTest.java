package org.acme.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class BlogResourceTest {

    @Test
    void listingBlogsReturnsSeededData() {
        given()
          .when().get("/blogs")
          .then()
             .statusCode(200)
             .body("size()", greaterThanOrEqualTo(2));
    }

    @Test
    void creatingBlogAndReadingItBack() {
        // Arrange / Act
        Integer id = given()
              .contentType("application/json")
              .body("{\"title\":\"REST Blog\",\"content\":\"created via HTTP\"}")
            .when().post("/blogs")
            .then()
               .statusCode(201)
               .body("title", is("REST Blog"))
               .extract().path("id");

        // Assert
        given()
          .when().get("/blogs/" + id)
          .then()
             .statusCode(200)
             .body("content", is("created via HTTP"));
    }

    @Test
    void addingAndListingComments() {
        // Arrange
        Integer blogId = given()
              .contentType("application/json")
              .body("{\"title\":\"Commented Blog\",\"content\":\"has comments\"}")
            .when().post("/blogs")
            .then().statusCode(201)
            .extract().path("id");

        // Act
        given()
              .contentType("application/json")
              .body("{\"author\":\"Alice\",\"text\":\"Nice one\"}")
            .when().post("/blogs/" + blogId + "/comments")
            .then()
               .statusCode(201)
               .body("author", is("Alice"));

        // Assert
        given()
          .when().get("/blogs/" + blogId + "/comments")
          .then()
             .statusCode(200)
             .body("size()", is(1))
             .body("[0].text", is("Nice one"));
    }

    @Test
    void searchingBlogsByTitle() {
        given()
              .contentType("application/json")
              .body("{\"title\":\"Searchable Unicorn\",\"content\":\"find me\"}")
            .when().post("/blogs")
            .then().statusCode(201);

        given()
              .queryParam("title", "unicorn")
            .when().get("/blogs")
            .then()
               .statusCode(200)
               .body("size()", is(1))
               .body("[0].title", is("Searchable Unicorn"));
    }

    @Test
    void updatingBlogReplacesItsFields() {
        // Arrange
        Integer id = given()
              .contentType("application/json")
              .body("{\"title\":\"Vorher\",\"content\":\"alter Inhalt\"}")
            .when().post("/blogs")
            .then().statusCode(201)
            .extract().path("id");

        // Act
        given()
              .contentType("application/json")
              .body("{\"title\":\"Nachher\",\"content\":\"neuer Inhalt\"}")
            .when().put("/blogs/" + id)
            .then()
               .statusCode(200)
               .body("title", is("Nachher"));

        // Assert
        given()
          .when().get("/blogs/" + id)
          .then()
             .statusCode(200)
             .body("title", is("Nachher"))
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
        given().when().get("/blogs/999999").then().statusCode(404);
    }

    @Test
    void deletingBlogRemovesIt() {
        // Arrange
        Integer id = given()
              .contentType("application/json")
              .body("{\"title\":\"Temp Blog\",\"content\":\"short lived\"}")
            .when().post("/blogs")
            .then().statusCode(201)
            .extract().path("id");

        // Act
        given().when().delete("/blogs/" + id).then().statusCode(204);

        // Assert
        given().when().get("/blogs/" + id).then().statusCode(404);
    }
}
