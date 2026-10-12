package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * TC-BND - the edges of the API surface: routes that exist but are not implemented,
 * methods a collection does not accept, single-record 404s on the sub-resources the
 * other classes only read as collections, and the authorisation boundary on Time.
 *
 * Every case here pins CURRENT behaviour of OS 5.9. Several of them assert something
 * that is arguably wrong; those are named FINDING so that the day the product changes,
 * the failure names the contract that moved rather than looking like a flake.
 */
public class ModuleBoundaryTest extends BaseAPITest {

    private static Integer empNumber;

    private static int employee() {
        if (empNumber == null) empNumber = require(firstEmpNumber(), "an employee in PIM");
        return empNumber;
    }

    /* ================================================================== */
    /* Routed but not implemented                                          */
    /* ================================================================== */

    @Test(priority = 1, description = "TC-BND-001 | The employee terminations route is present but returns 501")
    public void tcBnd001_terminationsNotImplemented() {
        Response r = api().pathParam("empNumber", employee()).when().get(Endpoints.TERMINATIONS);

        assertNotImplemented(r, "GET /pim/employees/{empNumber}/terminations");
    }

    /* ================================================================== */
    /* Methods a collection does not accept                                */
    /* ================================================================== */

    @Test(priority = 2, description = "TC-BND-002 | GET on the Buzz shares collection returns 405")
    public void tcBnd002_buzzSharesRejectsGet() {
        Response r = api().when().get(Endpoints.BUZZ_SHARES);

        assertBodylessError(r, StatusCodes.METHOD_NOT_ALLOWED, "GET /buzz/shares");
    }

    /* ================================================================== */
    /* Single-record 404s - JSON envelope vs empty body                    */
    /* ================================================================== */

    @Test(priority = 10, description = "TC-BND-010 | A non-existent work shift returns a 404 JSON envelope")
    public void tcBnd010_missingWorkShift() {
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.WORK_SHIFT_BY_ID);

        assertRecordNotFound(r, "GET /admin/work-shifts/99999999");
    }

    @Test(priority = 11, description = "TC-BND-011 | A non-existent holiday returns a 404 JSON envelope")
    public void tcBnd011_missingHoliday() {
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.HOLIDAY_BY_ID);

        assertRecordNotFound(r, "GET /leave/holidays/99999999");
    }

    @Test(priority = 12, description = "TC-BND-012 | A non-existent leave entitlement returns a 404 JSON envelope")
    public void tcBnd012_missingLeaveEntitlement() {
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.LEAVE_ENTITLEMENT_BY_ID);

        assertRecordNotFound(r, "GET /leave/leave-entitlements/99999999");
    }

    @Test(priority = 13, description = "TC-BND-013 | A non-existent Buzz post returns a 404 JSON envelope")
    public void tcBnd013_missingBuzzPost() {
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.BUZZ_POST_BY_ID);

        assertRecordNotFound(r, "GET /buzz/posts/99999999");
    }

    @Test(priority = 14, description = "TC-BND-014 | INCONSISTENCY: a non-existent performance tracker 404s with an empty body")
    public void tcBnd014_missingTrackerHasNoEnvelope() {
        // Every other single-record 404 above carries {"error":{...}}; this one does not,
        // so a client parsing the envelope unconditionally breaks here.
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.TRACKER_BY_ID);

        assertBodylessError(r, StatusCodes.NOT_FOUND, "GET /performance/trackers/99999999");
    }

    @Test(priority = 15, description = "TC-BND-015 | FINDING: logs for a non-existent tracker return 200 with an empty list")
    public void tcBnd015_logsForMissingTrackerReturn200() {
        // The tracker itself 404s (TC-BND-014) but its log collection reports success,
        // so a caller cannot tell "no logs yet" from "no such tracker".
        Response r = api().pathParam("id", 99999999).when().get(Endpoints.TRACKER_LOGS);

        assertOkList(r, "GET /performance/trackers/99999999/logs");
        Assert.assertTrue(r.jsonPath().getList("data").isEmpty(),
                "Logs for a tracker that does not exist should at least be empty");
        assertFieldPresent(r, "meta.positive", "positive log count in meta");
        assertFieldPresent(r, "meta.negative", "negative log count in meta");
    }

    @Test(priority = 16, description = "TC-BND-016 | The employee photo route 404s with an empty body, not a JSON envelope")
    public void tcBnd016_employeePhotoIsBodyless() {
        Response r = api().pathParam("empNumber", employee()).when().get(Endpoints.EMP_PHOTO);

        assertBodylessError(r, StatusCodes.NOT_FOUND,
                "GET /pim/employees/{empNumber}/photo on api/v2");
    }

    /* ================================================================== */
    /* Required query parameters                                           */
    /* ================================================================== */

    @Test(priority = 20, description = "TC-BND-020 | Maintenance candidate search without its required parameters returns 422")
    public void tcBnd020_maintenanceCandidatesNeedsParameters() {
        Response r = api().when().get(Endpoints.MAINTENANCE_CANDIDATES);

        assertValidationError(r, "vacancyId", "GET /maintenance/candidates with no parameters");
    }

    /* ================================================================== */
    /* Authorisation boundary on Time                                      */
    /* ================================================================== */

    @Test(priority = 30, description = "TC-BND-030 | The Time module is enabled on this instance")
    public void tcBnd030_timeModuleIsEnabled() {
        // Pinned first so the three 403s below cannot be explained away as a disabled module.
        Response r = api().when().get(Endpoints.MODULES);

        assertOkObject(r, "GET /admin/modules");
        assertField(r, "data.time", true, "the time module flag");
    }

    @Test(priority = 31, dependsOnMethods = "tcBnd030_timeModuleIsEnabled",
          description = "TC-BND-031 | ACCESS: Admin access to Time customers matches the instance profile")
    public void tcBnd031_timeCustomersForbidden() {
        Response r = api().queryParam("limit", 1).when().get(Endpoints.CUSTOMERS);

        assertAdminTimeAccess(r, "GET /time/customers as Admin");
    }

    @Test(priority = 32, dependsOnMethods = "tcBnd030_timeModuleIsEnabled",
          description = "TC-BND-032 | ACCESS: Admin access to project admins matches the instance profile")
    public void tcBnd032_projectAdminsForbidden() {
        Response r = api().when().get(Endpoints.PROJECT_ADMINS);

        assertAdminTimeAccess(r, "GET /time/project-admins as Admin");
    }

    @Test(priority = 33, dependsOnMethods = "tcBnd030_timeModuleIsEnabled",
          description = "TC-BND-033 | ACCESS: Admin access to an employee's timesheets matches the instance profile")
    public void tcBnd033_employeeTimesheetsForbidden() {
        Response r = api().pathParam("empNumber", employee()).when().get(Endpoints.EMP_TIMESHEETS);

        assertAdminTimeAccess(r, "GET /time/employees/{empNumber}/timesheets as Admin");
    }

    @Test(priority = 34, dependsOnMethods = "tcBnd030_timeModuleIsEnabled",
          description = "TC-BND-034 | ACCESS: the same rule covers the whole Time module, projects included")
    public void tcBnd034_timeProjectsAlsoForbidden() {
        // Projects, customers, project admins and timesheets share one rule: on an instance
        // that refuses an Admin, none of it is reachable despite time=true in /admin/modules.
        Response r = api().queryParam("limit", 1).when().get(Endpoints.PROJECTS);

        assertAdminTimeAccess(r, "GET /time/projects as Admin");
    }
}
