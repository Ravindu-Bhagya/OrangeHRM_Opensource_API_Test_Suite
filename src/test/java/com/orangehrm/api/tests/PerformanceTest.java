package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

/**
 * TC-PRF - Performance module: KPIs, performance trackers and reviews.
 */
public class PerformanceTest extends BaseAPITest {

    private static Integer kpiId;
    private static Integer trackerId;

    @Test(priority = 1, description = "TC-PRF-001 | GET all KPIs returns 200 with a paginated list")
    public void tcPrf001_getAllKpis() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.KPIS);

        assertOkList(r, "GET /performance/kpis");
        assertHasTotal(r, "GET /performance/kpis");
    }

    @Test(priority = 2, description = "TC-PRF-002 | POST creates a KPI against a job title")
    public void tcPrf002_createKpi() {
        int jobTitleId = jobTitleFixture();

        Response r = api().body(TestData.kpi(TestData.KPI, jobTitleId)).when().post(Endpoints.KPIS);

        assertOkObject(r, "POST /performance/kpis");
        assertField(r, "data.title", TestData.KPI, "created KPI title");
        assertFieldPresent(r, "data.id", "created KPI id");

        kpiId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.KPIS, kpiId);
    }

    @Test(priority = 3, dependsOnMethods = "tcPrf002_createKpi",
          description = "TC-PRF-003 | GET a KPI by id returns the created record with its rating range")
    public void tcPrf003_getKpiById() {
        Response r = api().pathParam("id", kpiId).when().get(Endpoints.KPI_BY_ID);

        assertOkObject(r, "GET /performance/kpis/{id}");
        assertField(r, "data.id", kpiId, "fetched KPI id");
        assertFieldPresent(r, "data.minRating", "KPI min rating");
        assertFieldPresent(r, "data.maxRating", "KPI max rating");
    }

    @Test(priority = 4, dependsOnMethods = "tcPrf002_createKpi",
          description = "TC-PRF-004 | PUT updates the KPI title")
    public void tcPrf004_updateKpi() {
        int jobTitleId = jobTitleFixture();

        Response r = api().pathParam("id", kpiId)
                .body(TestData.kpi(TestData.KPI + "_UPD", jobTitleId))
                .when().put(Endpoints.KPI_BY_ID);

        assertOkObject(r, "PUT /performance/kpis/{id}");
        assertField(r, "data.title", TestData.KPI + "_UPD", "updated KPI title");
    }

    @Test(priority = 5, description = "TC-PRF-005 | Creating a KPI without a title returns 422")
    public void tcPrf005_createKpiMissingTitle() {
        int jobTitleId = jobTitleFixture();

        Response r = api().body("""
                {"jobTitleId":%d,"minRating":0,"maxRating":100}""".formatted(jobTitleId))
                .when().post(Endpoints.KPIS);

        assertValidationError(r, null, "POST KPI with no title");
    }

    @Test(priority = 10, description = "TC-PRF-010 | GET all performance trackers returns 200")
    public void tcPrf010_getAllTrackers() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.TRACKERS);

        assertOkList(r, "GET /performance/trackers");
        assertHasTotal(r, "GET /performance/trackers");
    }

    @Test(priority = 11, description = "TC-PRF-011 | POST creates a performance tracker for an employee")
    public void tcPrf011_createTracker() {
        int empNumber = require(firstEmpNumber(), "an employee to track");

        Response r = api().body("""
                {"empNumber":%d,"trackerName":"%s","reviewerEmpNumbers":[%d]}"""
                .formatted(empNumber, TestData.KPI + "_TRK", empNumber))
                .when().post(Endpoints.CONFIG_TRACKERS);

        // Trackers are created under /performance/config/trackers;
        // POST on /performance/trackers returns 405.
        assertOkObject(r, "POST /performance/config/trackers");
        assertField(r, "data.trackerName", TestData.KPI + "_TRK", "created tracker name");
        trackerId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.CONFIG_TRACKERS, trackerId);
    }

    @Test(priority = 12, description = "TC-PRF-012 | GET my performance trackers returns 200")
    public void tcPrf012_getMyTrackers() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.EMP_TRACKERS);
        assertOkList(r, "GET /performance/employees/trackers");
    }

    @Test(priority = 13, description = "TC-PRF-013 | GET the tracker configuration returns 200")
    public void tcPrf013_getTrackerConfig() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.CONFIG_TRACKERS);
        assertOkList(r, "GET /performance/config/trackers");
    }

    @Test(priority = 20, description = "TC-PRF-020 | GET managed performance reviews returns 200")
    public void tcPrf020_getManagedReviews() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.MANAGE_REVIEWS);

        assertOkList(r, "GET /performance/manage/reviews");
        assertHasTotal(r, "GET /performance/manage/reviews");
    }

    @Test(priority = 21, description = "TC-PRF-021 | GET my performance reviews returns 200")
    public void tcPrf021_getMyReviews() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.MY_REVIEWS);
        assertOkList(r, "GET /performance/reviews");
    }

    @Test(priority = 22, description = "TC-PRF-022 | GET employee reviews returns 200")
    public void tcPrf022_getEmployeeReviews() {
        Response r = api().queryParam("limit", 20).when().get(Endpoints.EMP_REVIEWS);
        assertOkList(r, "GET /performance/employees/reviews");
    }

    @Test(priority = 23, description = "TC-PRF-023 | GET performance supervisors returns 200 (rejects a limit param)")
    public void tcPrf023_getSupervisors() {
        Response r = api().when().get(Endpoints.PERF_SUPERVISORS);
        assertOkList(r, "GET /performance/supervisors");

        // This endpoint is not paginated - passing limit is rejected as an invalid parameter.
        Response paged = api().queryParam("limit", 20).when().get(Endpoints.PERF_SUPERVISORS);
        assertValidationError(paged, "limit", "GET /performance/supervisors?limit=20");
    }

    @Test(priority = 30, dependsOnMethods = "tcPrf002_createKpi", alwaysRun = true,
          description = "TC-PRF-030 | DELETE removes the KPI created by this run")
    public void tcPrf030_deleteKpi() {
        // alwaysRun: without this guard a skipped create sends {"ids":[null]} and fails.
        require(kpiId, "a KPI created by this run");
        Response r = api().body(TestData.deleteIds(kpiId)).when().delete(Endpoints.KPIS);

        assertStatus(r, StatusCodes.OK, "DELETE /performance/kpis");
        unregisterCleanup(Endpoints.KPIS, kpiId);
    }

    @Test(priority = 31, dependsOnMethods = "tcPrf011_createTracker", alwaysRun = true,
          description = "TC-PRF-031 | DELETE removes the tracker created by this run")
    public void tcPrf031_deleteTracker() {
        if (trackerId == null) throw new org.testng.SkipException("No tracker was created");

        Response r = api().body(TestData.deleteIds(trackerId)).when().delete(Endpoints.CONFIG_TRACKERS);
        assertStatus(r, StatusCodes.OK, "DELETE /performance/config/trackers");
        unregisterCleanup(Endpoints.CONFIG_TRACKERS, trackerId);
    }
}
