package com.orangehrm.api.core;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import com.orangehrm.api.config.Credentials;
import com.orangehrm.api.config.InstanceProfile;
import com.orangehrm.api.config.Messages;
import com.orangehrm.api.config.StatusCodes;
import com.orangehrm.api.config.TestData;
import com.orangehrm.api.config.UserData;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared base for every OrangeHRM API test class.
 *
 * Responsibilities:
 *   - session-cookie authentication (OrangeHRM OS exposes no bearer-token API; its Vue
 *     SPA scrapes a CSRF token, posts it to /auth/validate, then rides a session cookie)
 *   - authenticated and anonymous request specifications
 *   - assertion helpers that always check BOTH the status code and the response body
 *   - a cleanup registry, so a suite run on a daily schedule leaves no residue behind
 *   - result capture and a PDF summary report
 */
public class BaseAPITest {

    protected static Map<String, String> SESSION_COOKIES = new HashMap<>();

    private static final List<String[]> RESULTS = new ArrayList<>();
    private static final AtomicInteger PASSED  = new AtomicInteger();
    private static final AtomicInteger FAILED  = new AtomicInteger();
    private static final AtomicInteger SKIPPED = new AtomicInteger();
    private static long suiteStart;

    private static final Pattern TOKEN_PATTERN = Pattern.compile(":token=\"&quot;(.+?)&quot;\"");

    /** Records created during the run: collection endpoint -> ids, deleted in @AfterSuite. */
    private static final Map<String, List<Object>> CLEANUP = new LinkedHashMap<>();

    /* ================================================================== */
    /* Authentication                                                      */
    /* ================================================================== */

    @BeforeSuite(alwaysRun = true)
    public void authenticate() {
        if (!SESSION_COOKIES.isEmpty()) return;      // once per JVM
        suiteStart = System.currentTimeMillis();
        RestAssured.useRelaxedHTTPSValidation();
        login();
        System.out.println("Authenticated against " + Credentials.BASE_HOST
                + " as '" + Credentials.ADMIN_USERNAME + "'");
        purgeLeftoversFromPreviousRuns();
    }

    /** Scrapes the CSRF token from the login page and exchanges credentials for a session. */
    protected static void login() {
        Response page = RestAssured.given()
                .relaxedHTTPSValidation()
                .when().get(Credentials.LOGIN_PAGE);

        Assert.assertEquals(page.statusCode(), StatusCodes.OK,
                "Login page unreachable at " + Credentials.LOGIN_PAGE);

        String csrf = extractToken(page.asString());
        Assert.assertNotNull(csrf, "Could not scrape the CSRF :token from the login page");

        Map<String, String> cookies = new HashMap<>(page.getCookies());

        Response validate = RestAssured.given()
                .relaxedHTTPSValidation()
                .cookies(cookies)
                .contentType(ContentType.URLENC)
                .formParam("_token", csrf)
                .formParam("username", Credentials.ADMIN_USERNAME)
                .formParam("password", Credentials.ADMIN_PASSWORD)
                .redirects().follow(false)
                .when().post(Credentials.VALIDATE);

        Assert.assertEquals(validate.statusCode(), StatusCodes.FOUND_REDIRECT,
                "Expected a 302 from /auth/validate, got " + validate.statusCode());

        String location = validate.header("Location");
        Assert.assertTrue(location != null && location.contains("/dashboard"),
                "Login failed - redirected to '" + location + "' instead of the dashboard. "
                        + "Check -Dohrm.username / -Dohrm.password.");

        cookies.putAll(validate.getCookies());
        SESSION_COOKIES = cookies;
    }

    protected static String extractToken(String html) {
        Matcher m = TOKEN_PATTERN.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    /* ================================================================== */
    /* Request specifications                                              */
    /* ================================================================== */

    /** Authenticated JSON request against /api/v2. */
    protected static RequestSpecification api() {
        return RestAssured.given()
                .relaxedHTTPSValidation()
                .cookies(SESSION_COOKIES)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .baseUri(Credentials.API_BASE);
    }

    /** Anonymous request - drives the 401 auth-boundary tests. */
    protected static RequestSpecification anonymous() {
        return RestAssured.given()
                .relaxedHTTPSValidation()
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .baseUri(Credentials.API_BASE);
    }

    /* ================================================================== */
    /* Assertion helpers - status code AND response body                   */
    /* ================================================================== */

    protected static Response assertStatus(Response r, int expected, String what) {
        Assert.assertEquals(r.statusCode(), expected,
                "Status code mismatch | " + what + "\nResponse: " + safeBody(r) + "\n");
        return r;
    }

    /** 200 + a JSON array under "data". */
    protected static JsonPath assertOkList(Response r, String what) {
        assertStatus(r, StatusCodes.OK, what);
        JsonPath jp = r.jsonPath();
        Object data = jp.get("data");
        Assert.assertNotNull(data, "'data' missing | " + what + "\nResponse: " + safeBody(r));
        Assert.assertTrue(data instanceof List,
                "'data' should be a JSON array | " + what + "\nResponse: " + safeBody(r));
        return jp;
    }

    /** 200 + a JSON object under "data". */
    protected static JsonPath assertOkObject(Response r, String what) {
        assertStatus(r, StatusCodes.OK, what);
        JsonPath jp = r.jsonPath();
        Object data = jp.get("data");
        Assert.assertNotNull(data, "'data' missing | " + what + "\nResponse: " + safeBody(r));
        Assert.assertTrue(data instanceof Map,
                "'data' should be a JSON object | " + what + "\nResponse: " + safeBody(r));
        return jp;
    }

    /** Asserts a JSON path equals an expected value. */
    protected static void assertField(Response r, String path, Object expected, String what) {
        Assert.assertEquals(r.jsonPath().get(path), expected,
                "Field '" + path + "' mismatch | " + what + "\nResponse: " + safeBody(r) + "\n");
    }

    /** Asserts a JSON path is present and non-null. */
    protected static void assertFieldPresent(Response r, String path, String what) {
        Assert.assertNotNull(r.jsonPath().get(path),
                "Field '" + path + "' missing | " + what + "\nResponse: " + safeBody(r) + "\n");
    }

    /** Asserts every element of a list field is non-null (schema sanity on collections). */
    protected static void assertEachHas(Response r, String listPath, String what) {
        List<Object> values = r.jsonPath().getList(listPath);
        Assert.assertNotNull(values, "'" + listPath + "' missing | " + what
                + "\nResponse: " + safeBody(r));
        for (Object v : values) {
            Assert.assertNotNull(v, "Null entry in '" + listPath + "' | " + what
                    + "\nResponse: " + safeBody(r));
        }
    }

    /** Asserts error.message equals the expected text. */
    protected static void assertMessage(Response r, String expectedMessage, String what) {
        String actual = r.jsonPath().getString("error.message");
        Assert.assertNotNull(actual,
                "Expected an error.message of '" + expectedMessage + "' | " + what
                        + "\nResponse: " + safeBody(r));
        Assert.assertEquals(actual, expectedMessage,
                "Error message mismatch | " + what + "\nResponse: " + safeBody(r) + "\n");
    }

    /**
     * Asserts status code + error.message together, plus that error.status echoes the code.
     * This is the standard error assertion - prefer it over a bare status check.
     */
    protected static void assertError(Response r, int status, String expectedMessage, String what) {
        assertStatus(r, status, what);
        Assert.assertNotNull(r.jsonPath().get("error"),
                "Expected an 'error' object | " + what + "\nResponse: " + safeBody(r));
        assertMessage(r, expectedMessage, what);
        // The envelope repeats the status inside the body; it is a string on some routes.
        Object echoed = r.jsonPath().get("error.status");
        if (echoed != null) {
            Assert.assertEquals(String.valueOf(echoed), String.valueOf(status),
                    "error.status does not echo the HTTP status | " + what
                            + "\nResponse: " + safeBody(r));
        }
    }

    /** 401 - no session, expired session or forged cookie. */
    protected static void assertUnauthenticated(Response r, String what) {
        assertError(r, StatusCodes.UNAUTHORIZED, Messages.SESSION_EXPIRED, what);
    }

    /** 403 - authenticated but not permitted. */
    protected static void assertForbidden(Response r, String what) {
        assertError(r, StatusCodes.FORBIDDEN, Messages.UNAUTHORIZED, what);
    }

    /**
     * Admin access to a /time data endpoint, per the instance profile: a 200 list where
     * the instance grants it, the standard 403 where it does not.
     */
    protected static void assertAdminTimeAccess(Response r, String what) {
        if (InstanceProfile.ADMIN_CAN_READ_TIME) {
            assertOkList(r, what);
        } else {
            assertForbidden(r, what);
        }
    }

    /** 404 for a single missing record ("Record Not Found"). */
    protected static void assertRecordNotFound(Response r, String what) {
        assertError(r, StatusCodes.NOT_FOUND, Messages.RECORD_NOT_FOUND, what);
    }

    /** 404 for a bulk delete matching nothing ("Records Not Found" - plural). */
    protected static void assertRecordsNotFound(Response r, String what) {
        assertError(r, StatusCodes.NOT_FOUND, Messages.RECORDS_NOT_FOUND, what);
    }

    /** 501 - route present but not implemented. */
    protected static void assertNotImplemented(Response r, String what) {
        assertError(r, StatusCodes.NOT_IMPLEMENTED, Messages.NOT_IMPLEMENTED, what);
    }

    /**
     * 405 and unknown routes return an EMPTY text/html body - there is no JSON envelope, so
     * there is no message to assert. Verifies the code and that no error body was produced.
     */
    protected static void assertBodylessError(Response r, int status, String what) {
        assertStatus(r, status, what);
        String body = r.getBody().asString();
        Assert.assertTrue(body == null || body.isBlank(),
                "Expected an empty body for " + status + " | " + what + "\nResponse: " + body);
    }

    /**
     * Asserts the 422 envelope: status, the "Invalid Parameter" message, and that
     * invalidParamKeys names the expected field.
     */
    protected static void assertValidationError(Response r, String expectedKey, String what) {
        assertError(r, StatusCodes.UNPROCESSABLE, Messages.INVALID_PARAMETER, what);
        List<String> keys = r.jsonPath().getList("error.data.invalidParamKeys");
        Assert.assertNotNull(keys, "invalidParamKeys missing | " + what
                + "\nResponse: " + safeBody(r));
        if (expectedKey != null) {
            Assert.assertTrue(keys.contains(expectedKey),
                    "Expected invalidParamKeys to contain '" + expectedKey + "' | " + what
                            + " but got " + keys);
        }
    }

    /** Kept for call sites that only need the envelope shape plus a known message. */
    protected static void assertErrorEnvelope(Response r, int status, String expectedMessage,
                                              String what) {
        assertError(r, status, expectedMessage, what);
    }

    /** Asserts the paginated envelope carries a non-negative meta.total, and returns it. */
    protected static int assertHasTotal(Response r, String what) {
        Integer total = r.jsonPath().get("meta.total");
        Assert.assertNotNull(total, "meta.total missing | " + what + "\nResponse: " + safeBody(r));
        Assert.assertTrue(total >= 0, "meta.total should be >= 0 | " + what);
        return total;
    }

    protected static String safeBody(Response r) {
        try {
            String b = r.getBody().asPrettyString();
            return b.length() > 1200 ? b.substring(0, 1200) + " ...[truncated]" : b;
        } catch (Exception e) {
            return "<unreadable body>";
        }
    }

    /* ================================================================== */
    /* Fixtures & reference-data lookups                                   */
    /* ================================================================== */

    /** First id from a collection, or null when empty / inaccessible. */
    protected static Integer firstId(String path) {
        Response r = api().queryParam("limit", 1).when().get(path);
        if (r.statusCode() != StatusCodes.OK) return null;
        List<Integer> ids = r.jsonPath().getList("data.id");
        return (ids == null || ids.isEmpty()) ? null : ids.get(0);
    }

    /** empNumber of the first employee in PIM. */
    protected static Integer firstEmpNumber() {
        Response r = api().queryParam("limit", 1).when().get("/pim/employees");
        if (r.statusCode() != StatusCodes.OK) return null;
        List<Integer> ids = r.jsonPath().getList("data.empNumber");
        return (ids == null || ids.isEmpty()) ? null : ids.get(0);
    }

    private static Integer fixtureJobTitleId;

    /**
     * A job title to hang vacancies and KPIs on. Uses an existing one when the instance has
     * any; on an empty instance it creates a QA_AUT_ job title once per run, which the cleanup
     * registry (or the next run's sweep) removes.
     */
    protected static synchronized int jobTitleFixture() {
        Integer existing = firstId("/admin/job-titles");
        if (existing != null) return existing;
        if (fixtureJobTitleId != null) return fixtureJobTitleId;
        Response r = api().body(TestData.jobTitle(TestData.JOB_TITLE + "_FIX"))
                .when().post("/admin/job-titles");
        if (r.statusCode() != StatusCodes.OK) {
            throw new SkipException("Prerequisite unavailable: could not create a job title fixture ("
                    + r.statusCode() + ")");
        }
        fixtureJobTitleId = r.jsonPath().getInt("data.id");
        registerForCleanup("/admin/job-titles", fixtureJobTitleId);
        return fixtureJobTitleId;
    }

    /** Skips (rather than fails) a test whose prerequisite fixture is unavailable. */
    protected static <T> T require(T value, String what) {
        if (value == null) throw new SkipException("Prerequisite unavailable: " + what);
        return value;
    }

    /* ================================================================== */
    /* Cleanup registry - keeps daily runs from accumulating test data     */
    /* ================================================================== */

    /** Registers a created record for deletion at the end of the suite. */
    protected static void registerForCleanup(String collectionEndpoint, Object id) {
        if (id == null) return;
        CLEANUP.computeIfAbsent(collectionEndpoint, k -> new ArrayList<>()).add(id);
    }

    /** Drops a record from the registry - call after a test deletes it itself. */
    protected static void unregisterCleanup(String collectionEndpoint, Object id) {
        List<Object> ids = CLEANUP.get(collectionEndpoint);
        if (ids != null) ids.remove(id);
    }

    @AfterSuite(alwaysRun = true)
    public void cleanupCreatedData() {
        if (CLEANUP.isEmpty()) return;
        System.out.println("\n--- Cleaning up records created by this run ---");
        // Reverse order so dependents (candidates) go before their parents (vacancies).
        List<String> endpoints = new ArrayList<>(CLEANUP.keySet());
        Collections.reverse(endpoints);
        for (String endpoint : endpoints) {
            List<Object> ids = CLEANUP.get(endpoint);
            if (ids == null || ids.isEmpty()) continue;
            try {
                Response r = api().body(TestData.deleteIds(ids.toArray()))
                        .when().delete(endpoint);
                System.out.printf("  DELETE %-38s %s -> %d%n", endpoint, ids, r.statusCode());
            } catch (Exception e) {
                System.out.printf("  DELETE %-38s %s -> error: %s%n", endpoint, ids, e.getMessage());
            }
        }
    }

    /* ================================================================== */
    /* Pre-run sweep - makes the suite re-runnable on the same instance    */
    /* ================================================================== */

    /**
     * Collections the suite writes to, in delete order (dependents before parents), with the
     * field that carries the QA_AUT_ marker. Holidays need a date range to be listable.
     */
    private static final String[][] PURGE_TARGETS = {
            { "/recruitment/candidates",      "email"       },
            { "/recruitment/vacancies",       "name"        },
            { "/performance/config/trackers", "trackerName" },
            { "/performance/kpis",            "title"       },
            { "/admin/users",                 "userName"    },
            { "/pim/employees",               "middleName"  },
            { "/leave/holidays",              "name"        },
            { "/leave/leave-types",           "name"        },
            { "/claim/events",                "name"        },
            { "/claim/expenses/types",        "name"        },
            { "/admin/job-titles",            "title"       },
            { "/admin/employment-statuses",   "name"        },
            { "/admin/pay-grades",            "name"        },
            { "/admin/work-shifts",           "name"        },
            { "/admin/locations",             "name"        },
            { "/admin/skills",                "name"        },
            { "/admin/educations",            "name"        },
            { "/admin/licenses",              "name"        },
            { "/admin/languages",             "name"        },
            { "/admin/memberships",           "name"        },
            { "/admin/job-categories",        "name"        },
    };

    /**
     * Deletes records left behind by earlier runs that never reached @AfterSuite (aborted with
     * Ctrl+C, a wedged JVM, a crash). Only rows carrying this suite's marker are touched: the
     * QA_AUT_ prefix, the QA_AUT middle name on employees, or the qa.candidate. email.
     * Best-effort: a collection that cannot be listed or deleted is logged, never fatal.
     *
     * Do not run two suites against the same instance at once - each sweeps the other's data.
     */
    protected static void purgeLeftoversFromPreviousRuns() {
        if (Boolean.getBoolean("ohrm.skipPurge")) return;
        System.out.println("--- Sweeping leftovers from previous runs ---");
        for (String[] t : PURGE_TARGETS) {
            String endpoint = t[0], field = t[1];
            try {
                RequestSpecification q = api().queryParam("limit", 500);
                if (endpoint.equals("/leave/holidays")) {
                    q.queryParam("fromDate", UserData.daysFromNow(-400))
                     .queryParam("toDate", UserData.daysFromNow(800));
                }
                Response list = q.when().get(endpoint);
                if (list.statusCode() != StatusCodes.OK) continue;
                List<Map<String, Object>> rows = list.jsonPath().getList("data");
                if (rows == null) continue;

                String idKey = endpoint.equals("/pim/employees") ? "empNumber" : "id";
                List<Object> ids = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    Object v = row.get(field);
                    if (v instanceof String str && isSuiteMarked(str)) ids.add(row.get(idKey));
                }
                if (ids.isEmpty()) continue;
                Response del = api().body(TestData.deleteIds(ids.toArray())).when().delete(endpoint);
                System.out.printf("  purge %-30s %d leftover(s) -> %d%n",
                        endpoint, ids.size(), del.statusCode());
            } catch (Exception e) {
                System.out.printf("  purge %-30s error: %s%n", endpoint, e.getMessage());
            }
        }
    }

    private static boolean isSuiteMarked(String value) {
        return value.startsWith(UserData.TEST_PREFIX)
                || value.equals(UserData.EMP_MIDDLE_NAME)
                || value.startsWith(UserData.CANDIDATE_EMAIL_PREFIX);
    }

    /* ================================================================== */
    /* Reporting                                                           */
    /* ================================================================== */

    @AfterMethod(alwaysRun = true)
    public void captureResult(ITestResult result) {
        String name = result.getTestClass().getRealClass().getSimpleName()
                + "." + result.getMethod().getMethodName();
        String desc = result.getMethod().getDescription();
        String status;
        String detail = "";

        switch (result.getStatus()) {
            case ITestResult.SUCCESS -> { status = "PASSED"; PASSED.incrementAndGet(); }
            case ITestResult.FAILURE -> {
                status = "FAILED"; FAILED.incrementAndGet();
                detail = result.getThrowable() == null ? "" : firstLine(result.getThrowable().getMessage());
            }
            default -> {
                status = "SKIPPED"; SKIPPED.incrementAndGet();
                detail = result.getThrowable() == null ? "" : firstLine(result.getThrowable().getMessage());
            }
        }
        RESULTS.add(new String[]{ name, desc == null ? "" : desc, status, detail });
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        String line = s.split("\n")[0];
        return line.length() > 150 ? line.substring(0, 150) + "..." : line;
    }

    @AfterSuite(alwaysRun = true, dependsOnMethods = "cleanupCreatedData")
    public void generatePDFReport() {
        if (RESULTS.isEmpty()) return;

        int total = PASSED.get() + FAILED.get() + SKIPPED.get();
        long secs = (System.currentTimeMillis() - suiteStart) / 1000;

        System.out.printf("%n=== OrangeHRM API Suite: %d tests | %d passed | %d failed | %d skipped | %ds ===%n",
                total, PASSED.get(), FAILED.get(), SKIPPED.get(), secs);

        String path = System.getProperty("user.dir") + "/OrangeHRM-API-TestReport.pdf";
        try (PdfWriter writer = new PdfWriter(new FileOutputStream(path));
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            doc.add(new Paragraph("OrangeHRM OS - API Test Report").setBold().setFontSize(18));
            doc.add(new Paragraph("Target: " + Credentials.BASE_HOST).setFontSize(9));
            doc.add(new Paragraph("Executed: " + new Date()).setItalic().setFontSize(9));
            doc.add(new Paragraph(
                    "Total: %d    Passed: %d    Failed: %d    Skipped: %d    Duration: %ds"
                            .formatted(total, PASSED.get(), FAILED.get(), SKIPPED.get(), secs))
                    .setBold().setFontSize(11));

            Table table = new Table(UnitValue.createPercentArray(new float[]{26, 36, 10, 28}))
                    .useAllAvailableWidth().setFontSize(7);
            for (String h : new String[]{"Test", "Scenario", "Result", "Detail"}) {
                table.addHeaderCell(new Cell().add(new Paragraph(h).setBold().setFontSize(8)));
            }
            for (String[] row : RESULTS) {
                table.addCell(new Cell().add(new Paragraph(row[0])));
                table.addCell(new Cell().add(new Paragraph(row[1])));
                Cell verdict = new Cell().add(new Paragraph(row[2]).setBold());
                verdict.setFontColor(switch (row[2]) {
                    case "PASSED" -> ColorConstants.GREEN;
                    case "FAILED" -> ColorConstants.RED;
                    default       -> ColorConstants.ORANGE;
                });
                table.addCell(verdict);
                table.addCell(new Cell().add(new Paragraph(row[3])));
            }
            doc.add(table);
            System.out.println("PDF report generated: " + path);
        } catch (Exception e) {
            System.out.println("Could not write the PDF report: " + e.getMessage());
        }
    }
}
