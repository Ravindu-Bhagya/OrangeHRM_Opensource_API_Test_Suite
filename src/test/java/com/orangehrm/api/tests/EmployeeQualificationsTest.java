package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * TC-EMQ - the per-employee qualification sub-resources under /pim/employees/{empNumber}.
 *
 * Each of these hangs off a master-data record in Admin, and this instance ships with
 * those lists empty, so every lifecycle here creates its own reference record first and
 * removes it again. Nothing depends on data another class leaves behind.
 *
 * Three contract traps are pinned deliberately, because the payload the UI sends and the
 * payload the API accepts are not the same shape:
 *   - languages take fluencyId/competencyId, not fluency/competency
 *   - memberships reject every currencyId value, so the field must be omitted
 *   - salary amounts are written as salaryAmount and read back as amount
 */
public class EmployeeQualificationsTest extends BaseAPITest {

    private static Integer empNumber;
    private static Integer languageId;
    private static Integer licenseId;
    private static Integer membershipId;
    private static Integer payGradeId;
    private static Integer salaryComponentId;

    private static final int FLUENCY_WRITING = 1;
    private static final int COMPETENCY_POOR = 1;

    private static int employee() {
        if (empNumber == null) empNumber = require(firstEmpNumber(), "an employee in PIM");
        return empNumber;
    }

    /* ================================================================== */
    /* Languages                                                           */
    /* ================================================================== */

    @Test(priority = 1, description = "TC-EMQ-001 | A language is created in Admin as the fixture for the employee tests")
    public void tcEmq001_createAdminLanguage() {
        Response r = api().body(TestData.language(TestData.EMP_LANGUAGE))
                .when().post(Endpoints.LANGUAGES);

        assertOkObject(r, "POST /admin/languages");
        assertField(r, "data.name", TestData.EMP_LANGUAGE, "created language name");
        languageId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.LANGUAGES, languageId);
    }

    @Test(priority = 2, dependsOnMethods = "tcEmq001_createAdminLanguage",
          description = "TC-EMQ-002 | A language is attached to an employee with a fluency and a competency")
    public void tcEmq002_attachLanguageToEmployee() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.empLanguage(languageId, FLUENCY_WRITING, COMPETENCY_POOR))
                .when().post(Endpoints.EMP_LANGUAGES);

        assertOkObject(r, "POST /pim/employees/{empNumber}/languages");
        assertField(r, "data.language.id", languageId, "attached language id");
        assertField(r, "data.fluency.id", FLUENCY_WRITING, "attached fluency id");
        assertField(r, "data.competency.id", COMPETENCY_POOR, "attached competency id");
        assertField(r, "meta.empNumber", employee(), "meta echoes the employee");
    }

    @Test(priority = 3, dependsOnMethods = "tcEmq002_attachLanguageToEmployee",
          description = "TC-EMQ-003 | GET employee languages returns the attached language with its names resolved")
    public void tcEmq003_listEmployeeLanguages() {
        Response r = api().pathParam("empNumber", employee()).when().get(Endpoints.EMP_LANGUAGES);

        assertOkList(r, "GET /pim/employees/{empNumber}/languages");
        assertField(r, "meta.empNumber", employee(), "meta echoes the employee");
        List<Integer> ids = r.jsonPath().getList("data.language.id");
        Assert.assertTrue(ids.contains(languageId),
                "Attached language " + languageId + " missing from " + ids);
        assertEachHas(r, "data.fluency.name", "fluency names resolved");
        assertEachHas(r, "data.competency.name", "competency names resolved");
    }

    @Test(priority = 4, dependsOnMethods = "tcEmq001_createAdminLanguage",
          description = "TC-EMQ-004 | Sending fluency/competency instead of fluencyId/competencyId returns 422")
    public void tcEmq004_legacyFluencyFieldNamesRejected() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.empLanguageLegacyFields(languageId))
                .when().post(Endpoints.EMP_LANGUAGES);

        assertValidationError(r, "fluencyId", "POST employee language with fluency/competency");
    }

    @Test(priority = 5, dependsOnMethods = "tcEmq002_attachLanguageToEmployee",
          description = "TC-EMQ-005 | There is no PUT on the employee languages collection - it returns 405")
    public void tcEmq005_putOnLanguageCollectionNotAllowed() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.empLanguage(languageId, FLUENCY_WRITING, COMPETENCY_POOR))
                .when().put(Endpoints.EMP_LANGUAGES);

        assertBodylessError(r, StatusCodes.METHOD_NOT_ALLOWED,
                "PUT /pim/employees/{empNumber}/languages");
    }

    @Test(priority = 6, dependsOnMethods = "tcEmq001_createAdminLanguage",
          description = "TC-EMQ-006 | Attaching a language to a non-existent employee returns 422, not 404")
    public void tcEmq006_attachLanguageToMissingEmployee() {
        // The empNumber is a path segment, so a missing employee reads as a bad parameter
        // rather than a missing record - the opposite of the GET routes on the same resource.
        Response r = api().pathParam("empNumber", 99999999)
                .body(TestData.empLanguage(languageId, FLUENCY_WRITING, COMPETENCY_POOR))
                .when().post(Endpoints.EMP_LANGUAGES);

        assertValidationError(r, "empNumber", "POST language for employee 99999999");
    }

    @Test(priority = 7, dependsOnMethods = "tcEmq003_listEmployeeLanguages",
          description = "TC-EMQ-007 | An employee language is deleted by its composite languageId+fluencyId key")
    public void tcEmq007_detachLanguage() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.deleteLanguageKey(languageId, FLUENCY_WRITING))
                .when().delete(Endpoints.EMP_LANGUAGES);

        assertStatus(r, StatusCodes.OK, "DELETE employee language");
        List<Map<String, Object>> removed = r.jsonPath().getList("data");
        Assert.assertEquals(removed.size(), 1, "Exactly one language should be detached");
        Assert.assertEquals(removed.get(0).get("languageId"), languageId,
                "DELETE should echo the composite key it removed");
    }

    @Test(priority = 8, dependsOnMethods = "tcEmq007_detachLanguage",
          description = "TC-EMQ-008 | The detached language is gone from the employee's list")
    public void tcEmq008_languageGoneAfterDetach() {
        Response r = api().pathParam("empNumber", employee()).when().get(Endpoints.EMP_LANGUAGES);

        assertOkList(r, "GET employee languages after detach");
        List<Integer> ids = r.jsonPath().getList("data.language.id");
        Assert.assertFalse(ids.contains(languageId),
                "Detached language " + languageId + " is still listed: " + ids);
    }

    /* ================================================================== */
    /* Licenses                                                            */
    /* ================================================================== */

    @Test(priority = 10, description = "TC-EMQ-010 | A license is created in Admin as the fixture for the employee tests")
    public void tcEmq010_createAdminLicense() {
        Response r = api().body(TestData.license(TestData.EMP_LICENSE))
                .when().post(Endpoints.LICENSES);

        assertOkObject(r, "POST /admin/licenses");
        licenseId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.LICENSES, licenseId);
    }

    @Test(priority = 11, dependsOnMethods = "tcEmq010_createAdminLicense",
          description = "TC-EMQ-011 | A license is attached to an employee with its number and dates")
    public void tcEmq011_attachLicense() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.empLicense(licenseId, "QA-AUT-1", "2026-01-01", "2027-01-01"))
                .when().post(Endpoints.EMP_LICENSES);

        assertOkObject(r, "POST /pim/employees/{empNumber}/licenses");
        assertField(r, "data.license.id", licenseId, "attached license id");
        assertField(r, "data.licenseNo", "QA-AUT-1", "license number");
        assertField(r, "data.issuedDate", "2026-01-01", "issued date");
        assertField(r, "data.expiryDate", "2027-01-01", "expiry date");
    }

    @Test(priority = 12, dependsOnMethods = "tcEmq011_attachLicense",
          description = "TC-EMQ-012 | GET employee licenses lists the attached license with its dates intact")
    public void tcEmq012_listEmployeeLicenses() {
        Response r = api().pathParam("empNumber", employee()).when().get(Endpoints.EMP_LICENSES);

        assertOkList(r, "GET /pim/employees/{empNumber}/licenses");
        assertHasTotal(r, "GET employee licenses");
        List<Integer> ids = r.jsonPath().getList("data.license.id");
        Assert.assertTrue(ids.contains(licenseId),
                "Attached license " + licenseId + " missing from " + ids);
    }

    @Test(priority = 13, dependsOnMethods = "tcEmq011_attachLicense",
          description = "TC-EMQ-013 | Re-POSTing the same license upserts the row rather than duplicating it")
    public void tcEmq013_repeatedLicensePostUpserts() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.empLicense(licenseId, "QA-AUT-2", "2026-02-02", "2027-02-02"))
                .when().post(Endpoints.EMP_LICENSES);

        assertOkObject(r, "POST the same employee license a second time");
        assertField(r, "data.licenseNo", "QA-AUT-2", "the second POST wins");

        Response list = api().pathParam("empNumber", employee()).when().get(Endpoints.EMP_LICENSES);
        List<Integer> ids = list.jsonPath().getList("data.license.id");
        Assert.assertEquals(java.util.Collections.frequency(ids, licenseId), 1,
                "The licence should appear once, not twice, after a repeated POST: " + ids);
        assertField(list, "data[" + ids.indexOf(licenseId) + "].licenseNo", "QA-AUT-2",
                "the stored licence number after the upsert");
    }

    @Test(priority = 14, dependsOnMethods = "tcEmq010_createAdminLicense",
          description = "TC-EMQ-014 | A license with a malformed issued date returns 422")
    public void tcEmq014_licenseMalformedDateRejected() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.empLicense(licenseId, "QA-AUT-3", "01-01-2026", "2027-01-01"))
                .when().post(Endpoints.EMP_LICENSES);

        assertValidationError(r, "issuedDate", "POST employee license with a dd-mm-yyyy date");
    }

    @Test(priority = 15, dependsOnMethods = "tcEmq013_repeatedLicensePostUpserts",
          description = "TC-EMQ-015 | An employee license is deleted by its licenseId")
    public void tcEmq015_detachLicense() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.deleteIds(licenseId))
                .when().delete(Endpoints.EMP_LICENSES);

        assertStatus(r, StatusCodes.OK, "DELETE employee license");
        Assert.assertTrue(r.jsonPath().getList("data").contains(licenseId),
                "DELETE should echo the removed licenseId");
    }

    @Test(priority = 16, dependsOnMethods = "tcEmq015_detachLicense",
          description = "TC-EMQ-016 | FINDING: deleting an already-detached license still returns 200 with the id echoed")
    public void tcEmq016_detachLicenseTwice() {
        // Every plural Admin collection answers "Records Not Found" here; this one reports
        // success for a row that is not there, so a caller cannot detect a no-op delete.
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.deleteIds(licenseId))
                .when().delete(Endpoints.EMP_LICENSES);

        assertStatus(r, StatusCodes.OK, "DELETE an already-detached employee license");
        Assert.assertTrue(r.jsonPath().getList("data").contains(licenseId),
                "The no-op delete still echoes the id it did not remove");
    }

    /* ================================================================== */
    /* Memberships                                                         */
    /* ================================================================== */

    @Test(priority = 20, description = "TC-EMQ-020 | A membership is created in Admin as the fixture for the employee tests")
    public void tcEmq020_createAdminMembership() {
        Response r = api().body(TestData.membership(TestData.EMP_MEMBERSHIP))
                .when().post(Endpoints.MEMBERSHIPS);

        assertOkObject(r, "POST /admin/memberships");
        membershipId = r.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.MEMBERSHIPS, membershipId);
    }

    @Test(priority = 21, dependsOnMethods = "tcEmq020_createAdminMembership",
          description = "TC-EMQ-021 | A membership is attached to an employee when currencyId is omitted")
    public void tcEmq021_attachMembership() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.empMembership(membershipId))
                .when().post(Endpoints.EMP_MEMBERSHIPS);

        assertOkObject(r, "POST /pim/employees/{empNumber}/memberships");
        assertField(r, "data.membership.id", membershipId, "attached membership id");
        assertField(r, "data.subscriptionPaidBy", "Individual", "who pays the subscription");
        assertFieldPresent(r, "data.id", "membership record id");
    }

    @Test(priority = 22, dependsOnMethods = "tcEmq020_createAdminMembership",
          description = "TC-EMQ-022 | FINDING: every currencyId value is rejected on an employee membership")
    public void tcEmq022_membershipCurrencyAlwaysRejected() {
        // "USD" is the id the pay-grade currency route accepts, so this is not a bad value -
        // the field cannot be set at all, which makes the subscription fee currency-less.
        for (String currency : new String[]{"USD", "usd"}) {
            Response r = api().pathParam("empNumber", employee())
                    .body(TestData.empMembershipWithCurrency(membershipId, currency))
                    .when().post(Endpoints.EMP_MEMBERSHIPS);

            assertValidationError(r, "currencyId",
                    "POST employee membership with currencyId=" + currency);
        }
    }

    @Test(priority = 23, dependsOnMethods = "tcEmq021_attachMembership",
          description = "TC-EMQ-023 | GET employee memberships returns the fee normalised to two decimals")
    public void tcEmq023_listEmployeeMemberships() {
        Response r = api().pathParam("empNumber", employee()).when().get(Endpoints.EMP_MEMBERSHIPS);

        assertOkList(r, "GET /pim/employees/{empNumber}/memberships");
        List<Integer> ids = r.jsonPath().getList("data.membership.id");
        Assert.assertTrue(ids.contains(membershipId),
                "Attached membership " + membershipId + " missing from " + ids);

        int index = ids.indexOf(membershipId);
        String fee = r.jsonPath().getString("data[" + index + "].subscriptionFee");
        Assert.assertEquals(fee, "10.00",
                "A fee submitted as \"10\" should read back normalised, but got " + fee);
    }

    @Test(priority = 24, dependsOnMethods = "tcEmq023_listEmployeeMemberships",
          description = "TC-EMQ-024 | An employee membership is deleted by its own record id, not the membership id")
    public void tcEmq024_detachMembership() {
        Response list = api().pathParam("empNumber", employee()).when().get(Endpoints.EMP_MEMBERSHIPS);
        List<Integer> ids = list.jsonPath().getList("data.membership.id");
        int recordId = list.jsonPath().getInt("data[" + ids.indexOf(membershipId) + "].id");

        Response r = api().pathParam("empNumber", employee())
                .body(TestData.deleteIds(recordId))
                .when().delete(Endpoints.EMP_MEMBERSHIPS);

        assertStatus(r, StatusCodes.OK, "DELETE employee membership by record id");
        Assert.assertTrue(r.jsonPath().getList("data").contains(recordId),
                "DELETE should echo the removed record id");
    }

    @Test(priority = 25, dependsOnMethods = "tcEmq024_detachMembership",
          description = "TC-EMQ-025 | KNOWN DEFECT: deleting an attached Admin membership orphans the employee row and 500s its list")
    public void tcEmq025_orphanedMembershipBreaksTheList() {
        // Deleting master data that is still referenced leaves a row pointing at nothing,
        // and the employee's membership list then fails to serialise. The endpoint stays
        // broken for that employee until the orphan is removed by id - which cannot be
        // discovered through the API, because listing is exactly what is broken.
        Integer orphanMembershipId = null;
        Integer recordId = null;
        try {
            Response created = api().body(TestData.membership(TestData.EMP_MEMBERSHIP + "_ORPHAN"))
                    .when().post(Endpoints.MEMBERSHIPS);
            assertOkObject(created, "POST the membership used for the orphan check");
            orphanMembershipId = created.jsonPath().getInt("data.id");

            Response attached = api().pathParam("empNumber", employee())
                    .body(TestData.empMembership(orphanMembershipId))
                    .when().post(Endpoints.EMP_MEMBERSHIPS);
            assertOkObject(attached, "attach the membership before orphaning it");
            recordId = attached.jsonPath().getInt("data.id");

            Response dropped = api().body(TestData.deleteIds(orphanMembershipId))
                    .when().delete(Endpoints.MEMBERSHIPS);
            assertStatus(dropped, StatusCodes.OK, "DELETE the still-attached Admin membership");
            orphanMembershipId = null;   // it is gone; nothing left to clean up centrally

            Response list = api().pathParam("empNumber", employee())
                    .when().get(Endpoints.EMP_MEMBERSHIPS);
            assertStatus(list, StatusCodes.INTERNAL_SERVER_ERROR,
                    "GET employee memberships with an orphaned row");
            assertMessage(list, Messages.UNEXPECTED_ERROR,
                    "GET employee memberships with an orphaned row");
        } finally {
            if (recordId != null) {
                api().pathParam("empNumber", employee())
                        .body(TestData.deleteIds(recordId))
                        .when().delete(Endpoints.EMP_MEMBERSHIPS);
            }
            if (orphanMembershipId != null) {
                api().body(TestData.deleteIds(orphanMembershipId))
                        .when().delete(Endpoints.MEMBERSHIPS);
            }
        }

        Response after = api().pathParam("empNumber", employee())
                .when().get(Endpoints.EMP_MEMBERSHIPS);
        assertOkList(after, "GET employee memberships once the orphan is removed");
    }

    /**
     * Safety net. The membership cases above deliberately walk past a state that breaks
     * GET /pim/employees/{empNumber}/memberships, and the suite runs daily - so if one of
     * them fails part-way the instance must not be left with a 500 on that employee.
     */
    @AfterClass(alwaysRun = true)
    public void repairOrphanedMemberships() {
        if (empNumber == null) return;
        Response list = api().pathParam("empNumber", empNumber).when().get(Endpoints.EMP_MEMBERSHIPS);
        if (list.statusCode() == StatusCodes.OK) return;

        System.out.println("  Repairing orphaned memberships on employee " + empNumber
                + " (list returned " + list.statusCode() + ")");
        Object[] ids = new Object[40];
        for (int i = 0; i < ids.length; i++) ids[i] = i + 1;
        api().pathParam("empNumber", empNumber).body(TestData.deleteIds(ids))
                .when().delete(Endpoints.EMP_MEMBERSHIPS);

        Response after = api().pathParam("empNumber", empNumber).when().get(Endpoints.EMP_MEMBERSHIPS);
        System.out.println("  Employee membership list now returns " + after.statusCode());
    }

    /* ================================================================== */
    /* Salary components                                                   */
    /* ================================================================== */

    @Test(priority = 30, description = "TC-EMQ-030 | A salary component is added to an employee")
    public void tcEmq030_createSalaryComponent() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.salaryComponent(TestData.SALARY_COMPONENT, "1000"))
                .when().post(Endpoints.EMP_SALARY_COMPONENTS);

        assertOkObject(r, "POST /pim/employees/{empNumber}/salary-components");
        assertField(r, "data.salaryName", TestData.SALARY_COMPONENT, "salary component name");
        assertField(r, "data.currencyType.id", "USD", "salary currency");
        salaryComponentId = r.jsonPath().getInt("data.id");
    }

    @Test(priority = 31, dependsOnMethods = "tcEmq030_createSalaryComponent",
          description = "TC-EMQ-031 | The create response returns the amount as the instance profile expects")
    public void tcEmq031_createResponseAmountIsEncrypted() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.salaryComponent(TestData.SALARY_COMPONENT + "_2", "2000"))
                .when().post(Endpoints.EMP_SALARY_COMPONENTS);

        assertOkObject(r, "POST a second salary component");
        try {
            String amount = r.jsonPath().getString("data.amount");
            if (InstanceProfile.SALARY_ENCRYPTED_ON_CREATE) {
                Assert.assertTrue(amount != null && amount.startsWith("GCMAES256"),
                        "Expected a GCMAES256-prefixed ciphertext in the create response, got " + amount);
            } else {
                Assert.assertEquals(amount, "2000",
                        "Expected the create response to echo the submitted amount");
            }
        } finally {
            api().pathParam("empNumber", employee())
                    .body(TestData.deleteIds(r.jsonPath().getInt("data.id")))
                    .when().delete(Endpoints.EMP_SALARY_COMPONENTS);
        }
    }

    @Test(priority = 32, dependsOnMethods = "tcEmq030_createSalaryComponent",
          description = "TC-EMQ-032 | GET salary components returns the amount in plain text")
    public void tcEmq032_readBackPlainAmount() {
        Response r = api().pathParam("empNumber", employee())
                .when().get(Endpoints.EMP_SALARY_COMPONENTS);

        assertOkList(r, "GET /pim/employees/{empNumber}/salary-components");
        List<Integer> ids = r.jsonPath().getList("data.id");
        int index = ids.indexOf(salaryComponentId);
        Assert.assertTrue(index >= 0, "Created salary component missing from " + ids);
        assertField(r, "data[" + index + "].amount", "1000",
                "the amount written as salaryAmount reads back as amount");
    }

    @Test(priority = 33, description = "TC-EMQ-033 | A salary outside its pay grade's range returns 400")
    public void tcEmq033_salaryOutsidePayGradeRange() {
        Response grade = api().body(TestData.payGrade(TestData.SALARY_PAY_GRADE))
                .when().post(Endpoints.PAY_GRADES);
        assertOkObject(grade, "POST a pay grade for the salary range test");
        payGradeId = grade.jsonPath().getInt("data.id");
        registerForCleanup(Endpoints.PAY_GRADES, payGradeId);

        Response currency = api().pathParam("id", payGradeId)
                .body(TestData.payGradeCurrency("USD", 100, 200))
                .when().post(Endpoints.PAY_GRADE_CURRENCIES);
        assertOkObject(currency, "POST a currency band on the pay grade");

        Response r = api().pathParam("empNumber", employee())
                .body(TestData.salaryComponentInGrade(
                        TestData.SALARY_COMPONENT + "_3", "5000", payGradeId))
                .when().post(Endpoints.EMP_SALARY_COMPONENTS);

        assertError(r, StatusCodes.BAD_REQUEST, Messages.SALARY_OUT_OF_RANGE,
                "POST a salary of 5000 into a 100-200 pay grade band");
    }

    @Test(priority = 34, dependsOnMethods = "tcEmq032_readBackPlainAmount",
          description = "TC-EMQ-034 | A salary component is deleted and leaves the list empty")
    public void tcEmq034_deleteSalaryComponent() {
        Response r = api().pathParam("empNumber", employee())
                .body(TestData.deleteIds(salaryComponentId))
                .when().delete(Endpoints.EMP_SALARY_COMPONENTS);

        assertStatus(r, StatusCodes.OK, "DELETE employee salary component");

        Response after = api().pathParam("empNumber", employee())
                .when().get(Endpoints.EMP_SALARY_COMPONENTS);
        List<Integer> ids = after.jsonPath().getList("data.id");
        Assert.assertFalse(ids.contains(salaryComponentId),
                "Deleted salary component " + salaryComponentId + " is still listed: " + ids);
    }

    /* ================================================================== */
    /* Custom fields                                                       */
    /* ================================================================== */

    @Test(priority = 40, description = "TC-EMQ-040 | GET employee custom fields returns the field definitions in meta")
    public void tcEmq040_employeeCustomFields() {
        Response r = api().pathParam("empNumber", employee())
                .when().get(Endpoints.EMP_CUSTOM_FIELDS);

        assertOkList(r, "GET /pim/employees/{empNumber}/custom-fields");
        assertField(r, "meta.empNumber", employee(), "meta echoes the employee");
        assertFieldPresent(r, "meta.fields", "custom field definitions in meta");
    }

    @Test(priority = 41, description = "TC-EMQ-041 | GET custom fields for a non-existent employee returns 422, not 404")
    public void tcEmq041_customFieldsMissingEmployee() {
        Response r = api().pathParam("empNumber", 99999999)
                .when().get(Endpoints.EMP_CUSTOM_FIELDS);

        assertValidationError(r, "empNumber", "GET custom fields for employee 99999999");
    }
}
