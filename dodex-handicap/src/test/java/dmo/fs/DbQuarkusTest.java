package dmo.fs;


import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
public class DbQuarkusTest  /*extends DbPostgres*/ {
		@Test
    public void testDefaultEndpoint() {
        given().port(8089)
          .when().get("/")
          .then().log().all()
             .statusCode(200).body(containsString("Welcome"));
    }
}
