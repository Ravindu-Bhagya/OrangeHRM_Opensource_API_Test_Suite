package com.orangehrm.api.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * PAGE 2 - USER / EMPLOYEE DETAILS
 *
 * All person-shaped test data: the employees the suite creates, the system users it
 * provisions, and their personal / contact / job attributes.
 *
 * Values marked "run-scoped" are generated once per JVM so a whole run shares one
 * consistent identity, and repeat runs never collide on a unique constraint.
 */
public final class UserData {

    private UserData() { }

    private static final Random RANDOM = new Random();

    /* ----------------------------------------------------------------- */
    /* Name pools                                                         */
    /* ----------------------------------------------------------------- */

    private static final String[] FIRST_NAMES = {
            "Peter", "John", "Alice", "Emma", "Michael", "Olivia", "Liam", "Sophia"
    };
    private static final String[] LAST_NAMES = {
            "Collins", "Smith", "Brown", "Taylor", "Johnson", "Williams", "Davis", "Wilson"
    };

    public static String randomFirstName() { return FIRST_NAMES[RANDOM.nextInt(FIRST_NAMES.length)]; }
    public static String randomLastName()  { return LAST_NAMES[RANDOM.nextInt(LAST_NAMES.length)]; }

    /** Timestamp-based unique id (HHmmssSSS + noise). */
    public static String uniqueId() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS"))
                + RANDOM.nextInt(100);
    }

    /** Everything this suite creates carries this prefix so it is easy to find and purge. */
    public static final String TEST_PREFIX = "QA_AUT_";

    /* ----------------------------------------------------------------- */
    /* Run-scoped employee identity                                       */
    /* ----------------------------------------------------------------- */

    public static final String EMP_FIRST_NAME   = randomFirstName();
    /** Doubles as the marker the pre-run sweep uses to find leftover employees. */
    public static final String EMP_MIDDLE_NAME  = "QA_AUT";
    public static final String EMP_LAST_NAME    = randomLastName();
    /** The API caps employeeId at 10 characters; uniqueId() can produce 11. */
    public static final int EMPLOYEE_ID_MAX_LENGTH = 10;
    public static final String EMP_EMPLOYEE_ID  = shortId();

    /** A unique id trimmed to the employeeId length limit (duplicates are allowed by the API). */
    public static String shortId() {
        String id = uniqueId();
        return id.length() > EMPLOYEE_ID_MAX_LENGTH ? id.substring(0, EMPLOYEE_ID_MAX_LENGTH) : id;
    }

    public static final String EMP_FIRST_NAME_UPDATED = "Updated" + EMP_FIRST_NAME;

    /* Personal details */
    public static final String EMP_BIRTHDAY       = "1990-05-17";
    public static final int    EMP_GENDER_MALE    = 1;      // 1 = Male, 2 = Female
    public static final String EMP_MARITAL_STATUS = "Single";
    public static final String EMP_DRIVING_LICENSE = "DL" + EMP_EMPLOYEE_ID;
    public static final String EMP_LICENSE_EXPIRY  = "2030-12-31";
    public static final String EMP_OTHER_ID        = "OID" + EMP_EMPLOYEE_ID;

    /* Contact details - phone numbers must be plain digits/dashes, extensions are rejected */
    public static final String EMP_STREET1     = "100 Automation Avenue";
    public static final String EMP_STREET2     = "Suite 200";
    public static final String EMP_CITY        = "San Jose";
    public static final String EMP_PROVINCE    = "California";
    public static final String EMP_ZIP         = "95101";
    public static final String EMP_COUNTRY     = "US";
    public static final String EMP_HOME_PHONE  = "555-010-0100";
    public static final String EMP_MOBILE      = "555-010-0200";
    public static final String EMP_WORK_PHONE  = "555-010-0300";
    public static final String EMP_WORK_EMAIL  = "qa.work." + EMP_EMPLOYEE_ID + "@example.com";
    public static final String EMP_OTHER_EMAIL = "qa.other." + EMP_EMPLOYEE_ID + "@example.com";

    /* Emergency contact */
    public static final String CONTACT_NAME         = "Jordan " + randomLastName();
    public static final String CONTACT_RELATIONSHIP = "Sibling";
    public static final String CONTACT_HOME_PHONE   = "555-010-0400";
    public static final String CONTACT_MOBILE       = "555-010-0500";

    /* Dependent - relationshipType only accepts lowercase "child" or "other" */
    public static final String DEPENDENT_NAME         = "Casey " + randomLastName();
    public static final String DEPENDENT_RELATIONSHIP = "child";
    public static final String DEPENDENT_DOB          = "2015-03-08";

    /* Education / work experience / membership */
    public static final int    EDU_YEAR        = 2012;
    public static final String EDU_INSTITUTE   = "QA Institute of Technology";
    public static final String EDU_SPECIALIZATION = "Software Quality";
    public static final String EDU_SCORE       = "A";
    public static final String WORK_COMPANY    = "Previous Employer Ltd";
    public static final String WORK_JOB_TITLE  = "QA Engineer";
    public static final String WORK_FROM_DATE  = "2015-01-01";
    public static final String WORK_TO_DATE    = "2019-12-31";
    public static final String WORK_COMMENT    = "Created by automated API suite";

    /* Immigration */
    public static final String IMMIGRATION_NUMBER = "P" + EMP_EMPLOYEE_ID;
    public static final String IMMIGRATION_ISSUED = "2022-01-01";
    public static final String IMMIGRATION_EXPIRY = "2032-01-01";

    /* ----------------------------------------------------------------- */
    /* Run-scoped system user (Admin > User Management)                   */
    /* ----------------------------------------------------------------- */

    /** OrangeHRM user-role ids. */
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_ESS   = 2;

    public static final String NEW_USERNAME          = TEST_PREFIX + "user_" + uniqueId();
    public static final String NEW_USER_PASSWORD     = "QaAuto@2026!";
    public static final String NEW_USER_PASSWORD_UPD = "QaAuto@2026Upd!";
    /** Too short / no digit - used for the password-policy negative test. */
    public static final String WEAK_PASSWORD         = "abc";

    /* ----------------------------------------------------------------- */
    /* Candidate (Recruitment)                                            */
    /* ----------------------------------------------------------------- */

    public static final String CANDIDATE_FIRST_NAME = randomFirstName();
    public static final String CANDIDATE_LAST_NAME  = randomLastName();
    public static final String CANDIDATE_EMAIL_PREFIX = "qa.candidate.";
    public static final String CANDIDATE_EMAIL      = CANDIDATE_EMAIL_PREFIX + uniqueId() + "@example.com";
    public static final String CANDIDATE_PHONE      = "555-010-0600";
    public static final String CANDIDATE_KEYWORDS   = "api,automation,testing";

    /* ----------------------------------------------------------------- */
    /* Dates                                                              */
    /* ----------------------------------------------------------------- */

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String today()             { return LocalDate.now().format(D); }
    public static String daysFromNow(int n)  { return LocalDate.now().plusDays(n).format(D); }
    public static String yearStart()         { return LocalDate.now().withDayOfYear(1).format(D); }
    public static String yearEnd()           {
        LocalDate n = LocalDate.now();
        return n.withDayOfYear(n.lengthOfYear()).format(D);
    }
}
