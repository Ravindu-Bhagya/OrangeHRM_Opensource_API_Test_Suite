package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * TC-ATT - Attendance (punch in/out, records, configuration) and
 * TC-TIM - the Time module (projects, customers, timesheets).
 *
 * The punch-in test creates a real attendance record for the logged-in user; it is always
 * removed again in @AfterClass so a scheduled daily run never leaves the account stuck in
 * a "Punched In" state.
 */
public class TimeAttendanceTest extends BaseAPITest {

    private static Integer attendanceRecordId;
    private static boolean punchedIn;

    private static final ZoneId USER_ZONE = ZoneId.of("Asia/Colombo");

    /**
     * The server's current date and time converted to the user's zone. When the instance has
     * canUserChangeCurrentTime=false, a punch must carry exactly this time, otherwise the API
     * answers 400 "Provided Date And Time Invalid" - so never send a fixed clock time.
     */
    private String[] serverNow() {
        Response r = api().when().get(Endpoints.ATTENDANCE_DATETIME);
        assertOkObject(r, "GET /attendance/current-datetime");
        ZonedDateTime utc = LocalDateTime.parse(r.jsonPath().getString("data.utcDate") + "T"
                + r.jsonPath().getString("data.utcTime")).atZone(ZoneOffset.UTC);
        ZonedDateTime local = utc.withZoneSameInstant(USER_ZONE);
        return new String[] { local.toLocalDate().toString(),
                local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) };
    }

    /**
     * An aborted earlier run can leave the account "Punched In", which makes TC-ATT-010 fail
     * with "Already Punched In". Close it first - but only if this suite opened it.
     */
    @BeforeClass(alwaysRun = true)
    public void closeStalePunchFromPreviousRun() {
        Response latest = api().when().get(Endpoints.ATTENDANCE_LATEST);
        if (latest.statusCode() != StatusCodes.OK) return;
        String state = latest.jsonPath().getString("data.state.id");
        String note = latest.jsonPath().getString("data.punchIn.note");
        if (!"PUNCHED IN".equals(state) || !TestData.DESCRIPTION.equals(note)) return;
        Response out = punchOut("QA stale punch closed");
        System.out.println("Closed stale punch-in from a previous run -> " + out.statusCode());
    }

    /* ---------------------------- Configuration ---------------------------- */

    @Test(priority = 1, description = "TC-ATT-001 | GET attendance configuration returns the permission flags")
    public void tcAtt001_getAttendanceConfig() {
        Response r = api().when().get(Endpoints.ATTENDANCE_CONFIGS);

        assertOkObject(r, "GET /attendance/configs");
        assertFieldPresent(r, "data.canUserChangeCurrentTime", "canUserChangeCurrentTime flag");
        assertFieldPresent(r, "data.canUserModifyAttendance", "canUserModifyAttendance flag");
        assertFieldPresent(r, "data.canSupervisorModifyAttendance", "canSupervisorModifyAttendance flag");
    }

    @Test(priority = 2, description = "TC-ATT-002 | GET the server current date/time returns UTC values")
    public void tcAtt002_getCurrentDateTime() {
        Response r = api().when().get(Endpoints.ATTENDANCE_DATETIME);

        assertOkObject(r, "GET /attendance/current-datetime");
        assertFieldPresent(r, "data.utcDate", "server UTC date");
        assertFieldPresent(r, "data.utcTime", "server UTC time");
        Assert.assertTrue(r.jsonPath().getString("data.utcDate").matches("\\d{4}-\\d{2}-\\d{2}"),
                "utcDate should be yyyy-MM-dd but was " + r.jsonPath().getString("data.utcDate"));
    }

    @Test(priority = 3, description = "TC-ATT-003 | GET supported timezones returns the full IANA list")
    public void tcAtt003_getTimezones() {
        Response r = api().when().get(Endpoints.ATTENDANCE_TIMEZONES);

        assertOkList(r, "GET /attendance/timezones");
        Assert.assertTrue(r.jsonPath().getList("data").size() > 100,
                "Expected a large timezone list, got " + r.jsonPath().getList("data").size());
    }

    /* ---------------------------- Records (read) ---------------------------- */

    @Test(priority = 4, description = "TC-ATT-004 | GET attendance records for a date returns a total and sum")
    public void tcAtt004_getAttendanceRecordsForDate() {
        Response r = api().queryParam("date", UserData.today())
                .when().get(Endpoints.ATTENDANCE_RECORDS);

        assertOkList(r, "GET /attendance/records?date=");
        assertHasTotal(r, "GET /attendance/records");
        assertFieldPresent(r, "meta.sum.label", "attendance hours sum label");
    }

    @Test(priority = 5, description = "TC-ATT-005 | GET the attendance summary for all employees returns 200")
    public void tcAtt005_getAttendanceSummary() {
        Response r = api().queryParam("limit", 10).when().get(Endpoints.ATTENDANCE_SUMMARY);

        assertOkList(r, "GET /attendance/employees/summary");
        assertHasTotal(r, "GET /attendance/employees/summary");
    }

    @Test(priority = 6, description = "TC-ATT-006 | GET attendance records for a specific employee returns 200")
    public void tcAtt006_getEmployeeAttendanceRecords() {
        int empNumber = require(firstEmpNumber(), "an employee");

        Response r = api().pathParam("empNumber", empNumber)
                .queryParam("date", UserData.today())
                .when().get(Endpoints.EMP_ATTENDANCE_RECORDS);

        assertOkList(r, "GET /attendance/employees/{empNumber}/records");
    }

    /* ---------------------------- Punch cycle ---------------------------- */

    @Test(priority = 10, description = "TC-ATT-010 | POST punch-in creates an open attendance record")
    public void tcAtt010_punchIn() {
        String[] now = serverNow();
        Response r = api().body("""
                {"date":"%s","time":"%s","timezoneOffset":5.5,
                 "timezoneName":"Asia/Colombo","note":"%s"}"""
                .formatted(now[0], now[1], TestData.DESCRIPTION))
                .when().post(Endpoints.ATTENDANCE_RECORDS);

        assertOkObject(r, "POST /attendance/records (punch in)");
        assertFieldPresent(r, "data.id", "attendance record id");
        assertFieldPresent(r, "data.punchIn.userTime", "punch-in time");
        assertField(r, "data.punchIn.userDate", now[0], "punch-in date");
        Assert.assertNull(r.jsonPath().get("data.punchOut.utcTime"),
                "A fresh punch-in should have no punch-out time yet");
        attendanceRecordId = r.jsonPath().getInt("data.id");
        punchedIn = true;
    }

    @Test(priority = 11, dependsOnMethods = "tcAtt010_punchIn",
          description = "TC-ATT-011 | GET the latest record returns the open punch-in")
    public void tcAtt011_getLatestRecordAfterPunchIn() {
        Response r = api().when().get(Endpoints.ATTENDANCE_LATEST);

        assertOkObject(r, "GET /attendance/records/latest");
        assertField(r, "data.id", attendanceRecordId, "latest record id");
    }

    @Test(priority = 12, dependsOnMethods = "tcAtt010_punchIn",
          description = "TC-ATT-012 | A second punch-in while already punched in is rejected")
    public void tcAtt012_doublePunchInRejected() {
        String[] now = serverNow();
        Response r = api().body("""
                {"date":"%s","time":"%s","timezoneOffset":5.5,
                 "timezoneName":"Asia/Colombo","note":"duplicate"}"""
                .formatted(now[0], now[1]))
                .when().post(Endpoints.ATTENDANCE_RECORDS);

        assertError(r, StatusCodes.BAD_REQUEST, Messages.ALREADY_PUNCHED_IN,
                "POST a second punch-in while already punched in");
    }

    @Test(priority = 13, dependsOnMethods = "tcAtt010_punchIn",
          description = "TC-ATT-013 | Punch-out with an unrecognised offset field returns 422")
    public void tcAtt013_punchOutWrongFieldNamesRejected() {
        Response r = api().pathParam("id", attendanceRecordId)
                .body("""
                      {"date":"%s","time":"17:00","timezoneOffset":5.5,
                       "timezoneName":"Asia/Colombo","note":"out"}"""
                      .formatted(UserData.today()))
                .when().put(Endpoints.ATTENDANCE_RECORD_BY_ID);

        assertValidationError(r, null, "PUT punch-out with the punch-in field names");
    }

    @Test(priority = 14, dependsOnMethods = "tcAtt010_punchIn",
          description = "TC-ATT-014 | PUT punch-out on the collection closes the open record")
    public void tcAtt014_punchOut() {
        Response r = punchOut("QA punch-out");

        // The real punch-out is PUT /attendance/records (no id). PUT /attendance/records/{id}
        // is the admin *edit* endpoint - using it for punch-out was the source of the earlier
        // "punch-out does not persist" false defect.
        assertOkObject(r, "PUT /attendance/records (punch out)");
        assertField(r, "data.id", attendanceRecordId, "punched-out record id");
        assertFieldPresent(r, "data.punchOut.utcTime", "punch-out time");
        assertField(r, "data.state.id", "PUNCHED OUT", "record state after punch-out");
        punchedIn = false;
    }

    @Test(priority = 15, dependsOnMethods = "tcAtt014_punchOut",
          description = "TC-ATT-015 | DELETE the record succeeds only when attendance modification is allowed")
    public void tcAtt015_deleteAttendanceRecord() {
        Response r = api().body(TestData.deleteIds(attendanceRecordId))
                .when().delete(Endpoints.ATTENDANCE_RECORDS);

        if (!canModifyAttendance()) {
            // canUserModifyAttendance=false: the admin may not delete even their own records.
            // The record stays, but it is punched out, so the next run is not blocked.
            assertForbidden(r, "DELETE /attendance/records with modification disabled");
            return;
        }
        assertStatus(r, StatusCodes.OK, "DELETE /attendance/records");
        Response latest = api().when().get(Endpoints.ATTENDANCE_LATEST);
        assertRecordNotFound(latest, "GET latest after deleting the only attendance record");
        attendanceRecordId = null;
    }

    private Response punchOut(String note) {
        String[] now = serverNow();
        return api().body("""
                {"date":"%s","time":"%s","timezoneOffset":5.5,
                 "timezoneName":"Asia/Colombo","note":"%s"}"""
                .formatted(now[0], now[1], note))
                .when().put(Endpoints.ATTENDANCE_RECORDS);
    }

    private boolean canModifyAttendance() {
        return api().when().get(Endpoints.ATTENDANCE_CONFIGS)
                .jsonPath().getBoolean("data.canUserModifyAttendance");
    }

    /* ---------------------------- Time module ---------------------------- */

    @Test(priority = 20, description = "TC-TIM-020 | GET the timesheet period start day returns 200")
    public void tcTim020_getTimesheetPeriod() {
        Response r = api().when().get(Endpoints.TIMESHEET_PERIOD);

        assertOkObject(r, "GET /time/time-sheet-period");
        assertFieldPresent(r, "data.startDay", "timesheet period start day");
    }

    @Test(priority = 21, description = "TC-TIM-021 | ACCESS: Admin access to projects matches the instance profile")
    public void tcTim021_projectsForbiddenForAdmin() {
        Response r = api().queryParam("limit", 10).when().get(Endpoints.PROJECTS);

        // The Time module is enabled (see TC-ADM-050) and this account holds the Admin role.
        // Whether that reaches the /time data endpoints differs per instance - see InstanceProfile.
        assertAdminTimeAccess(r, "GET /time/projects as admin");
    }

    @Test(priority = 22, description = "TC-TIM-022 | ACCESS: Admin access to customers matches the instance profile")
    public void tcTim022_customersForbiddenForAdmin() {
        Response r = api().queryParam("limit", 10).when().get(Endpoints.CUSTOMERS);

        assertAdminTimeAccess(r, "GET /time/customers as admin");
    }

    @Test(priority = 23, description = "TC-TIM-023 | ACCESS: Admin access to timesheets matches the instance profile")
    public void tcTim023_timesheetsForbiddenForAdmin() {
        Response r = api().when().get(Endpoints.TIMESHEETS);

        assertAdminTimeAccess(r, "GET /time/timesheets as admin");
    }

    /* ---------------------------- Safety net ---------------------------- */

    @AfterClass(alwaysRun = true)
    public void ensureNotLeftPunchedIn() {
        if (punchedIn) {
            Response out = punchOut("QA safety punch-out");
            System.out.println("Safety punch-out -> " + out.statusCode());
            punchedIn = false;
        }
        if (attendanceRecordId == null || !canModifyAttendance()) return;
        Response r = api().body(TestData.deleteIds(attendanceRecordId))
                .when().delete(Endpoints.ATTENDANCE_RECORDS);
        System.out.println("Safety cleanup of attendance record " + attendanceRecordId
                + " -> " + r.statusCode());
        attendanceRecordId = null;
    }
}
