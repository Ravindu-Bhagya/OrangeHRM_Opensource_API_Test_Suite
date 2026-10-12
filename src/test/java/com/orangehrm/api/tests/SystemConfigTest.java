package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * TC-CFG - the configuration and reference surfaces the other classes never touch:
 * the shared uniqueness and password validators, i18n, email subscriptions, OAuth
 * clients, pay-grade currency bands, user lookup by id, and the reference lists
 * Recruitment and PIM read from.
 *
 * These are read-mostly routes, so most cases here assert response SHAPE rather than a
 * round trip - a silently reshaped envelope is exactly what breaks a client.
 */
public class SystemConfigTest extends BaseAPITest {

    private static Integer payGradeId;

    /* ================================================================== */
    /* Shared validators                                                   */
    /* ================================================================== */

    @Test(priority = 1, description = "TC-CFG-001 | The unique-value validator reports an unused job title as available")
    public void tcCfg001_uniqueValidatorAcceptsFreeName() {
        Response r = api()
                .queryParam("entityName", "JobTitle")
                .queryParam("attributeName", "jobTitleName")
                .queryParam("value", TestData.JOB_TITLE + "_NEVER_USED")
                .when().get(Endpoints.VALIDATION_UNIQUE);

        assertOkObject(r, "GET /core/validation/unique for an unused name");
        assertField(r, "data.valid", true, "an unused name should validate");
    }

    @Test(priority = 2, description = "TC-CFG-002 | The unique-value validator rejects an unknown entity with 422")
    public void tcCfg002_uniqueValidatorUnknownEntity() {
        Response r = api()
                .queryParam("entityName", "NoSuchEntity")
                .queryParam("attributeName", "nope")
                .queryParam("value", "x")
                .when().get(Endpoints.VALIDATION_UNIQUE);

        assertValidationError(r, "entityName", "GET /core/validation/unique for an unknown entity");
    }

    @Test(priority = 3, description = "TC-CFG-003 | The password validator scores a weak password and lists every unmet rule")
    public void tcCfg003_passwordValidatorRejectsWeakPassword() {
        Response r = anonymous().body(TestData.password("abc"))
                .when().post(Endpoints.PASSWORD_VALIDATION);

        assertOkObject(r, "POST /auth/public/validation/password with 'abc'");
        List<String> messages = r.jsonPath().getList("data.messages");
        Assert.assertNotNull(messages, "Expected a list of unmet password rules");
        Assert.assertFalse(messages.isEmpty(), "A three-character password should break rules");
        assertField(r, "meta.strength", 0, "a weak password scores zero strength");
    }

    @Test(priority = 4, description = "TC-CFG-004 | The password validator is a POST route - GET returns 405")
    public void tcCfg004_passwordValidatorRejectsGet() {
        Response r = anonymous().queryParam("password", "abc")
                .when().get(Endpoints.PASSWORD_VALIDATION);

        assertBodylessError(r, StatusCodes.METHOD_NOT_ALLOWED,
                "GET /auth/public/validation/password");
    }

    @Test(priority = 5, description = "TC-CFG-005 | The password validator is reachable without a session")
    public void tcCfg005_passwordValidatorIsPublic() {
        Response r = anonymous().body(TestData.password("Str0ng!Passw0rd"))
                .when().post(Endpoints.PASSWORD_VALIDATION);

        assertOkObject(r, "POST /auth/public/validation/password anonymously");
        assertFieldPresent(r, "meta.strength", "strength score for a strong password");
    }

    /* ================================================================== */
    /* Localisation & i18n                                                 */
    /* ================================================================== */

    @Test(priority = 10, description = "TC-CFG-010 | GET i18n languages returns the full language catalogue with codes")
    public void tcCfg010_i18nLanguages() {
        Response r = api().queryParam("limit", 5).when().get(Endpoints.I18N_LANGUAGES);

        assertOkList(r, "GET /admin/i18n/languages");
        int total = assertHasTotal(r, "GET /admin/i18n/languages");
        Assert.assertTrue(total > 100,
                "The language catalogue should hold hundreds of entries, got " + total);
        assertEachHas(r, "data.code", "language codes");
        assertEachHas(r, "data.name", "language names");
    }

    @Test(priority = 11, description = "TC-CFG-011 | GET i18n groups returns one translation group per module")
    public void tcCfg011_i18nGroups() {
        Response r = api().when().get(Endpoints.I18N_GROUPS);

        assertOkList(r, "GET /admin/i18n/groups");
        List<String> names = r.jsonPath().getList("data.name");
        Assert.assertTrue(names.contains("admin") && names.contains("pim"),
                "Expected admin and pim translation groups, got " + names);
        assertEachHas(r, "data.title", "group titles");
    }

    @Test(priority = 12, description = "TC-CFG-012 | GET i18n languages honours the pagination limit")
    public void tcCfg012_i18nLanguagesRespectLimit() {
        Response r = api().queryParam("limit", 3).when().get(Endpoints.I18N_LANGUAGES);

        assertOkList(r, "GET /admin/i18n/languages?limit=3");
        List<Object> data = r.jsonPath().getList("data");
        Assert.assertEquals(data.size(), 3, "limit=3 should return exactly three languages");
    }

    /* ================================================================== */
    /* Notifications & integrations                                        */
    /* ================================================================== */

    @Test(priority = 20, description = "TC-CFG-020 | GET email subscriptions lists each notification with its enabled flag")
    public void tcCfg020_emailSubscriptions() {
        Response r = api().when().get(Endpoints.EMAIL_SUBSCRIPTIONS);

        assertOkList(r, "GET /admin/email-subscriptions");
        List<Map<String, Object>> data = r.jsonPath().getList("data");
        Assert.assertFalse(data.isEmpty(), "The instance ships with stock email subscriptions");
        assertEachHas(r, "data.name", "subscription names");
        for (Map<String, Object> row : data) {
            Assert.assertTrue(row.get("isEnabled") instanceof Boolean,
                    "isEnabled should be a boolean, got " + row.get("isEnabled"));
            Assert.assertTrue(row.get("subscribers") instanceof List,
                    "subscribers should be an array, got " + row.get("subscribers"));
        }
    }

    @Test(priority = 21, description = "TC-CFG-021 | GET OAuth clients includes the mobile app client with its redirect URI")
    public void tcCfg021_oauthClients() {
        Response r = api().when().get(Endpoints.OAUTH_CLIENTS);

        assertOkList(r, "GET /admin/oauth-clients");
        List<String> clientIds = r.jsonPath().getList("data.clientId");
        Assert.assertTrue(clientIds.contains("orangehrm_mobile_app"),
                "The stock mobile OAuth client should be present, got " + clientIds);
        assertEachHas(r, "data.redirectUri", "OAuth redirect URIs");
    }

    @Test(priority = 22, description = "TC-CFG-022 | An OAuth client secret is never exposed on the collection")
    public void tcCfg022_oauthSecretNotExposed() {
        Response r = api().when().get(Endpoints.OAUTH_CLIENTS);

        assertOkList(r, "GET /admin/oauth-clients");
        String body = r.getBody().asString();
        Assert.assertFalse(body.contains("clientSecret"),
                "The OAuth client list must not carry clientSecret: " + safeBody(r));
    }

    /* ================================================================== */
    /* Pay-grade currency bands                                            */
    /* ================================================================== */

    @Test(priority = 30, description = "TC-CFG-030 | A pay grade is created to carry a currency band")
    public void tcCfg030_createPayGrade() {
        Response r = api().body(TestData.payGrade(TestData.PAY_GRADE + "_CFG"))
                .when().post(Endpoints.PAY_GRADES);

        assertOkObject(r, "POST /admin/pay-grades");
        payGradeId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.PAY_GRADES, payGradeId);
        assertField(r, "data.currencies", List.of(), "a new pay grade starts with no currencies");
    }

    @Test(priority = 31, dependsOnMethods = "tcCfg030_createPayGrade",
          description = "TC-CFG-031 | A currency band is added to a pay grade and echoes the resolved currency")
    public void tcCfg031_addCurrencyBand() {
        Response r = api().pathParam("id", payGradeId)
                .body(TestData.payGradeCurrency("USD", 1000, 2000))
                .when().post(Endpoints.PAY_GRADE_CURRENCIES);

        assertOkObject(r, "POST /admin/pay-grades/{id}/currencies");
        assertField(r, "data.minSalary", 1000, "band minimum");
        assertField(r, "data.maxSalary", 2000, "band maximum");
        assertField(r, "data.currencyType.id", "USD", "currency id");
        assertField(r, "data.currencyType.name", "United States Dollar", "currency resolved name");
        assertField(r, "meta.payGradeId", payGradeId, "meta echoes the pay grade");
    }

    @Test(priority = 32, dependsOnMethods = "tcCfg031_addCurrencyBand",
          description = "TC-CFG-032 | GET the pay grade's currencies lists the band")
    public void tcCfg032_listCurrencyBands() {
        Response r = api().pathParam("id", payGradeId).when().get(Endpoints.PAY_GRADE_CURRENCIES);

        assertOkList(r, "GET /admin/pay-grades/{id}/currencies");
        assertHasTotal(r, "GET pay grade currencies");
        List<String> currencies = r.jsonPath().getList("data.currencyType.id");
        Assert.assertTrue(currencies.contains("USD"),
                "The added USD band should be listed, got " + currencies);
    }

    @Test(priority = 33, dependsOnMethods = "tcCfg031_addCurrencyBand",
          description = "TC-CFG-033 | A currency band with a maximum below its minimum returns 400 with a specific message")
    public void tcCfg033_invertedBandRejected() {
        // Note this is a 400 with its own message, not the 422 "Invalid Parameter" envelope
        // the rest of the write surface uses for bad input.
        Response r = api().pathParam("id", payGradeId)
                .body(TestData.payGradeCurrency("EUR", 5000, 100))
                .when().post(Endpoints.PAY_GRADE_CURRENCIES);

        assertError(r, StatusCodes.BAD_REQUEST, Messages.BAND_INVERTED,
                "POST a currency band whose maximum is below its minimum");
    }

    @Test(priority = 34, description = "TC-CFG-034 | FINDING: currencies for a non-existent pay grade return 200 and echo the bogus id")
    public void tcCfg034_currenciesForMissingPayGrade() {
        // GET /admin/pay-grades/99999999 itself 404s, but its currencies sub-collection
        // reports success, so "no bands configured" and "no such grade" look identical.
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.PAY_GRADE_CURRENCIES);

        assertOkList(r, "GET /admin/pay-grades/99999999/currencies");
        Assert.assertTrue(r.jsonPath().getList("data").isEmpty(),
                "A non-existent pay grade should at least carry no currency bands");
        assertField(r, "meta.payGradeId", 99999999, "meta echoes the id that does not exist");
    }

    /* ================================================================== */
    /* Users & reference lists                                             */
    /* ================================================================== */

    @Test(priority = 40, description = "TC-CFG-040 | GET a system user by id returns the account with its linked employee")
    public void tcCfg040_getUserById() {
        Integer id = require(firstId(Endpoints.USERS), "a system user");
        Response r = api().pathParam("id", id).when().get(Endpoints.USER_BY_ID);

        assertOkObject(r, "GET /admin/users/{id}");
        assertField(r, "data.id", id, "fetched user id");
        assertFieldPresent(r, "data.userName", "user name");
        assertFieldPresent(r, "data.status", "account status");
        assertFieldPresent(r, "data.userRole.name", "assigned user role");
    }

    @Test(priority = 41, description = "TC-CFG-041 | A user record never carries a password hash")
    public void tcCfg041_userHasNoPasswordField() {
        Integer id = require(firstId(Endpoints.USERS), "a system user");
        Response r = api().pathParam("id", id).when().get(Endpoints.USER_BY_ID);

        assertOkObject(r, "GET /admin/users/{id}");
        String body = r.getBody().asString().toLowerCase();
        Assert.assertFalse(body.contains("password"),
                "A user record must not expose any password field: " + safeBody(r));
    }

    @Test(priority = 42, description = "TC-CFG-042 | GET a non-existent user returns 404")
    public void tcCfg042_getMissingUser() {
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.USER_BY_ID);

        assertRecordNotFound(r, "GET /admin/users/99999999");
    }

    @Test(priority = 43, description = "TC-CFG-043 | GET candidate statuses returns the recruitment pipeline stages")
    public void tcCfg043_candidateStatuses() {
        Response r = api().when().get(Endpoints.CANDIDATE_STATUSES);

        assertOkList(r, "GET /recruitment/candidates/statuses");
        List<Integer> ids = r.jsonPath().getList("data.id");
        Assert.assertTrue(ids.size() >= 4,
                "The pipeline should expose at least four stages, got " + ids.size());
        assertEachHas(r, "data.label", "status labels");
    }

    @Test(priority = 44, description = "TC-CFG-044 | GET interviewers returns employees usable as interviewers")
    public void tcCfg044_interviewers() {
        Response r = api().queryParam("limit", 5).when().get(Endpoints.INTERVIEWERS);

        assertOkList(r, "GET /recruitment/interviewers");
        List<Map<String, Object>> data = r.jsonPath().getList("data");
        if (!data.isEmpty()) {
            assertEachHas(r, "data.empNumber", "interviewer employee numbers");
            assertEachHas(r, "data.lastName", "interviewer last names");
        }
    }

    @Test(priority = 45, description = "TC-CFG-045 | GET defined PIM reports returns the saved report list")
    public void tcCfg045_definedPimReports() {
        Response r = api().queryParam("limit", 10).when().get(Endpoints.PIM_REPORTS);

        assertOkList(r, "GET /pim/reports/defined");
        assertHasTotal(r, "GET /pim/reports/defined");
        List<Map<String, Object>> data = r.jsonPath().getList("data");
        if (!data.isEmpty()) assertEachHas(r, "data.name", "report names");
    }
}
