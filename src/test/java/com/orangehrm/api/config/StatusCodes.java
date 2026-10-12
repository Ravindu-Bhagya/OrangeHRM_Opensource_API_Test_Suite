package com.orangehrm.api.config;

/**
 * PAGE 4 - EXPECTED STATUS CODES
 *
 * The status codes OrangeHRM OS 5.9 actually returns, verified against the live
 * instance. Note the two that differ from common REST convention:
 *
 *   - a successful POST (create) returns 200, NOT 201
 *   - a successful DELETE returns 200 with the deleted ids echoed in "data"
 */
public final class StatusCodes {

    private StatusCodes() { }

    public static final int OK                  = 200;
    public static final int FOUND_REDIRECT      = 302;
    public static final int BAD_REQUEST         = 400;
    public static final int UNAUTHORIZED        = 401;
    public static final int FORBIDDEN           = 403;
    public static final int NOT_FOUND           = 404;
    public static final int METHOD_NOT_ALLOWED  = 405;
    public static final int UNPROCESSABLE       = 422;
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int NOT_IMPLEMENTED     = 501;
}
