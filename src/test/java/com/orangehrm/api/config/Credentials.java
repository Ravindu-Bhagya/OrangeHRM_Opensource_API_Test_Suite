package com.orangehrm.api.config;

/**
 * PAGE 1 - CREDENTIALS
 *
 * The only place login credentials and the target host are defined.
 * Every value can be overridden without touching code:
 *
 *   mvn test -Dohrm.baseUrl=https://host -Dohrm.username=admin -Dohrm.password=secret
 *   OHRM_BASE_URL=... OHRM_USERNAME=... OHRM_PASSWORD=... mvn test
 *
 * Keep real secrets out of version control - pass them in from the CI secret store.
 */
public final class Credentials {

    private Credentials() { }

    private static String cfg(String prop, String env, String def) {
        String v = System.getProperty(prop);
        if (v == null || v.isBlank()) v = System.getenv(env);
        return (v == null || v.isBlank()) ? def : v;
    }

    /* ----------------------------------------------------------------- */
    /* Target environment                                                 */
    /* ----------------------------------------------------------------- */

    public static final String BASE_HOST =
            cfg("ohrm.baseUrl", "OHRM_BASE_URL", "https://59ostfs-os-kord.orangehrm.com");
//previous instance = https://59ostfs-os-kord.orangehrm.com
//tested instance = https://sec59issue-os-kord.orangehrm.com
    /** Everything the OrangeHRM SPA calls lives under this prefix. */
    public static final String WEB_BASE = BASE_HOST + "/web/index.php";
    public static final String API_BASE = WEB_BASE + "/api/v2";

    /* ----------------------------------------------------------------- */
    /* Authentication endpoints                                           */
    /* ----------------------------------------------------------------- */

    public static final String LOGIN_PAGE = WEB_BASE + "/auth/login";
    public static final String VALIDATE   = WEB_BASE + "/auth/validate";
    public static final String LOGOUT     = WEB_BASE + "/auth/logout";

    /* ----------------------------------------------------------------- */
    /* Admin login                                                        */
    /* ----------------------------------------------------------------- */

    public static final String ADMIN_USERNAME =
            cfg("ohrm.username", "OHRM_USERNAME", "admin");

    public static final String ADMIN_PASSWORD =
            cfg("ohrm.password", "OHRM_PASSWORD", "admin@OHRM123");

    /* ----------------------------------------------------------------- */
    /* Deliberately invalid credentials, for the negative auth tests       */
    /* ----------------------------------------------------------------- */

    public static final String INVALID_USERNAME = "not_a_real_user_qa";
    public static final String INVALID_PASSWORD = "WrongPassword!123";

    /** Expected product identity of the system under test. */
    public static final String EXPECTED_PRODUCT = "OrangeHRM OS";
    public static final String EXPECTED_VERSION = "5.9";
}
