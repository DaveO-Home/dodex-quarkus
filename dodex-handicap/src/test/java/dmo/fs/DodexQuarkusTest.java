package dmo.fs;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

 @QuarkusTest
public class DodexQuarkusTest {

    @Test
    public void testDodexEndpoint() {
        given()
          .when().get("/test")
          .then()
             .statusCode(200).body(containsString("dodex--open"))
        ;
    }
}