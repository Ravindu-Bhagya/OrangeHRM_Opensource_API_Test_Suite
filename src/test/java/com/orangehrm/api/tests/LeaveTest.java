package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.SkipException;

import java.util.List;

/**
 * TC-LEV - Leave module: leave types, entitlements, holidays, the work week,
 * the leave period and leave-balance calculation.
 */
public class LeaveTest extends BaseAPITest {

    private static Integer leaveTypeId;
    private static Integer entitlementId;

    /* ---------------------------- Leave types ---------------------------- */

    @Test(priority = 1, description = "TC-LEV-001 | GET all leave types returns 200 with a list")
    public void tcLev001_getAllLeaveTypes() {
        Response r = api().queryParam("limit", 50).when().get(Endpoints.LEAVE_TYPES);

        assertOkList(r, "GET /leave/leave-types");
        assertHasTotal(r, "GET /leave/leave-types");
    }

    @Test(priority = 2, description = "TC-LEV-002 | POST creates a leave type and returns it with an id")
    public void tcLev002_createLeaveType() {
        Response r = api().body(TestData.leaveType(TestData.JOB_TITLE + "_LT"))
                .when().post(Endpoints.LEAVE_TYPES);

        assertOkObject(r, "POST /leave/leave-types");
        assertField(r, "data.name", TestData.JOB_TITLE + "_LT", "created leave type name");
        assertField(r, "data.situational", false, "created leave type situational flag");
        assertFieldPresent(r, "data.id", "created leave type id");

        leaveTypeId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.LEAVE_TYPES, leaveTypeId);
    }

    @Test(priority = 3, dependsOnMethods = "tcLev002_createLeaveType",
          description = "TC-LEV-003 | GET a leave type by id returns the created record")
    public void tcLev003_getLeaveTypeById() {
        Response r = api().pathParam("id", leaveTypeId).when().get(Endpoints.LEAVE_TYPE_BY_ID);

        assertOkObject(r, "GET /leave/leave-types/{id}");
        assertField(r, "data.id", leaveTypeId, "fetched leave type id");
    }

    @Test(priority = 4, dependsOnMethods = "tcLev002_createLeaveType",
          description = "TC-LEV-004 | PUT renames a leave type")
    public void tcLev004_updateLeaveType() {
        String updated = TestData.JOB_TITLE + "_LT_UPD";
        Response r = api().pathParam("id", leaveTypeId)
                .body(TestData.leaveType(updated))
                .when().put(Endpoints.LEAVE_TYPE_BY_ID);

        assertOkObject(r, "PUT /leave/leave-types/{id}");
        assertField(r, "data.name", updated, "updated leave type name");
    }

    @Test(priority = 5, description = "TC-LEV-005 | Creating a leave type without a name returns 422")
    public void tcLev005_createLeaveTypeMissingName() {
        Response r = api().body("{\"situational\":false}").when().post(Endpoints.LEAVE_TYPES);

        assertValidationError(r, "name", "POST leave type with no name");
    }

    @Test(priority = 6, description = "TC-LEV-006 | GET eligible leave types for the logged-in user")
    public void tcLev006_getEligibleLeaveTypes() {
        Response r = api().when().get(Endpoints.LEAVE_TYPES_ELIGIBLE);
        assertOkList(r, "GET /leave/leave-types/eligible");
    }

    /* ---------------------------- Entitlements ---------------------------- */

    @Test(priority = 10, dependsOnMethods = "tcLev002_createLeaveType",
          description = "TC-LEV-010 | POST grants a leave entitlement to an employee")
    public void tcLev010_createEntitlement() {
        int empNumber = require(firstEmpNumber(), "an employee to grant entitlement to");

        Response r = api().body(TestData.leaveEntitlement(empNumber, leaveTypeId,
                        UserData.yearStart(), UserData.yearEnd(), 10))
                .when().post(Endpoints.LEAVE_ENTITLEMENTS);

        assertOkObject(r, "POST /leave/leave-entitlements");
        assertFieldPresent(r, "data.id", "created entitlement id");
        Assert.assertEquals(r.jsonPath().getFloat("data.entitlement"), 10.0f,
                "Granted entitlement should be 10 days\n" + safeBody(r));

        entitlementId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.LEAVE_ENTITLEMENTS, entitlementId);
    }

    @Test(priority = 11, dependsOnMethods = "tcLev010_createEntitlement",
          description = "TC-LEV-011 | GET entitlements for an employee lists the grant")
    public void tcLev011_getEntitlementsForEmployee() {
        int empNumber = require(firstEmpNumber(), "an employee");

        Response r = api().queryParam("empNumber", empNumber)
                .queryParam("leaveTypeId", leaveTypeId)
                .queryParam("fromDate", UserData.yearStart())
                .queryParam("toDate", UserData.yearEnd())
                .when().get(Endpoints.LEAVE_ENTITLEMENTS);

        assertOkList(r, "GET /leave/leave-entitlements");
    }

    @Test(priority = 12, description = "TC-LEV-012 | GET entitlements without required filters returns 422")
    public void tcLev012_getEntitlementsMissingFiltersRejected() {
        Response r = api().when().get(Endpoints.EMP_LEAVE_ENTITLEMENTS);

        assertValidationError(r, null, "GET /leave/employees/leave-entitlements with no filters");
    }

    @Test(priority = 13, dependsOnMethods = "tcLev010_createEntitlement",
          description = "TC-LEV-013 | DELETE removes the entitlement")
    public void tcLev013_deleteEntitlement() {
        Response r = api().body(TestData.deleteIds(entitlementId))
                .when().delete(Endpoints.LEAVE_ENTITLEMENTS);

        assertStatus(r, StatusCodes.OK, "DELETE /leave/leave-entitlements");
        unregisterCleanup(Endpoints.LEAVE_ENTITLEMENTS, entitlementId);
    }

    /* ---------------------------- Leave balance ---------------------------- */

    @Test(priority = 20, description = "TC-LEV-020 | GET the leave balance for a leave type returns the breakdown")
    public void tcLev020_getLeaveBalance() {
        Integer anyLeaveType = require(firstId(Endpoints.LEAVE_TYPES), "a leave type");

        Response r = api().pathParam("id", anyLeaveType).when().get(Endpoints.LEAVE_BALANCE);

        assertOkObject(r, "GET /leave/leave-balance/leave-type/{id}");
        for (String field : new String[]{"entitled", "used", "scheduled", "pending", "taken", "balance"}) {
            assertFieldPresent(r, "data.balance." + field, "leave balance '" + field + "'");
        }
    }

    /* ---------------------------- Holidays ---------------------------- */

    @Test(priority = 30, description = "TC-LEV-030 | GET holidays within a date range returns 200")
    public void tcLev030_getHolidays() {
        Response r = api().queryParam("fromDate", UserData.yearStart())
                .queryParam("toDate", UserData.yearEnd())
                .when().get(Endpoints.HOLIDAYS);

        assertOkList(r, "GET /leave/holidays");
        assertHasTotal(r, "GET /leave/holidays");
    }

    @Test(priority = 31, description = "TC-LEV-031 | GET holidays without a date range returns 422")
    public void tcLev031_getHolidaysWithoutRangeRejected() {
        Response r = api().when().get(Endpoints.HOLIDAYS);

        assertValidationError(r, null, "GET /leave/holidays with no date range");
    }

    @Test(priority = 32, description = "TC-LEV-032 | A full-day holiday can be created and deleted")
    public void tcLev032_holidayLifecycle() {
        Response create = api().body(TestData.holiday(TestData.HOLIDAY,
                        freeHolidayDate(45), TestData.HOLIDAY_FULL_DAY))
                .when().post(Endpoints.HOLIDAYS);

        assertOkObject(create, "POST /leave/holidays");
        assertField(create, "data.name", TestData.HOLIDAY, "created holiday name");
        assertField(create, "data.length", TestData.HOLIDAY_FULL_DAY, "created holiday length");
        int id = create.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.HOLIDAYS, id);

        Response delete = api().body(TestData.deleteIds(id)).when().delete(Endpoints.HOLIDAYS);
        assertStatus(delete, StatusCodes.OK, "DELETE /leave/holidays");
        unregisterCleanup(Endpoints.HOLIDAYS, id);
    }

    @Test(priority = 33, description = "TC-LEV-033 | A half-day holiday reports lengthName 'Half Day'")
    public void tcLev033_halfDayHoliday() {
        Response create = api().body(TestData.holiday(TestData.HOLIDAY + "_HALF",
                        freeHolidayDate(46), TestData.HOLIDAY_HALF_DAY))
                .when().post(Endpoints.HOLIDAYS);

        assertOkObject(create, "POST a half-day holiday");
        int id = create.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.HOLIDAYS, id);
        assertField(create, "data.lengthName", "Half Day", "half-day holiday lengthName");

        api().body(TestData.deleteIds(id)).when().delete(Endpoints.HOLIDAYS);
        unregisterCleanup(Endpoints.HOLIDAYS, id);
    }

    @Test(priority = 34, description = "TC-LEV-034 | A holiday with an unsupported length returns 422")
    public void tcLev034_holidayInvalidLengthRejected() {
        Response r = api().body(TestData.holiday(TestData.HOLIDAY + "_BAD",
                        UserData.daysFromNow(47), 8))
                .when().post(Endpoints.HOLIDAYS);

        assertValidationError(r, "length", "POST holiday with length=8");
    }

    /**
     * First date on or after today+offset that has no holiday yet. Only one holiday may exist
     * per date, so a fixed date collides with any holiday already there (a real one, or one
     * left by an aborted run).
     */
    private String freeHolidayDate(int offsetDays) {
        Response r = api().queryParam("fromDate", UserData.daysFromNow(offsetDays))
                .queryParam("toDate", UserData.daysFromNow(offsetDays + 365))
                .queryParam("limit", 500)
                .when().get(Endpoints.HOLIDAYS);
        List<String> taken = r.statusCode() == StatusCodes.OK
                ? r.jsonPath().getList("data.date") : List.of();
        for (int d = offsetDays; d < offsetDays + 365; d++) {
            String date = UserData.daysFromNow(d);
            if (taken == null || !taken.contains(date)) return date;
        }
        throw new SkipException("No free holiday date in the next year");
    }

    /* ---------------------------- Configuration ---------------------------- */

    @Test(priority = 40, description = "TC-LEV-040 | GET the work week returns all seven days")
    public void tcLev040_getWorkWeek() {
        Response r = api().when().get(Endpoints.WORKWEEK);

        assertOkObject(r, "GET /leave/workweek");
        for (String day : new String[]{"monday", "tuesday", "wednesday", "thursday",
                                       "friday", "saturday", "sunday"}) {
            assertFieldPresent(r, "data." + day, "work week '" + day + "'");
        }
    }

    @Test(priority = 41, description = "TC-LEV-041 | GET the leave period returns its start month and day")
    public void tcLev041_getLeavePeriod() {
        Response r = api().when().get(Endpoints.LEAVE_PERIOD);

        assertOkObject(r, "GET /leave/leave-period");
        assertFieldPresent(r, "data.startMonth", "leave period start month");
        assertFieldPresent(r, "data.startDay", "leave period start day");
    }

    @Test(priority = 42, description = "TC-LEV-042 | GET the defined leave periods returns a list")
    public void tcLev042_getLeavePeriods() {
        Response r = api().when().get(Endpoints.LEAVE_PERIODS);

        assertOkList(r, "GET /leave/leave-periods");
        Assert.assertFalse(r.jsonPath().getList("data").isEmpty(),
                "At least one leave period should be defined");
    }

    /* ---------------------------- Leave requests ---------------------------- */

    @Test(priority = 50, description = "TC-LEV-050 | GET all employees' leave requests returns 200")
    public void tcLev050_getEmployeeLeaveRequests() {
        Response r = api().queryParam("limit", 20)
                .queryParam("fromDate", UserData.yearStart())
                .queryParam("toDate", UserData.yearEnd())
                .when().get(Endpoints.EMP_LEAVE_REQUESTS);

        assertOkList(r, "GET /leave/employees/leave-requests");
        assertHasTotal(r, "GET /leave/employees/leave-requests");
    }

    @Test(priority = 51, description = "TC-LEV-051 | GET my leave requests returns 200")
    public void tcLev051_getMyLeaveRequests() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.LEAVE_REQUESTS);

        assertOkList(r, "GET /leave/leave-requests");
    }

    @Test(priority = 52, description = "TC-LEV-052 | GET overlapping leaves for a date range returns 200")
    public void tcLev052_getOverlapLeaves() {
        int empNumber = require(firstEmpNumber(), "an employee");

        Response r = api().queryParam("fromDate", UserData.daysFromNow(10))
                .queryParam("toDate", UserData.daysFromNow(11))
                .queryParam("empNumber", empNumber)
                .when().get(Endpoints.OVERLAP_LEAVES);

        assertOkList(r, "GET /leave/overlap-leaves");
    }

    /* ---------------------------- Teardown ---------------------------- */

    @Test(priority = 99, dependsOnMethods = "tcLev002_createLeaveType", alwaysRun = true,
          description = "TC-LEV-099 | DELETE removes the leave type created by this run")
    public void tcLev099_deleteLeaveType() {
        // alwaysRun: without this guard a skipped create sends {"ids":[null]} and fails.
        require(leaveTypeId, "a leave type created by this run");
        Response r = api().body(TestData.deleteIds(leaveTypeId))
                .when().delete(Endpoints.LEAVE_TYPES);

        assertStatus(r, StatusCodes.OK, "DELETE /leave/leave-types");
        unregisterCleanup(Endpoints.LEAVE_TYPES, leaveTypeId);
    }
}
