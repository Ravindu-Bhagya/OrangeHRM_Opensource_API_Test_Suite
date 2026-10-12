package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * TC-AUTH - Authentication, session handling and the unauthenticated access boundary.
 */
public class AuthTest extends BaseAPITest {

    @Test(priority = 1, description = "TC-AUTH-001 | Login page is reachable and exposes a CSRF token")
    public void tcAuth001_loginPageServesCsrfToken() {
        Response r = RestAssured.given().relaxedHTTPSValidation()
                .when().get(Credentials.LOGIN_PAGE);

        assertStatus(r, StatusCodes.OK, "GET login page");
        String token = extractToken(r.asString());
        Assert.assertNotNull(token, "Login page did not expose a :token attribute");
        Assert.assertTrue(token.length() > 20, "CSRF token looks too short: " + token);
        Assert.assertTrue(r.asString().contains("auth-login"),
                "Login page did not render the auth-login component");
    }

    @Test(priority = 2, description = "TC-AUTH-002 | Valid credentials return 302 redirecting to the dashboard")
    public void tcAuth002_loginWithValidCredentials() {
        Response page = RestAssured.given().relaxedHTTPSValidation()
                .when().get(Credentials.LOGIN_PAGE);
        String csrf = extractToken(page.asString());

        Response r = RestAssured.given().relaxedHTTPSValidation()
                .cookies(page.getCookies())
                .contentType(ContentType.URLENC)
                .formParam("_token", csrf)
                .formParam("username", Credentials.ADMIN_USERNAME)
                .formParam("password", Credentials.ADMIN_PASSWORD)
                .redirects().follow(false)
                .when().post(Credentials.VALIDATE);

        assertStatus(r, StatusCodes.FOUND_REDIRECT, "POST /auth/validate with valid credentials");
        Assert.assertTrue(r.header("Location").contains("/dashboard"),
                "Expected a redirect to the dashboard but got: " + r.header("Location"));
    }

    @Test(priority = 3, description = "TC-AUTH-003 | Invalid credentials redirect back to the login page")
    public void tcAuth003_loginWithInvalidCredentials() {
        Response page = RestAssured.given().relaxedHTTPSValidation()
                .when().get(Credentials.LOGIN_PAGE);
        String csrf = extractToken(page.asString());

        Response r = RestAssured.given().relaxedHTTPSValidation()
                .cookies(page.getCookies())
                .contentType(ContentType.URLENC)
                .formParam("_token", csrf)
                .formParam("username", Credentials.INVALID_USERNAME)
                .formParam("password", Credentials.INVALID_PASSWORD)
                .redirects().follow(false)
                .when().post(Credentials.VALIDATE);

        assertStatus(r, StatusCodes.FOUND_REDIRECT, "POST /auth/validate with invalid credentials");
        Assert.assertTrue(r.header("Location").contains("/auth/login"),
                "Invalid credentials should bounce back to /auth/login, got: " + r.header("Location"));
        Assert.assertFalse(r.header("Location").contains("/dashboard"),
                "SECURITY: invalid credentials reached the dashboard");
    }

    @Test(priority = 4, description = "TC-AUTH-004 | Correct username with a wrong password is rejected")
    public void tcAuth004_loginWithWrongPassword() {
        Response page = RestAssured.given().relaxedHTTPSValidation()
                .when().get(Credentials.LOGIN_PAGE);

        Response r = RestAssured.given().relaxedHTTPSValidation()
                .cookies(page.getCookies())
                .contentType(ContentType.URLENC)
                .formParam("_token", extractToken(page.asString()))
                .formParam("username", Credentials.ADMIN_USERNAME)
                .formParam("password", Credentials.INVALID_PASSWORD)
                .redirects().follow(false)
                .when().post(Credentials.VALIDATE);

        assertStatus(r, StatusCodes.FOUND_REDIRECT, "POST /auth/validate with a wrong password");
        Assert.assertTrue(r.header("Location").contains("/auth/login"),
                "A wrong password must not authenticate");
    }

    @Test(priority = 5, description = "TC-AUTH-005 | Login with an empty payload is rejected")
    public void tcAuth005_loginWithEmptyCredentials() {
        Response page = RestAssured.given().relaxedHTTPSValidation()
                .when().get(Credentials.LOGIN_PAGE);

        Response r = RestAssured.given().relaxedHTTPSValidation()
                .cookies(page.getCookies())
                .contentType(ContentType.URLENC)
                .formParam("_token", extractToken(page.asString()))
                .formParam("username", "")
                .formParam("password", "")
                .redirects().follow(false)
                .when().post(Credentials.VALIDATE);

        assertStatus(r, StatusCodes.FOUND_REDIRECT, "POST /auth/validate with empty credentials");
        Assert.assertTrue(r.header("Location").contains("/auth/login"),
                "Empty credentials must not authenticate");
    }

    @Test(priority = 6, description = "TC-AUTH-006 | Login with a missing/invalid CSRF token is rejected")
    public void tcAuth006_loginWithInvalidCsrfToken() {
        Response r = RestAssured.given().relaxedHTTPSValidation()
                .contentType(ContentType.URLENC)
                .formParam("_token", "not-a-valid-csrf-token")
                .formParam("username", Credentials.ADMIN_USERNAME)
                .formParam("password", Credentials.ADMIN_PASSWORD)
                .redirects().follow(false)
                .when().post(Credentials.VALIDATE);

        Assert.assertNotEquals(r.header("Location"), Credentials.WEB_BASE + "/dashboard/index",
                "SECURITY: a forged CSRF token was accepted");
        Assert.assertTrue(r.statusCode() >= 300,
                "Expected a rejection for a forged CSRF token, got " + r.statusCode());
    }

    @Test(priority = 7, description = "TC-AUTH-007 | An unauthenticated API call returns 401")
    public void tcAuth007_apiCallWithoutSessionReturns401() {
        Response r = anonymous().when().get(Endpoints.JOB_TITLES);

        assertUnauthenticated(r, "GET /admin/job-titles without a session");
    }

    @Test(priority = 8, description = "TC-AUTH-008 | An invalid session cookie returns 401")
    public void tcAuth008_apiCallWithForgedCookieReturns401() {
        Map<String, String> forged = new HashMap<>();
        forged.put("orangehrm", "forged-session-value-000000");

        Response r = RestAssured.given().relaxedHTTPSValidation()
                .cookies(forged)
                .accept(ContentType.JSON)
                .baseUri(Credentials.API_BASE)
                .when().get(Endpoints.EMPLOYEES);

        assertUnauthenticated(r, "GET /pim/employees with a forged cookie");
    }

    @Test(priority = 9, description = "TC-AUTH-009 | A valid session reaches a protected resource")
    public void tcAuth009_validSessionReachesProtectedResource() {
        Response r = api().queryParam("limit", 1).when().get(Endpoints.EMPLOYEES);

        assertOkList(r, "GET /pim/employees with a valid session");
        assertHasTotal(r, "GET /pim/employees");
    }

    @Test(priority = 10, description = "TC-AUTH-010 | The openid-providers endpoint is publicly readable")
    public void tcAuth010_openIdProvidersIsPublic() {
        Response r = api().when().get(Endpoints.OPENID_PROVIDERS);

        if (r.statusCode() == StatusCodes.OK) {
            assertOkList(r, "GET /auth/openid-providers");
        } else {
            assertRecordNotFound(r, "GET /auth/openid-providers");
        }
    }
}
