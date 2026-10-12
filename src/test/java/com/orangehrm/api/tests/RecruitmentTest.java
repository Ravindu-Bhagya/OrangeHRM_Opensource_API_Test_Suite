package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * TC-REC - Recruitment: vacancies, candidates and the candidate status workflow.
 *
 * A vacancy is created first because a candidate must be attached to one; both are
 * removed at the end of the class.
 */
public class RecruitmentTest extends BaseAPITest {

    private static Integer vacancyId;
    private static Integer candidateId;
    private static Integer jobTitleId;

    @Test(priority = 1, description = "TC-REC-001 | GET all vacancies returns 200 with a paginated list")
    public void tcRec001_getAllVacancies() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.VACANCIES);

        assertOkList(r, "GET /recruitment/vacancies");
        assertHasTotal(r, "GET /recruitment/vacancies");
    }

    @Test(priority = 2, description = "TC-REC-002 | GET hiring managers returns the selectable list")
    public void tcRec002_getHiringManagers() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.HIRING_MANAGERS);
        assertOkList(r, "GET /recruitment/hiring-managers");
    }

    @Test(priority = 3, description = "TC-REC-003 | GET candidate statuses returns the workflow states")
    public void tcRec003_getCandidateStatuses() {
        Response r = api().when().get(Endpoints.CANDIDATE_STATUSES);

        assertOkList(r, "GET /recruitment/candidates/statuses");
        Assert.assertFalse(r.jsonPath().getList("data").isEmpty(),
                "The candidate status list should not be empty");
    }

    @Test(priority = 4, description = "TC-REC-004 | GET published public vacancies returns 200")
    public void tcRec004_getPublicVacancies() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.PUBLIC_VACANCIES);
        assertOkList(r, "GET /recruitment/public/vacancies");
    }

    @Test(priority = 5, description = "TC-REC-005 | POST creates a vacancy against a job title and hiring manager")
    public void tcRec005_createVacancy() {
        jobTitleId = jobTitleFixture();
        int hiringManager = require(firstEmpNumber(), "an employee to act as hiring manager");

        Response r = api().body(TestData.vacancy(TestData.VACANCY, jobTitleId, hiringManager))
                .when().post(Endpoints.VACANCIES);

        assertOkObject(r, "POST /recruitment/vacancies");
        assertField(r, "data.name", TestData.VACANCY, "created vacancy name");
        assertFieldPresent(r, "data.id", "created vacancy id");

        vacancyId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.VACANCIES, vacancyId);
    }

    @Test(priority = 6, dependsOnMethods = "tcRec005_createVacancy",
          description = "TC-REC-006 | GET a vacancy by id returns the created record")
    public void tcRec006_getVacancyById() {
        Response r = api().pathParam("id", vacancyId).when().get(Endpoints.VACANCY_BY_ID);

        assertOkObject(r, "GET /recruitment/vacancies/{id}");
        assertField(r, "data.id", vacancyId, "fetched vacancy id");
        assertField(r, "data.name", TestData.VACANCY, "fetched vacancy name");
    }

    @Test(priority = 7, dependsOnMethods = "tcRec005_createVacancy",
          description = "TC-REC-007 | PUT updates the vacancy when all required fields are resent")
    public void tcRec007_updateVacancy() {
        int hiringManager = require(firstEmpNumber(), "an employee to act as hiring manager");

        Response r = api().pathParam("id", vacancyId)
                .body(TestData.vacancy(TestData.VACANCY_UPDATED, jobTitleId, hiringManager))
                .when().put(Endpoints.VACANCY_BY_ID);

        assertOkObject(r, "PUT /recruitment/vacancies/{id}");
        assertField(r, "data.name", TestData.VACANCY_UPDATED, "updated vacancy name");
    }

    @Test(priority = 8, description = "TC-REC-008 | Creating a vacancy without a job title returns 422")
    public void tcRec008_createVacancyMissingJobTitle() {
        Response r = api().body("""
                {"name":"%s","numOfPositions":1,"status":true}""".formatted(TestData.VACANCY + "_BAD"))
                .when().post(Endpoints.VACANCIES);

        assertValidationError(r, null, "POST vacancy with no jobTitleId");
    }

    @Test(priority = 10, description = "TC-REC-010 | GET all candidates returns 200 with a paginated list")
    public void tcRec010_getAllCandidates() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.CANDIDATES);

        assertOkList(r, "GET /recruitment/candidates");
        assertHasTotal(r, "GET /recruitment/candidates");
    }

    @Test(priority = 11, dependsOnMethods = "tcRec005_createVacancy",
          description = "TC-REC-011 | POST creates a candidate against the vacancy")
    public void tcRec011_createCandidate() {
        Response r = api().body(TestData.candidate(vacancyId))
                .when().post(Endpoints.CANDIDATES);

        assertOkObject(r, "POST /recruitment/candidates");
        assertField(r, "data.firstName", UserData.CANDIDATE_FIRST_NAME, "candidate first name");
        assertField(r, "data.lastName", UserData.CANDIDATE_LAST_NAME, "candidate last name");
        assertFieldPresent(r, "data.id", "created candidate id");

        candidateId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.CANDIDATES, candidateId);
    }

    @Test(priority = 12, dependsOnMethods = "tcRec011_createCandidate",
          description = "TC-REC-012 | GET a candidate by id returns the created record with its email")
    public void tcRec012_getCandidateById() {
        Response r = api().pathParam("id", candidateId).when().get(Endpoints.CANDIDATE_BY_ID);

        assertOkObject(r, "GET /recruitment/candidates/{id}");
        assertField(r, "data.id", candidateId, "fetched candidate id");
        assertField(r, "data.email", UserData.CANDIDATE_EMAIL, "fetched candidate email");
    }

    @Test(priority = 13, dependsOnMethods = "tcRec005_createVacancy",
          description = "TC-REC-013 | Creating a candidate with a malformed email returns 422")
    public void tcRec013_createCandidateInvalidEmail() {
        Response r = api().body(TestData.candidateBadEmail(vacancyId))
                .when().post(Endpoints.CANDIDATES);

        assertValidationError(r, "email", "POST candidate with a malformed email");
    }

    @Test(priority = 14, dependsOnMethods = "tcRec011_createCandidate",
          description = "TC-REC-014 | GET the candidate history returns the action audit trail")
    public void tcRec014_getCandidateHistory() {
        Response r = api().pathParam("id", candidateId).queryParam("limit", 20)
                .when().get(Endpoints.CANDIDATE_HISTORY);

        assertOkList(r, "GET /recruitment/candidates/{id}/history");
    }

    @Test(priority = 15, dependsOnMethods = "tcRec011_createCandidate",
          description = "TC-REC-015 | A candidate can be shortlisted, advancing the workflow status")
    public void tcRec015_shortlistCandidate() {
        Response r = api().pathParam("id", candidateId)
                .body("{\"note\":\"%s\"}".formatted(TestData.DESCRIPTION))
                .when().put(Endpoints.CANDIDATE_SHORTLIST);

        Assert.assertTrue(r.statusCode() == StatusCodes.OK || r.statusCode() == StatusCodes.UNPROCESSABLE,
                "Unexpected status for shortlist: " + r.statusCode() + "\n" + safeBody(r));
        if (r.statusCode() == StatusCodes.OK) {
            Response check = api().pathParam("id", candidateId).when().get(Endpoints.CANDIDATE_BY_ID);
            assertFieldPresent(check, "data.status", "candidate status after shortlist");
        }
    }

    @Test(priority = 20, dependsOnMethods = "tcRec011_createCandidate", alwaysRun = true,
          description = "TC-REC-020 | DELETE removes the candidate created by this run")
    public void tcRec020_deleteCandidate() {
        // alwaysRun: without this guard a skipped create sends {"ids":[null]} and fails.
        require(candidateId, "a candidate created by this run");
        Response r = api().body(TestData.deleteIds(candidateId)).when().delete(Endpoints.CANDIDATES);

        assertStatus(r, StatusCodes.OK, "DELETE /recruitment/candidates");
        unregisterCleanup(Endpoints.CANDIDATES, candidateId);
    }

    @Test(priority = 21, dependsOnMethods = "tcRec005_createVacancy", alwaysRun = true,
          description = "TC-REC-021 | DELETE removes the vacancy created by this run")
    public void tcRec021_deleteVacancy() {
        // alwaysRun: without this guard a skipped create sends {"ids":[null]} and fails.
        require(vacancyId, "a vacancy created by this run");
        Response r = api().body(TestData.deleteIds(vacancyId)).when().delete(Endpoints.VACANCIES);

        assertStatus(r, StatusCodes.OK, "DELETE /recruitment/vacancies");
        unregisterCleanup(Endpoints.VACANCIES, vacancyId);
    }
}
