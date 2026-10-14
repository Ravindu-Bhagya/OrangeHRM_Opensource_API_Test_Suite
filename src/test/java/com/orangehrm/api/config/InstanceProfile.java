package com.orangehrm.api.config;

/**
 * PAGE 6 - INSTANCE PROFILE
 *
 * Behaviour that legitimately differs between OrangeHRM instances, so the tests that pin
 * it expect the right thing for whichever host Credentials.BASE_HOST points at.
 *
 * Defaults are derived from the host; any flag can be forced without touching code:
 *
 *   mvn test -Dohrm.adminCanReadTime=false -Dohrm.salaryEncryptedOnCreate=true
 *   OHRM_ADMIN_CAN_READ_TIME=false OHRM_SALARY_ENCRYPTED_ON_CREATE=true mvn test
 *
 * Observed behaviour:
 *   59ostfs-os-kord    - Admin gets 200 on the /time data endpoints (was 403 until
 *                        Oct 2026); salary create response returns the amount as
 *                        GCMAES256 ciphertext.
 *   sec59issue-os-kord - Admin gets 200 on the /time data endpoints; salary create
 *                        response echoes the plain amount.
 */
public final class InstanceProfile {

    private InstanceProfile() { }

    private static boolean flag(String prop, String env, boolean def) {
        String v = System.getProperty(prop);
        if (v == null || v.isBlank()) v = System.getenv(env);
        return (v == null || v.isBlank()) ? def : Boolean.parseBoolean(v.trim());
    }

    /** The original instance the 403 / encryption pins were written against. */
    private static final boolean IS_59OSTFS = Credentials.BASE_HOST.contains("59ostfs-os-kord");

    /** Admin can read /time projects, customers, project admins and timesheets (200, not 403). */
    public static final boolean ADMIN_CAN_READ_TIME =
            flag("ohrm.adminCanReadTime", "OHRM_ADMIN_CAN_READ_TIME", true);

    /** POST salary-components returns data.amount encrypted rather than as submitted. */
    public static final boolean SALARY_ENCRYPTED_ON_CREATE =
            flag("ohrm.salaryEncryptedOnCreate", "OHRM_SALARY_ENCRYPTED_ON_CREATE", IS_59OSTFS);
}
