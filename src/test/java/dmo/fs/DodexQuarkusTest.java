package dmo.fs;

import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

// @QuarkusTest
@Disabled("Disabled until VertxExtension works with reactivex")
public class DodexQuarkusTest {

    // @Test
    public void testTestEndpoint() {
        given()
          .when().get("/test")
          .then()
             .statusCode(200).body(containsString("dodex--open"))
            ;
    }

}