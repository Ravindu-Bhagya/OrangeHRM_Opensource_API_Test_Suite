package com.orangehrm.api.config;

/**
 * PAGE 6 - EXPECTED RESPONSE MESSAGES
 *
 * The exact `error.message` strings OrangeHRM OS 5.9 returns, captured from the live API.
 * Tests assert against these rather than only on the status code, so a change in the error
 * vocabulary is caught even when the code stays the same.
 *
 * Note the singular/plural split on 404: fetching one missing record says "Record Not Found",
 * while a bulk delete that matches nothing says "Records Not Found".
 */
public final class Messages {

    private Messages() { }

    /** 401 - no session, an expired session, or a forged cookie. */
    public static final String SESSION_EXPIRED    = "Session expired";

    /** 403 - authenticated but not permitted (e.g. the Time module for an Admin account). */
    public static final String UNAUTHORIZED       = "Unauthorized";

    /** 404 - GET/PUT against a single id that does not exist. */
    public static final String RECORD_NOT_FOUND   = "Record Not Found";

    /** 404 - bulk DELETE whose id list matches nothing (note the plural). */
    public static final String RECORDS_NOT_FOUND  = "Records Not Found";

    /** 422 - any field-level validation failure; details sit in error.data.invalidParamKeys. */
    public static final String INVALID_PARAMETER  = "Invalid Parameter";

    /** 501 - route exists but is not implemented on this build. */
    public static final String NOT_IMPLEMENTED    = "Not Implemented";

    /** 400 - punching in while an open attendance record already exists. */
    public static final String ALREADY_PUNCHED_IN =
            "Cannot Proceed Punch In Employee Already Punched In";

    /**
     * 405 (unsupported method) and an unknown route both return an EMPTY text/html body -
     * there is no JSON envelope and therefore no message to assert. Use
     * BaseAPITest.assertBodylessError for those.
     */
    /** 400 from POST /pim/employees/{empNumber}/salary-components outside the pay-grade band. */
    public static final String SALARY_OUT_OF_RANGE = "Salary should be within min and max";

    /** 400 from POST /admin/pay-grades/{id}/currencies with an inverted band. */
    public static final String BAND_INVERTED = "Min salary should be less than max salary";

    /** 500 envelope - the only one the API produces. */
    public static final String UNEXPECTED_ERROR = "Unexpected Error Occurred";

    public static final String NO_JSON_BODY = "<empty text/html body - no JSON error envelope>";
}
