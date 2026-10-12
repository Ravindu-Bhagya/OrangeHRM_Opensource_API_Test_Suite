package com.orangehrm.api.config;

/**
 * PAGE 5 - MASTER DATA & REQUEST PAYLOADS
 *
 * Non-person test data: the master-data records the suite creates (job titles,
 * locations, skills, vacancies ...) and the JSON request bodies for every write.
 *
 * Names are generated once per JVM with a QA_AUT_ prefix and a timestamp suffix, so
 * repeat runs never collide on the API's uniqueness constraints and leftover rows are
 * always identifiable.
 */
public final class TestData {

    private TestData() { }

    private static String name(String kind) {
        return UserData.TEST_PREFIX + kind + "_" + UserData.uniqueId();
    }

    /* ----------------------------------------------------------------- */
    /* Run-scoped master-data names                                       */
    /* ----------------------------------------------------------------- */

    public static final String JOB_TITLE          = name("JobTitle");
    public static final String JOB_TITLE_UPDATED  = JOB_TITLE + "_UPD";
    public static final String JOB_CATEGORY       = name("JobCat");
    public static final String EMPLOYMENT_STATUS  = name("EmpStatus");
    public static final String EMPLOYMENT_STATUS_UPDATED = EMPLOYMENT_STATUS + "_UPD";
    public static final String PAY_GRADE          = name("PayGrade");
    public static final String LOCATION           = name("Location");
    public static final String SUBUNIT            = name("Subunit");
    public static final String SKILL              = name("Skill");
    public static final String EDUCATION          = name("Education");
    public static final String LICENSE            = name("License");
    public static final String LANGUAGE           = name("Language");
    public static final String MEMBERSHIP         = name("Membership");
    public static final String WORK_SHIFT         = name("Shift");
    public static final String VACANCY            = name("Vacancy");
    public static final String VACANCY_UPDATED    = VACANCY + "_UPD";
    public static final String KPI                = name("KPI");
    public static final String CLAIM_EVENT        = name("ClaimEvent");
    public static final String EXPENSE_TYPE       = name("ExpenseType");
    public static final String CUSTOMER           = name("Customer");
    public static final String PROJECT            = name("Project");
    public static final String HOLIDAY            = name("Holiday");
    public static final String BUZZ_POST_TEXT     = "Automated API suite post " + UserData.uniqueId();
    public static final String BUZZ_COMMENT_TEXT  = "Automated API suite comment";

    public static final String EMP_LANGUAGE      = name("EmpLang");
    public static final String EMP_LICENSE       = name("EmpLic");
    public static final String EMP_MEMBERSHIP    = name("EmpMem");
    public static final String SALARY_COMPONENT  = name("Salary");
    public static final String SALARY_PAY_GRADE  = name("SalPayGrade");

    public static final String DESCRIPTION        = "Created by the automated API test suite";

    /** A name long enough to breach the API's max-length validation. */
    public static final String OVERSIZED_NAME     = "X".repeat(300);

    /* ----------------------------------------------------------------- */
    /* Admin payloads                                                     */
    /* ----------------------------------------------------------------- */

    public static String jobTitle(String title) {
        return """
               {"title":"%s","description":"%s","note":""}""".formatted(title, DESCRIPTION);
    }

    /** Missing the mandatory "title" - drives the 422 validation test. */
    public static String jobTitleMissingTitle() {
        return """
               {"description":"%s","note":""}""".formatted(DESCRIPTION);
    }

    public static String jobCategory(String n)      { return """
               {"name":"%s"}""".formatted(n); }

    public static String employmentStatus(String n) { return """
               {"name":"%s"}""".formatted(n); }

    public static String payGrade(String n)         { return """
               {"name":"%s"}""".formatted(n); }

    public static String skill(String n)            { return """
               {"name":"%s","description":"%s"}""".formatted(n, DESCRIPTION); }

    public static String education(String n)        { return """
               {"name":"%s"}""".formatted(n); }

    public static String license(String n)          { return """
               {"name":"%s"}""".formatted(n); }

    public static String language(String n)         { return """
               {"name":"%s"}""".formatted(n); }

    public static String membership(String n)       { return """
               {"name":"%s"}""".formatted(n); }

    public static String location(String n) {
        return """
               {"name":"%s","countryCode":"US","province":"California","city":"%s",
                "address":"%s","zipCode":"%s","phone":"%s","fax":"","note":"%s"}"""
                .formatted(n, UserData.EMP_CITY, UserData.EMP_STREET1,
                           UserData.EMP_ZIP, UserData.EMP_HOME_PHONE, DESCRIPTION);
    }

    /** parentId is mandatory - omitting it returns 404, not 422. */
    public static String subunit(String n, int parentId) {
        return """
               {"name":"%s","unitId":"","description":"%s","parentId":%d}"""
                .formatted(n, DESCRIPTION, parentId);
    }

    public static String user(String username, String password, int roleId, int empNumber) {
        return """
               {"username":"%s","password":"%s","userRoleId":%d,"empNumber":%d,"status":true}"""
                .formatted(username, password, roleId, empNumber);
    }

    /* ----------------------------------------------------------------- */
    /* PIM payloads                                                       */
    /* ----------------------------------------------------------------- */

    public static String employee(String first, String middle, String last, String empId) {
        return """
               {"firstName":"%s","middleName":"%s","lastName":"%s","employeeId":"%s"}"""
                .formatted(first, middle, last, empId);
    }

    /** An employeeId one character past the API's 10-character limit. */
    public static String employeeOversizedEmployeeId(String first, String last) {
        return """
               {"firstName":"%s","middleName":"","lastName":"%s","employeeId":"%s"}"""
                .formatted(first, last, "9".repeat(UserData.EMPLOYEE_ID_MAX_LENGTH + 1));
    }

    /** Missing the mandatory lastName. */
    public static String employeeMissingLastName(String first) {
        return """
               {"firstName":"%s","middleName":"","employeeId":"%s"}"""
                .formatted(first, UserData.uniqueId());
    }

    public static String personalDetails(String first, String last) {
        return """
               {"firstName":"%s","middleName":"%s","lastName":"%s","employeeId":"%s",
                "otherId":"%s","drivingLicenseNo":"%s","drivingLicenseExpiredDate":"%s",
                "gender":%d,"maritalStatus":"%s","birthday":"%s","nationalityId":null}"""
                .formatted(first, UserData.EMP_MIDDLE_NAME, last, UserData.EMP_EMPLOYEE_ID,
                           UserData.EMP_OTHER_ID, UserData.EMP_DRIVING_LICENSE,
                           UserData.EMP_LICENSE_EXPIRY, UserData.EMP_GENDER_MALE,
                           UserData.EMP_MARITAL_STATUS, UserData.EMP_BIRTHDAY);
    }

    public static String contactDetails() {
        return """
               {"street1":"%s","street2":"%s","city":"%s","province":"%s","zipCode":"%s",
                "countryCode":"%s","homeTelephone":"%s","mobile":"%s","workTelephone":"%s",
                "workEmail":"%s","otherEmail":"%s"}"""
                .formatted(UserData.EMP_STREET1, UserData.EMP_STREET2, UserData.EMP_CITY,
                           UserData.EMP_PROVINCE, UserData.EMP_ZIP, UserData.EMP_COUNTRY,
                           UserData.EMP_HOME_PHONE, UserData.EMP_MOBILE, UserData.EMP_WORK_PHONE,
                           UserData.EMP_WORK_EMAIL, UserData.EMP_OTHER_EMAIL);
    }

    /** jobDetails uses "subunitId" - lowercase u. "subUnitId" is rejected. */
    public static String jobDetails(Integer jobTitleId, Integer empStatusId, String joinedDate) {
        return """
               {"joinedDate":"%s","jobTitleId":%s,"empStatusId":%s,"jobCategoryId":null,
                "subunitId":null,"locationId":null}"""
                .formatted(joinedDate, jobTitleId, empStatusId);
    }

    public static String emergencyContact() {
        return """
               {"name":"%s","relationship":"%s","homePhone":"%s","mobilePhone":"%s","officePhone":""}"""
                .formatted(UserData.CONTACT_NAME, UserData.CONTACT_RELATIONSHIP,
                           UserData.CONTACT_HOME_PHONE, UserData.CONTACT_MOBILE);
    }

    public static String dependent() {
        return """
               {"name":"%s","relationshipType":"%s","dateOfBirth":"%s"}"""
                .formatted(UserData.DEPENDENT_NAME, UserData.DEPENDENT_RELATIONSHIP,
                           UserData.DEPENDENT_DOB);
    }

    /** "year" must be an int, a string is rejected. */
    public static String employeeEducation(int educationId) {
        return """
               {"educationId":%d,"institute":"%s","major":"%s","year":%d,
                "score":"%s","startDate":"2008-09-01","endDate":"2012-06-30"}"""
                .formatted(educationId, UserData.EDU_INSTITUTE, UserData.EDU_SPECIALIZATION,
                           UserData.EDU_YEAR, UserData.EDU_SCORE);
    }

    public static String workExperience() {
        return """
               {"company":"%s","jobTitle":"%s","fromDate":"%s","toDate":"%s","comment":"%s"}"""
                .formatted(UserData.WORK_COMPANY, UserData.WORK_JOB_TITLE,
                           UserData.WORK_FROM_DATE, UserData.WORK_TO_DATE, UserData.WORK_COMMENT);
    }

    /** Create accepts only skillId + comments; yearsOfExp is rejected. */
    public static String employeeSkill(int skillId) {
        return """
               {"skillId":%d,"comments":"%s"}""".formatted(skillId, DESCRIPTION);
    }

    public static String immigration() {
        return """
               {"number":"%s","issuedDate":"%s","expiryDate":"%s","status":1,
                "countryCode":"US","reviewDate":"%s","comment":"%s","type":1}"""
                .formatted(UserData.IMMIGRATION_NUMBER, UserData.IMMIGRATION_ISSUED,
                           UserData.IMMIGRATION_EXPIRY, UserData.IMMIGRATION_EXPIRY, DESCRIPTION);
    }

    /* ----------------------------------------------------------------- */
    /* Leave payloads                                                     */
    /* ----------------------------------------------------------------- */

    public static String leaveType(String n) { return """
               {"name":"%s","situational":false}""".formatted(n); }

    public static String leaveEntitlement(int empNumber, int leaveTypeId,
                                          String fromDate, String toDate, double days) {
        return """
               {"empNumber":%d,"leaveTypeId":%d,"fromDate":"%s","toDate":"%s","entitlement":%s}"""
                .formatted(empNumber, leaveTypeId, fromDate, toDate, days);
    }

    /** length accepts only 0 (Full Day) and 4 (Half Day); any other value is rejected. */
    public static final int HOLIDAY_FULL_DAY = 0;
    public static final int HOLIDAY_HALF_DAY = 4;

    public static String holiday(String n, String date, int length) {
        return """
               {"name":"%s","date":"%s","recurring":false,"length":%d}"""
                .formatted(n, date, length);
    }

    /* ----------------------------------------------------------------- */
    /* Recruitment payloads                                               */
    /* ----------------------------------------------------------------- */

    /** "employeeId" here is the hiring manager's empNumber, despite the name. */
    public static String vacancy(String n, int jobTitleId, int hiringManagerEmpNumber) {
        return """
               {"name":"%s","jobTitleId":%d,"employeeId":%d,"numOfPositions":2,
                "description":"%s","status":true,"isPublished":true}"""
                .formatted(n, jobTitleId, hiringManagerEmpNumber, DESCRIPTION);
    }

    public static String candidate(int vacancyId) {
        return """
               {"firstName":"%s","middleName":"","lastName":"%s","email":"%s",
                "contactNumber":"%s","vacancyId":%d,"keywords":"%s","comment":"%s",
                "consentToKeepData":false,"dateOfApplication":"%s"}"""
                .formatted(UserData.CANDIDATE_FIRST_NAME, UserData.CANDIDATE_LAST_NAME,
                           UserData.CANDIDATE_EMAIL, UserData.CANDIDATE_PHONE, vacancyId,
                           UserData.CANDIDATE_KEYWORDS, DESCRIPTION, UserData.today());
    }

    /** Malformed email - drives the 422 validation test. */
    public static String candidateBadEmail(int vacancyId) {
        return """
               {"firstName":"%s","lastName":"%s","email":"not-an-email","vacancyId":%d,
                "consentToKeepData":false,"dateOfApplication":"%s"}"""
                .formatted(UserData.CANDIDATE_FIRST_NAME, UserData.CANDIDATE_LAST_NAME,
                           vacancyId, UserData.today());
    }

    /* ----------------------------------------------------------------- */
    /* Performance / Claim / Buzz payloads                                */
    /* ----------------------------------------------------------------- */

    public static String kpi(String title, int jobTitleId) {
        return """
               {"title":"%s","jobTitleId":%d,"minRating":0,"maxRating":100}"""
                .formatted(title, jobTitleId);
    }

    public static String claimEvent(String n) {
        return """
               {"name":"%s","description":"%s","status":true}""".formatted(n, DESCRIPTION);
    }

    public static String expenseType(String n) {
        return """
               {"name":"%s","description":"%s","status":true}""".formatted(n, DESCRIPTION);
    }

    public static String buzzPost(String text) {
        return """
               {"type":"text","text":"%s"}""".formatted(text);
    }

    /* ----------------------------------------------------------------- */
    /* Bulk delete - every plural collection needs a JSON body {"ids":[]}  */
    /* ----------------------------------------------------------------- */

    /* ----------------------------------------------------------------- */
    /* Employee qualifications                                            */
    /* ----------------------------------------------------------------- */

    /** Employee language. The API names these fluencyId/competencyId, NOT fluency/competency. */
    public static String empLanguage(int languageId, int fluencyId, int competencyId) {
        return """
               {"languageId":%d,"fluencyId":%d,"competencyId":%d,"comment":"%s"}"""
               .formatted(languageId, fluencyId, competencyId, DESCRIPTION);
    }

    /** The field names the Vue client used before 5.x - still rejected with 422. */
    public static String empLanguageLegacyFields(int languageId) {
        return """
               {"languageId":%d,"fluency":1,"competency":1,"comment":""}""".formatted(languageId);
    }

    public static String empLicense(int licenseId, String licenseNo,
                                    String issued, String expiry) {
        return """
               {"licenseId":%d,"licenseNo":"%s","issuedDate":"%s","expiryDate":"%s"}"""
               .formatted(licenseId, licenseNo, issued, expiry);
    }

    /**
     * Employee membership. currencyId is deliberately absent: every value the API
     * accepts elsewhere ("USD", "usd", null, "") is rejected here with 422.
     */
    public static String empMembership(int membershipId) {
        return """
               {"membershipId":%d,"subscriptionPaidBy":"Individual","subscriptionFee":"10",\
"subscriptionCommenceDate":"2026-01-01","subscriptionRenewalDate":"2027-01-01"}"""
               .formatted(membershipId);
    }

    public static String empMembershipWithCurrency(int membershipId, String currencyId) {
        return """
               {"membershipId":%d,"subscriptionPaidBy":"Individual","subscriptionFee":"10",\
"currencyId":"%s","subscriptionCommenceDate":"2026-01-01","subscriptionRenewalDate":"2027-01-01"}"""
               .formatted(membershipId, currencyId);
    }

    /** Salary component. The amount field is salaryAmount on write; it reads back as amount. */
    public static String salaryComponent(String name, String amount) {
        return """
               {"salaryComponent":"%s","payFrequencyId":1,"currencyId":"USD",\
"salaryAmount":"%s","comment":"%s"}""".formatted(name, amount, DESCRIPTION);
    }

    public static String salaryComponentInGrade(String name, String amount, int payGradeId) {
        return """
               {"salaryComponent":"%s","payGradeId":%d,"payFrequencyId":1,"currencyId":"USD",\
"salaryAmount":"%s","comment":"%s"}""".formatted(name, payGradeId, amount, DESCRIPTION);
    }

    /** Body for the public password-strength validator. */
    public static String password(String candidate) {
        return """
               {"password":"%s"}""".formatted(candidate);
    }

    public static String payGradeCurrency(String currencyId, int min, int max) {
        return """
               {"currencyId":"%s","minSalary":%d,"maxSalary":%d}"""
               .formatted(currencyId, min, max);
    }

    /** Employee languages are keyed by a composite, so the delete body is a list of objects. */
    public static String deleteLanguageKey(int languageId, int fluencyId) {
        return """
               {"ids":[{"languageId":%d,"fluencyId":%d}]}""".formatted(languageId, fluencyId);
    }

    public static String deleteIds(Object... ids) {
        StringBuilder sb = new StringBuilder("{\"ids\":[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ids[i]);
        }
        return sb.append("]}").toString();
    }
}
