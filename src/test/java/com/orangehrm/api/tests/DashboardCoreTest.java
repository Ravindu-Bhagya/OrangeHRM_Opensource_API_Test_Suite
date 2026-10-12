package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * TC-DSH - Dashboard widgets, the employee directory and core product metadata.
 */
public class DashboardCoreTest extends BaseAPITest {

    @Test(priority = 1, description = "TC-DSH-001 | GET product info reports the expected product and version")
    public void tcDsh001_getAboutInfo() {
        Response r = api().when().get(Endpoints.ABOUT);

        assertOkObject(r, "GET /core/about");
        assertField(r, "data.productName", Credentials.EXPECTED_PRODUCT, "product name");
        assertField(r, "data.version", Credentials.EXPECTED_VERSION, "product version");
        assertFieldPresent(r, "data.companyName", "company name");
        Assert.assertTrue(r.jsonPath().getInt("data.numberOfActiveEmployee") >= 0,
                "Active employee count should be non-negative");
    }

    @Test(priority = 2, description = "TC-DSH-002 | GET the pending action summary widget returns 200")
    public void tcDsh002_getActionSummary() {
        Response r = api().when().get(Endpoints.DASH_ACTION_SUMMARY);

        assertOkList(r, "GET /dashboard/employees/action-summary");
        assertEachHas(r, "data.group", "action summary groups");
        assertEachHas(r, "data.pendingActionCount", "pending action counts");
    }

    @Test(priority = 3, description = "TC-DSH-003 | GET the employees-on-leave-today widget returns 200")
    public void tcDsh003_getEmployeesOnLeave() {
        Response r = api().queryParam("date", UserData.today())
                .when().get(Endpoints.DASH_LEAVES);

        assertOkList(r, "GET /dashboard/employees/leaves");
        assertFieldPresent(r, "meta.leavePeriodDefined", "leavePeriodDefined flag");
    }

    @Test(priority = 4, description = "TC-DSH-004 | GET the employee distribution by location widget returns 200")
    public void tcDsh004_getEmployeesByLocation() {
        Response r = api().when().get(Endpoints.DASH_LOCATIONS);
        assertOkList(r, "GET /dashboard/employees/locations");
    }

    @Test(priority = 5, description = "TC-DSH-005 | GET the employee distribution by subunit widget returns 200")
    public void tcDsh005_getEmployeesBySubunit() {
        Response r = api().when().get(Endpoints.DASH_SUBUNIT);
        assertOkList(r, "GET /dashboard/employees/subunit");
    }

    @Test(priority = 6, description = "TC-DSH-006 | GET the time-at-work widget returns the weekly breakdown")
    public void tcDsh006_getTimeAtWork() {
        Response r = api().when().get(Endpoints.DASH_TIME_AT_WORK);
        assertOkList(r, "GET /dashboard/employees/time-at-work");
    }

    @Test(priority = 7, description = "TC-DSH-007 | GET the quick-launch shortcuts returns the permitted actions")
    public void tcDsh007_getShortcuts() {
        Response r = api().when().get(Endpoints.DASH_SHORTCUTS);
        assertOkObject(r, "GET /dashboard/shortcuts");
    }

    @Test(priority = 8, description = "TC-DSH-008 | GET the on-leave-today widget configuration returns 200")
    public void tcDsh008_getLeaveWidgetConfig() {
        Response r = api().when().get(Endpoints.DASH_LEAVE_CONFIG);

        assertOkObject(r, "GET /dashboard/config/employee-on-leave-today");
        assertFieldPresent(r, "data.showOnlyAccessibleEmployeesOnLeaveToday",
                "on-leave widget scope flag");
    }

    @Test(priority = 10, description = "TC-DSH-010 | GET the employee directory returns a paginated list")
    public void tcDsh010_getDirectory() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.DIRECTORY_EMPLOYEES);

        assertOkList(r, "GET /directory/employees");
        assertHasTotal(r, "GET /directory/employees");
    }

    @Test(priority = 11, description = "TC-DSH-011 | The directory honours the pagination limit")
    public void tcDsh011_directoryPagination() {
        Response r = api().queryParam("limit", 2).when().get(Endpoints.DIRECTORY_EMPLOYEES);

        assertOkList(r, "GET /directory/employees?limit=2");
        Assert.assertTrue(r.jsonPath().getList("data").size() <= 2,
                "limit=2 returned more than 2 directory entries");
    }
}
