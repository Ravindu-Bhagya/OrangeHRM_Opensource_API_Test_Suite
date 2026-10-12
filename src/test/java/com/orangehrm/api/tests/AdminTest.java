package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * TC-ADM - Admin module: master data CRUD, organization setup and system configuration.
 *
 * Job titles are covered field-by-field as the reference CRUD pattern; the remaining
 * master-data resources run the same create/read/update/delete lifecycle.
 */
public class AdminTest extends BaseAPITest {

    private static Integer jobTitleId;

    /* ================================================================== */
    /* Job titles - the reference CRUD deep-dive                           */
    /* ================================================================== */

    @Test(priority = 1, description = "TC-ADM-001 | GET all job titles returns 200 with a paginated list")
    public void tcAdm001_getAllJobTitles() {
        Response r = api().queryParam("limit", 50).when().get(Endpoints.JOB_TITLES);

        assertOkList(r, "GET /admin/job-titles");
        assertHasTotal(r, "GET /admin/job-titles");
        List<Map<String, Object>> data = r.jsonPath().getList("data");
        if (!data.isEmpty()) {
            assertEachHas(r, "data.id", "job title ids");
            assertEachHas(r, "data.title", "job title names");
        }
    }

    @Test(priority = 2, description = "TC-ADM-002 | POST creates a job title and echoes it back with an id")
    public void tcAdm002_createJobTitle() {
        Response r = api().body(TestData.jobTitle(TestData.JOB_TITLE))
                .when().post(Endpoints.JOB_TITLES);

        assertOkObject(r, "POST /admin/job-titles");
        assertField(r, "data.title", TestData.JOB_TITLE, "created job title name");
        assertField(r, "data.description", TestData.DESCRIPTION, "created job title description");
        assertFieldPresent(r, "data.id", "created job title id");

        jobTitleId = r.jsonPath().getInt("data.id");
        Assert.assertTrue(jobTitleId > 0, "Created job title id should be positive");
        registerForCleanup(Endpoints.JOB_TITLES, jobTitleId);
    }

    @Test(priority = 3, dependsOnMethods = "tcAdm002_createJobTitle",
          description = "TC-ADM-003 | GET a job title by id returns exactly the created record")
    public void tcAdm003_getJobTitleById() {
        Response r = api().pathParam("id", jobTitleId).when().get(Endpoints.JOB_TITLE_BY_ID);

        assertOkObject(r, "GET /admin/job-titles/{id}");
        assertField(r, "data.id", jobTitleId, "fetched job title id");
        assertField(r, "data.title", TestData.JOB_TITLE, "fetched job title name");
    }

    @Test(priority = 4, dependsOnMethods = "tcAdm002_createJobTitle",
          description = "TC-ADM-004 | The created job title is findable in the collection")
    public void tcAdm004_createdJobTitleAppearsInList() {
        Response r = api().queryParam("limit", 100).when().get(Endpoints.JOB_TITLES);

        assertOkList(r, "GET /admin/job-titles after create");
        List<String> titles = r.jsonPath().getList("data.title");
        Assert.assertTrue(titles.contains(TestData.JOB_TITLE),
                "Created job title '" + TestData.JOB_TITLE + "' not found in the list: " + titles);
    }

    @Test(priority = 5, dependsOnMethods = "tcAdm002_createJobTitle",
          description = "TC-ADM-005 | PUT updates a job title and returns the new values")
    public void tcAdm005_updateJobTitle() {
        Response r = api().pathParam("id", jobTitleId)
                .body(TestData.jobTitle(TestData.JOB_TITLE_UPDATED))
                .when().put(Endpoints.JOB_TITLE_BY_ID);

        assertOkObject(r, "PUT /admin/job-titles/{id}");
        assertField(r, "data.id", jobTitleId, "updated job title id");
        assertField(r, "data.title", TestData.JOB_TITLE_UPDATED, "updated job title name");

        Response verify = api().pathParam("id", jobTitleId).when().get(Endpoints.JOB_TITLE_BY_ID);
        assertField(verify, "data.title", TestData.JOB_TITLE_UPDATED,
                "job title name persisted after update");
    }

    @Test(priority = 6, dependsOnMethods = "tcAdm005_updateJobTitle",
          description = "TC-ADM-006 | Creating a duplicate job title returns 422")
    public void tcAdm006_createDuplicateJobTitleRejected() {
        Response r = api().body(TestData.jobTitle(TestData.JOB_TITLE_UPDATED))
                .when().post(Endpoints.JOB_TITLES);

        assertValidationError(r, "title", "POST duplicate job title");
    }

    @Test(priority = 7, description = "TC-ADM-007 | Creating a job title without a title returns 422")
    public void tcAdm007_createJobTitleMissingMandatoryField() {
        Response r = api().body(TestData.jobTitleMissingTitle())
                .when().post(Endpoints.JOB_TITLES);

        assertValidationError(r, "title", "POST job title with no title");
    }

    @Test(priority = 8, description = "TC-ADM-008 | Creating a job title with an oversized name returns 422")
    public void tcAdm008_createJobTitleOversizedName() {
        Response r = api().body(TestData.jobTitle(TestData.OVERSIZED_NAME))
                .when().post(Endpoints.JOB_TITLES);

        assertValidationError(r, "title", "POST job title with a 300-character name");
    }

    @Test(priority = 9, description = "TC-ADM-009 | GET a non-existent job title returns 404")
    public void tcAdm009_getNonExistentJobTitle() {
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.JOB_TITLE_BY_ID);

        assertRecordNotFound(r, "GET /admin/job-titles/99999999");
    }

    @Test(priority = 10, dependsOnMethods = "tcAdm006_createDuplicateJobTitleRejected",
          description = "TC-ADM-010 | DELETE removes the job title from the collection")
    public void tcAdm010_deleteJobTitle() {
        Response r = api().body(TestData.deleteIds(jobTitleId))
                .when().delete(Endpoints.JOB_TITLES);

        assertStatus(r, StatusCodes.OK, "DELETE /admin/job-titles");
        List<Integer> deleted = r.jsonPath().getList("data");
        Assert.assertTrue(deleted.contains(jobTitleId),
                "Deleted ids should echo " + jobTitleId + " but got " + deleted);
        unregisterCleanup(Endpoints.JOB_TITLES, jobTitleId);

        // The record must no longer appear in the collection.
        Response list = api().queryParam("limit", 0).when().get(Endpoints.JOB_TITLES);
        assertOkList(list, "GET /admin/job-titles after delete");
        Assert.assertFalse(list.jsonPath().getList("data.title").contains(TestData.JOB_TITLE_UPDATED),
                "The deleted job title is still listed in the collection");

        // Deleting it a second time must report that it is gone.
        Response again = api().body(TestData.deleteIds(jobTitleId))
                .when().delete(Endpoints.JOB_TITLES);
        assertRecordsNotFound(again, "DELETE an already-deleted job title");
    }

    @Test(priority = 11, dependsOnMethods = "tcAdm010_deleteJobTitle",
          description = "TC-ADM-011 | KNOWN DEVIATION: a soft-deleted job title is still readable by id")
    public void tcAdm011_deletedJobTitleStillReadableById() {
        Response r = api().pathParam("id", jobTitleId).when().get(Endpoints.JOB_TITLE_BY_ID);

        // OrangeHRM soft-deletes job titles: the row is hidden from the collection and a
        // second DELETE reports 404, but GET-by-id does not filter on the deleted flag and
        // still serves the full record. Employment statuses, by contrast, 404 correctly.
        // This test pins the CURRENT behaviour - if the product is fixed to return 404 it
        // will fail here, which is the signal to flip the expectation.
        Assert.assertEquals(r.statusCode(), StatusCodes.OK,
                "Job-title GET-by-id after delete changed behaviour - review TC-ADM-011.\n"
                        + safeBody(r));
        assertField(r, "data.id", jobTitleId, "soft-deleted job title still readable");
    }

    @Test(priority = 12, description = "TC-ADM-012 | DELETE with a query string instead of a JSON body fails")
    public void tcAdm012_deleteWithQueryStringIsRejected() {
        Response r = api().queryParam("ids[]", 99999999).when().delete(Endpoints.JOB_TITLES);

        // A query string is ignored entirely, so the delete matches nothing.
        assertRecordsNotFound(r, "DELETE /admin/job-titles with ?ids[]= instead of a JSON body");
    }

    /* ================================================================== */
    /* Remaining master data - same lifecycle, one test per resource       */
    /* ================================================================== */

    /** create -> assert name -> update -> delete, asserting status and body at each step. */
    private void masterDataLifecycle(String collection, String byId, String body,
                                     String expectedName, String updateBody,
                                     String updatedName, String label) {
        Response create = api().body(body).when().post(collection);
        assertOkObject(create, "POST " + label);
        assertField(create, "data.name", expectedName, "created " + label + " name");
        assertFieldPresent(create, "data.id", "created " + label + " id");
        int id = create.jsonPath().getInt("data.id");
        registerForCleanup(collection, id);

        Response get = api().pathParam("id", id).when().get(byId);
        assertOkObject(get, "GET " + label + " by id");
        assertField(get, "data.name", expectedName, "fetched " + label + " name");

        Response update = api().pathParam("id", id).body(updateBody).when().put(byId);
        assertOkObject(update, "PUT " + label);
        assertField(update, "data.name", updatedName, "updated " + label + " name");

        Response delete = api().body(TestData.deleteIds(id)).when().delete(collection);
        assertStatus(delete, StatusCodes.OK, "DELETE " + label);
        Assert.assertTrue(delete.jsonPath().getList("data").contains(id),
                "DELETE should echo the removed id for " + label);
        unregisterCleanup(collection, id);
    }

    @Test(priority = 20, description = "TC-ADM-020 | Employment status full CRUD lifecycle")
    public void tcAdm020_employmentStatusLifecycle() {
        masterDataLifecycle(Endpoints.EMPLOYMENT_STATUSES, Endpoints.EMPLOYMENT_STATUS_BY_ID,
                TestData.employmentStatus(TestData.EMPLOYMENT_STATUS), TestData.EMPLOYMENT_STATUS,
                TestData.employmentStatus(TestData.EMPLOYMENT_STATUS_UPDATED),
                TestData.EMPLOYMENT_STATUS_UPDATED, "/admin/employment-statuses");
    }

    @Test(priority = 21, description = "TC-ADM-021 | Job category full CRUD lifecycle")
    public void tcAdm021_jobCategoryLifecycle() {
        masterDataLifecycle(Endpoints.JOB_CATEGORIES, Endpoints.JOB_CATEGORY_BY_ID,
                TestData.jobCategory(TestData.JOB_CATEGORY), TestData.JOB_CATEGORY,
                TestData.jobCategory(TestData.JOB_CATEGORY + "_UPD"),
                TestData.JOB_CATEGORY + "_UPD", "/admin/job-categories");
    }

    @Test(priority = 22, description = "TC-ADM-022 | Pay grade full CRUD lifecycle")
    public void tcAdm022_payGradeLifecycle() {
        masterDataLifecycle(Endpoints.PAY_GRADES, Endpoints.PAY_GRADE_BY_ID,
                TestData.payGrade(TestData.PAY_GRADE), TestData.PAY_GRADE,
                TestData.payGrade(TestData.PAY_GRADE + "_UPD"),
                TestData.PAY_GRADE + "_UPD", "/admin/pay-grades");
    }

    @Test(priority = 23, description = "TC-ADM-023 | Skill full CRUD lifecycle")
    public void tcAdm023_skillLifecycle() {
        masterDataLifecycle(Endpoints.SKILLS, Endpoints.SKILL_BY_ID,
                TestData.skill(TestData.SKILL), TestData.SKILL,
                TestData.skill(TestData.SKILL + "_UPD"),
                TestData.SKILL + "_UPD", "/admin/skills");
    }

    @Test(priority = 24, description = "TC-ADM-024 | Education level full CRUD lifecycle")
    public void tcAdm024_educationLifecycle() {
        masterDataLifecycle(Endpoints.EDUCATIONS, Endpoints.EDUCATION_BY_ID,
                TestData.education(TestData.EDUCATION), TestData.EDUCATION,
                TestData.education(TestData.EDUCATION + "_UPD"),
                TestData.EDUCATION + "_UPD", "/admin/educations");
    }

    @Test(priority = 25, description = "TC-ADM-025 | License type full CRUD lifecycle")
    public void tcAdm025_licenseLifecycle() {
        masterDataLifecycle(Endpoints.LICENSES, Endpoints.LICENSE_BY_ID,
                TestData.license(TestData.LICENSE), TestData.LICENSE,
                TestData.license(TestData.LICENSE + "_UPD"),
                TestData.LICENSE + "_UPD", "/admin/licenses");
    }

    @Test(priority = 26, description = "TC-ADM-026 | Language full CRUD lifecycle")
    public void tcAdm026_languageLifecycle() {
        masterDataLifecycle(Endpoints.LANGUAGES, Endpoints.LANGUAGE_BY_ID,
                TestData.language(TestData.LANGUAGE), TestData.LANGUAGE,
                TestData.language(TestData.LANGUAGE + "_UPD"),
                TestData.LANGUAGE + "_UPD", "/admin/languages");
    }

    @Test(priority = 27, description = "TC-ADM-027 | Membership full CRUD lifecycle")
    public void tcAdm027_membershipLifecycle() {
        masterDataLifecycle(Endpoints.MEMBERSHIPS, Endpoints.MEMBERSHIP_BY_ID,
                TestData.membership(TestData.MEMBERSHIP), TestData.MEMBERSHIP,
                TestData.membership(TestData.MEMBERSHIP + "_UPD"),
                TestData.MEMBERSHIP + "_UPD", "/admin/memberships");
    }

    @Test(priority = 28, description = "TC-ADM-028 | Location create, read and delete with address fields")
    public void tcAdm028_locationLifecycle() {
        Response create = api().body(TestData.location(TestData.LOCATION))
                .when().post(Endpoints.LOCATIONS);

        assertOkObject(create, "POST /admin/locations");
        assertField(create, "data.name", TestData.LOCATION, "created location name");
        assertField(create, "data.city", UserData.EMP_CITY, "created location city");
        assertField(create, "data.country.countryCode", "US", "created location country");
        int id = create.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.LOCATIONS, id);

        Response get = api().pathParam("id", id).when().get(Endpoints.LOCATION_BY_ID);
        assertOkObject(get, "GET /admin/locations/{id}");
        assertField(get, "data.zipCode", UserData.EMP_ZIP, "location zip code");

        Response delete = api().body(TestData.deleteIds(id)).when().delete(Endpoints.LOCATIONS);
        assertStatus(delete, StatusCodes.OK, "DELETE /admin/locations");
        unregisterCleanup(Endpoints.LOCATIONS, id);
    }

    @Test(priority = 29, description = "TC-ADM-029 | Subunit create requires parentId and deletes by id")
    public void tcAdm029_subunitLifecycle() {
        Response tree = api().when().get(Endpoints.SUBUNITS);
        assertOkList(tree, "GET /admin/subunits");
        int rootId = require(tree.jsonPath().getInt("data[0].id"), "an existing root subunit");

        Response create = api().body(TestData.subunit(TestData.SUBUNIT, rootId))
                .when().post(Endpoints.SUBUNITS);
        assertOkObject(create, "POST /admin/subunits");
        assertField(create, "data.name", TestData.SUBUNIT, "created subunit name");
        int id = create.jsonPath().getInt("data.id");

        // Subunits delete singularly, not by bulk id list.
        Response delete = api().pathParam("id", id).when().delete(Endpoints.SUBUNIT_BY_ID);
        assertStatus(delete, StatusCodes.OK, "DELETE /admin/subunits/{id}");
    }

    @Test(priority = 30, description = "TC-ADM-030 | Subunit create without parentId returns 404")
    public void tcAdm030_subunitWithoutParentRejected() {
        Response r = api().body("""
                {"name":"%s","unitId":"","description":"x"}""".formatted(TestData.SUBUNIT + "_NP"))
                .when().post(Endpoints.SUBUNITS);

        assertRecordNotFound(r, "POST /admin/subunits without parentId");
    }

    /* ================================================================== */
    /* System users                                                        */
    /* ================================================================== */

    @Test(priority = 40, description = "TC-ADM-040 | GET all system users returns 200 with role and employee")
    public void tcAdm040_getAllUsers() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.USERS);

        assertOkList(r, "GET /admin/users");
        assertHasTotal(r, "GET /admin/users");
        assertEachHas(r, "data.userName", "usernames");
        assertEachHas(r, "data.userRole.name", "user role names");
    }

    @Test(priority = 41, description = "TC-ADM-041 | GET the admin user by id returns the Admin role")
    public void tcAdm041_getAdminUserById() {
        Response r = api().pathParam("id", 1).when().get(Endpoints.USER_BY_ID);

        assertOkObject(r, "GET /admin/users/1");
        assertField(r, "data.id", 1, "admin user id");
        assertFieldPresent(r, "data.userName", "admin username");
        assertFieldPresent(r, "data.userRole.name", "admin user role");
    }

    @Test(priority = 42, description = "TC-ADM-042 | An ESS user can be created, fetched and deleted")
    public void tcAdm042_createEssUserLifecycle() {
        int empNumber = require(firstEmpNumber(), "an employee to attach the user to");

        Response create = api().body(TestData.user(UserData.NEW_USERNAME,
                        UserData.NEW_USER_PASSWORD, UserData.ROLE_ESS, empNumber))
                .when().post(Endpoints.USERS);

        assertOkObject(create, "POST /admin/users");
        assertField(create, "data.userName", UserData.NEW_USERNAME, "created username");
        assertField(create, "data.userRole.id", UserData.ROLE_ESS, "created user role");
        int userId = create.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.USERS, userId);

        Response get = api().pathParam("id", userId).when().get(Endpoints.USER_BY_ID);
        assertOkObject(get, "GET the created user");
        assertField(get, "data.userName", UserData.NEW_USERNAME, "fetched username");

        Response delete = api().body(TestData.deleteIds(userId)).when().delete(Endpoints.USERS);
        assertStatus(delete, StatusCodes.OK, "DELETE /admin/users");
        unregisterCleanup(Endpoints.USERS, userId);
    }

    @Test(priority = 43, description = "TC-ADM-043 | Creating a user with a weak password returns 422")
    public void tcAdm043_createUserWeakPasswordRejected() {
        int empNumber = require(firstEmpNumber(), "an employee to attach the user to");

        Response r = api().body(TestData.user(UserData.TEST_PREFIX + "weak_" + UserData.uniqueId(),
                        UserData.WEAK_PASSWORD, UserData.ROLE_ESS, empNumber))
                .when().post(Endpoints.USERS);

        assertValidationError(r, "password", "POST user with a weak password");
    }

    @Test(priority = 44, description = "TC-ADM-044 | Creating a user with a duplicate username returns 422")
    public void tcAdm044_createDuplicateUsernameRejected() {
        int empNumber = require(firstEmpNumber(), "an employee to attach the user to");

        Response r = api().body(TestData.user(Credentials.ADMIN_USERNAME,
                        UserData.NEW_USER_PASSWORD, UserData.ROLE_ESS, empNumber))
                .when().post(Endpoints.USERS);

        assertValidationError(r, "username", "POST user with an existing username");
    }

    /* ================================================================== */
    /* Organization & system configuration (read-only)                     */
    /* ================================================================== */

    @Test(priority = 50, description = "TC-ADM-050 | GET enabled modules lists every core module")
    public void tcAdm050_getModules() {
        Response r = api().when().get(Endpoints.MODULES);

        assertOkObject(r, "GET /admin/modules");
        for (String module : new String[]{"admin", "pim", "leave", "time",
                                          "recruitment", "performance", "maintenance"}) {
            assertFieldPresent(r, "data." + module, "module flag '" + module + "'");
        }
    }

    @Test(priority = 51, description = "TC-ADM-051 | GET organization details returns the general information")
    public void tcAdm051_getOrganization() {
        Response r = api().when().get(Endpoints.ORGANIZATION);

        assertOkObject(r, "GET /admin/organization");
        for (String field : new String[]{"name", "taxId", "phone", "email", "city", "zipCode"}) {
            Assert.assertTrue(r.jsonPath().get("data").toString().contains(field),
                    "Organization response is missing '" + field + "'\n" + safeBody(r));
        }
    }

    @Test(priority = 52, description = "TC-ADM-052 | GET localization returns language and date format")
    public void tcAdm052_getLocalization() {
        Response r = api().when().get(Endpoints.LOCALIZATION);

        assertOkObject(r, "GET /admin/localization");
        assertFieldPresent(r, "data.language", "localization language");
        assertFieldPresent(r, "data.dateFormat", "localization date format");
    }

    @Test(priority = 53, description = "TC-ADM-053 | GET theme configuration returns the colour variables")
    public void tcAdm053_getTheme() {
        Response r = api().when().get(Endpoints.THEME);

        assertOkObject(r, "GET /admin/theme");
        assertFieldPresent(r, "data.name", "theme name");
        assertFieldPresent(r, "data.variables", "theme variables");
    }

    @Test(priority = 54, description = "TC-ADM-054 | GET email configuration returns the SMTP settings")
    public void tcAdm054_getEmailConfiguration() {
        Response r = api().when().get(Endpoints.EMAIL_CONFIGURATION);

        assertOkObject(r, "GET /admin/email-configuration");
        assertFieldPresent(r, "data.mailType", "mail type");
    }

    @Test(priority = 55, description = "TC-ADM-055 | GET LDAP configuration returns the connection settings")
    public void tcAdm055_getLdapConfiguration() {
        Response r = api().when().get(Endpoints.LDAP_CONFIG);

        assertOkObject(r, "GET /admin/ldap-config");
        assertFieldPresent(r, "data.enable", "ldap enable flag");
    }

    @Test(priority = 56, description = "TC-ADM-056 | GET nationalities returns the reference list")
    public void tcAdm056_getNationalities() {
        Response r = api().queryParam("limit", 0).when().get(Endpoints.NATIONALITIES);

        assertOkList(r, "GET /admin/nationalities");
        Assert.assertFalse(r.jsonPath().getList("data").isEmpty(),
                "Nationality reference data should not be empty");
        assertEachHas(r, "data.name", "nationality names");
    }

    @Test(priority = 57, description = "TC-ADM-057 | GET i18n languages returns the translation catalogue")
    public void tcAdm057_getI18nLanguages() {
        Response r = api().queryParam("limit", 10).when().get(Endpoints.I18N_LANGUAGES);

        assertOkList(r, "GET /admin/i18n/languages");
        assertEachHas(r, "data.name", "i18n language names");
    }

    @Test(priority = 58, description = "TC-ADM-058 | GET work shifts returns the configured shifts")
    public void tcAdm058_getWorkShifts() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.WORK_SHIFTS);

        assertOkList(r, "GET /admin/work-shifts");
        assertHasTotal(r, "GET /admin/work-shifts");
    }

    @Test(priority = 59, description = "TC-ADM-059 | GET LDAP sync status returns the last run state")
    public void tcAdm059_getLdapSyncStatus() {
        Response r = api().when().get(Endpoints.LDAP_USER_SYNC);

        assertOkObject(r, "GET /admin/ldap/user-sync");
        assertFieldPresent(r, "data.syncStatus", "ldap sync status");
    }
}
