package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

/**
 * TC-CLM - Claim module: claim events, expense types and claim requests.
 */
public class ClaimTest extends BaseAPITest {

    private static Integer claimEventId;
    private static Integer expenseTypeId;

    @Test(priority = 1, description = "TC-CLM-001 | GET all claim events returns 200 with a list")
    public void tcClm001_getClaimEvents() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.CLAIM_EVENTS);

        assertOkList(r, "GET /claim/events");
        assertHasTotal(r, "GET /claim/events");
    }

    @Test(priority = 2, description = "TC-CLM-002 | POST creates a claim event")
    public void tcClm002_createClaimEvent() {
        Response r = api().body(TestData.claimEvent(TestData.CLAIM_EVENT))
                .when().post(Endpoints.CLAIM_EVENTS);

        assertOkObject(r, "POST /claim/events");
        assertField(r, "data.name", TestData.CLAIM_EVENT, "created claim event name");
        assertField(r, "data.status", true, "created claim event status");

        claimEventId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.CLAIM_EVENTS, claimEventId);
    }

    @Test(priority = 3, dependsOnMethods = "tcClm002_createClaimEvent",
          description = "TC-CLM-003 | GET a claim event by id returns the created record")
    public void tcClm003_getClaimEventById() {
        Response r = api().pathParam("id", claimEventId).when().get(Endpoints.CLAIM_EVENT_BY_ID);

        assertOkObject(r, "GET /claim/events/{id}");
        assertField(r, "data.id", claimEventId, "fetched claim event id");
    }

    @Test(priority = 4, description = "TC-CLM-004 | Creating a claim event without a name returns 422")
    public void tcClm004_createClaimEventMissingName() {
        Response r = api().body("{\"description\":\"x\",\"status\":true}")
                .when().post(Endpoints.CLAIM_EVENTS);

        assertValidationError(r, "name", "POST claim event with no name");
    }

    @Test(priority = 10, description = "TC-CLM-010 | GET all expense types returns 200 with a list")
    public void tcClm010_getExpenseTypes() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.EXPENSE_TYPES);

        assertOkList(r, "GET /claim/expenses/types");
        assertHasTotal(r, "GET /claim/expenses/types");
    }

    @Test(priority = 11, description = "TC-CLM-011 | POST creates an expense type")
    public void tcClm011_createExpenseType() {
        Response r = api().body(TestData.expenseType(TestData.EXPENSE_TYPE))
                .when().post(Endpoints.EXPENSE_TYPES);

        assertOkObject(r, "POST /claim/expenses/types");
        assertField(r, "data.name", TestData.EXPENSE_TYPE, "created expense type name");

        expenseTypeId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.EXPENSE_TYPES, expenseTypeId);
    }

    @Test(priority = 12, dependsOnMethods = "tcClm011_createExpenseType",
          description = "TC-CLM-012 | GET an expense type by id returns the created record")
    public void tcClm012_getExpenseTypeById() {
        Response r = api().pathParam("id", expenseTypeId).when().get(Endpoints.EXPENSE_TYPE_BY_ID);

        assertOkObject(r, "GET /claim/expenses/types/{id}");
        assertField(r, "data.id", expenseTypeId, "fetched expense type id");
    }

    @Test(priority = 20, description = "TC-CLM-020 | GET my claim requests returns 200")
    public void tcClm020_getMyClaimRequests() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.CLAIM_REQUESTS);
        assertOkList(r, "GET /claim/requests");
    }

    @Test(priority = 21, description = "TC-CLM-021 | GET all employees' claim requests returns 200")
    public void tcClm021_getEmployeeClaimRequests() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.EMP_CLAIM_REQUESTS);
        assertOkList(r, "GET /claim/employees/requests");
    }

    @Test(priority = 30, dependsOnMethods = "tcClm002_createClaimEvent", alwaysRun = true,
          description = "TC-CLM-030 | DELETE removes the claim event created by this run")
    public void tcClm030_deleteClaimEvent() {
        // alwaysRun: without this guard a skipped create sends {"ids":[null]} and fails.
        require(claimEventId, "a claim event created by this run");
        Response r = api().body(TestData.deleteIds(claimEventId)).when().delete(Endpoints.CLAIM_EVENTS);

        assertStatus(r, StatusCodes.OK, "DELETE /claim/events");
        unregisterCleanup(Endpoints.CLAIM_EVENTS, claimEventId);
    }

    @Test(priority = 31, dependsOnMethods = "tcClm011_createExpenseType", alwaysRun = true,
          description = "TC-CLM-031 | DELETE removes the expense type created by this run")
    public void tcClm031_deleteExpenseType() {
        // alwaysRun: without this guard a skipped create sends {"ids":[null]} and fails.
        require(expenseTypeId, "a expense type created by this run");
        Response r = api().body(TestData.deleteIds(expenseTypeId)).when().delete(Endpoints.EXPENSE_TYPES);

        assertStatus(r, StatusCodes.OK, "DELETE /claim/expenses/types");
        unregisterCleanup(Endpoints.EXPENSE_TYPES, expenseTypeId);
    }
}
