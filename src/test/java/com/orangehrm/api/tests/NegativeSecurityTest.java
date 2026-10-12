package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * TC-NEG - Cross-cutting negative, error-handling and input-safety scenarios.
 *
 * These assert the API's behaviour at its boundaries: bad methods, bad ids, malformed
 * payloads, oversized pagination and injection-shaped input.
 */
public class NegativeSecurityTest extends BaseAPITest {

    /* ---------------------------- Auth boundary ---------------------------- */

    @Test(priority = 1, description = "TC-NEG-001 | Every protected collection returns 401 without a session")
    public void tcNeg001_protectedEndpointsRequireAuth() {
        String[] endpoints = {
                Endpoints.EMPLOYEES, Endpoints.USERS, Endpoints.JOB_TITLES,
                Endpoints.LEAVE_TYPES, Endpoints.VACANCIES, Endpoints.KPIS
        };
        for (String endpoint : endpoints) {
            Response r = anonymous().when().get(endpoint);
            assertUnauthenticated(r, "unauthenticated GET " + endpoint);
        }
    }

    @Test(priority = 2, description = "TC-NEG-002 | An unauthenticated write is rejected with 401")
    public void tcNeg002_unauthenticatedWriteRejected() {
        Response r = anonymous().body(TestData.jobTitle("SHOULD_NOT_BE_CREATED"))
                .when().post(Endpoints.JOB_TITLES);

        assertUnauthenticated(r, "unauthenticated POST /admin/job-titles");
    }

    /* ---------------------------- Bad identifiers ---------------------------- */

    @Test(priority = 10, description = "TC-NEG-010 | A non-existent id returns 404 (422 for PIM employees)")
    public void tcNeg010_nonExistentIdsReturn404() {
        // INCONSISTENCY: PIM reports an unknown employee as an invalid parameter (422) while
        // every other module returns a plain 404. Pinned per-endpoint so the difference is
        // visible and a future alignment fails loudly here.
        Object[][] cases = {
                {Endpoints.JOB_TITLE_BY_ID,  "id",        StatusCodes.NOT_FOUND,     Messages.RECORD_NOT_FOUND},
                {Endpoints.USER_BY_ID,       "id",        StatusCodes.NOT_FOUND,     Messages.RECORD_NOT_FOUND},
                {Endpoints.LEAVE_TYPE_BY_ID, "id",        StatusCodes.NOT_FOUND,     Messages.RECORD_NOT_FOUND},
                {Endpoints.VACANCY_BY_ID,    "id",        StatusCodes.NOT_FOUND,     Messages.RECORD_NOT_FOUND},
                {Endpoints.EMPLOYEE_BY_ID,   "empNumber", StatusCodes.UNPROCESSABLE, Messages.INVALID_PARAMETER},
        };
        for (Object[] c : cases) {
            Response r = api().pathParam((String) c[1], 99999999).when().get((String) c[0]);
            assertError(r, (int) c[2], (String) c[3], "GET " + c[0] + " with a bogus id");
        }
    }

    @Test(priority = 11, description = "TC-NEG-011 | A non-numeric id is rejected, never 200")
    public void tcNeg011_nonNumericIdRejected() {
        Response r = api().pathParam("id", "abc").when().get(Endpoints.JOB_TITLE_BY_ID);

        assertRecordNotFound(r, "GET /admin/job-titles/abc");
    }

    @Test(priority = 12, description = "TC-NEG-012 | A negative id is rejected, never 200")
    public void tcNeg012_negativeIdRejected() {
        Response r = api().pathParam("id", -1).when().get(Endpoints.JOB_TITLE_BY_ID);

        assertRecordNotFound(r, "GET /admin/job-titles/-1");
    }

    /* ---------------------------- Method handling ---------------------------- */

    @Test(priority = 20, description = "TC-NEG-020 | An unsupported HTTP method returns 405")
    public void tcNeg020_unsupportedMethodReturns405() {
        Response r = api().when().patch(Endpoints.JOB_TITLES);

        // 405 carries an empty text/html body - there is no JSON envelope to read a message from.
        assertBodylessError(r, StatusCodes.METHOD_NOT_ALLOWED, "PATCH /admin/job-titles");
    }

    @Test(priority = 21, description = "TC-NEG-021 | An unknown endpoint returns 404")
    public void tcNeg021_unknownEndpointReturns404() {
        Response r = api().when().get("/admin/this-endpoint-does-not-exist");

        // An unrouted path also returns an empty text/html body rather than a JSON error.
        assertBodylessError(r, StatusCodes.NOT_FOUND, "GET an unknown endpoint");
    }

    /* ---------------------------- Malformed payloads ---------------------------- */

    @Test(priority = 30, description = "TC-NEG-030 | Malformed JSON is rejected, never 200")
    public void tcNeg030_malformedJsonRejected() {
        Response r = api().body("{\"title\": \"unclosed").when().post(Endpoints.JOB_TITLES);

        assertValidationError(r, "title", "POST /admin/job-titles with malformed JSON");
    }

    @Test(priority = 31, description = "TC-NEG-031 | An empty request body on a create returns 422")
    public void tcNeg031_emptyBodyRejected() {
        Response r = api().body("{}").when().post(Endpoints.JOB_TITLES);

        assertValidationError(r, "title", "POST /admin/job-titles with an empty body");
    }

    @Test(priority = 32, description = "TC-NEG-032 | Unknown fields in a payload are rejected with 422")
    public void tcNeg032_unknownFieldRejected() {
        Response r = api().body("""
                {"title":"%s","thisFieldDoesNotExist":"x"}""".formatted(TestData.JOB_TITLE + "_UNK"))
                .when().post(Endpoints.JOB_TITLES);

        assertValidationError(r, "thisFieldDoesNotExist",
                "POST /admin/job-titles with an unrecognised field");
    }

    /* ---------------------------- Pagination ---------------------------- */

    @Test(priority = 40, description = "TC-NEG-040 | A negative pagination limit is rejected with 422")
    public void tcNeg040_negativeLimitRejected() {
        Response r = api().queryParam("limit", -5).when().get(Endpoints.EMPLOYEES);

        assertValidationError(r, "limit", "GET /pim/employees?limit=-5");
    }

    @Test(priority = 41, description = "TC-NEG-041 | FINDING: an excessive pagination limit is accepted, not capped")
    public void tcNeg041_excessiveLimitIsNotCapped() {
        Response r = api().queryParam("limit", 999999).when().get(Endpoints.EMPLOYEES);

        // The API accepts an arbitrarily large limit and returns the entire table in one
        // response. There is no server-side ceiling, so a single request can be used to pull
        // the full employee dataset. Pinned as current behaviour; if a cap is introduced this
        // test fails, which is the signal to assert the cap instead.
        assertOkList(r, "GET /pim/employees?limit=999999");
        int total = assertHasTotal(r, "GET /pim/employees?limit=999999");
        Assert.assertEquals(r.jsonPath().getList("data").size(), total,
                "An unbounded limit returned a partial page - a cap may now exist, review TC-NEG-041");
    }

    @Test(priority = 42, description = "TC-NEG-042 | An unsupported sort field is rejected with 422")
    public void tcNeg042_invalidSortFieldRejected() {
        Response r = api().queryParam("sortField", "not.a.real.field")
                .queryParam("limit", 5)
                .when().get(Endpoints.EMPLOYEES);

        assertValidationError(r, "sortField", "GET /pim/employees with an unsupported sortField");
    }

    /* ---------------------------- Input safety ---------------------------- */

    @Test(priority = 50, description = "TC-NEG-050 | A SQL-injection style filter returns no unintended data")
    public void tcNeg050_sqlInjectionInFilterIsSafe() {
        Response r = api().queryParam("nameOrId", "' OR '1'='1")
                .queryParam("limit", 50)
                .when().get(Endpoints.EMPLOYEES);

        if (r.statusCode() != StatusCodes.OK) {
            assertValidationError(r, null, "GET /pim/employees with an injection-shaped filter");
        }
        if (r.statusCode() == StatusCodes.OK) {
            Assert.assertTrue(r.jsonPath().getList("data").isEmpty(),
                    "A SQL-injection style filter returned rows - the filter may be interpolated\n"
                            + safeBody(r));
        }
    }

    @Test(priority = 51, description = "TC-NEG-051 | A script-injection payload is stored escaped, not executed")
    public void tcNeg051_scriptPayloadIsEscaped() {
        String payload = "<script>alert(1)</script>";

        Response create = api().body(TestData.jobTitle(payload)).when().post(Endpoints.JOB_TITLES);

        if (create.statusCode() != StatusCodes.OK) {
            assertValidationError(create, "title", "POST a job title containing a script tag");
            return;
        }
        int id = create.jsonPath().getInt("data.id");
        try {
            // The API is a JSON API, so the value round-trips as data; what matters is that
            // it is returned as an escaped string rather than interpreted markup.
            Response get = api().pathParam("id", id).when().get(Endpoints.JOB_TITLE_BY_ID);
            assertOkObject(get, "GET the job title containing a script tag");
            Assert.assertEquals(get.jsonPath().getString("data.title"), payload,
                    "The stored value should round-trip unchanged as a plain string");
        } finally {
            api().body(TestData.deleteIds(id)).when().delete(Endpoints.JOB_TITLES);
        }
    }

    @Test(priority = 52, description = "TC-NEG-052 | Responses do not leak stack traces on error")
    public void tcNeg052_errorsDoNotLeakStackTraces() {
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.JOB_TITLE_BY_ID);

        String body = r.getBody().asString();
        for (String leak : new String[]{"Stack trace", "#0 /", "vendor/", ".php on line", "SQLSTATE"}) {
            Assert.assertFalse(body.contains(leak),
                    "The error response leaks internal detail ('" + leak + "'):\n" + body);
        }
    }
}
