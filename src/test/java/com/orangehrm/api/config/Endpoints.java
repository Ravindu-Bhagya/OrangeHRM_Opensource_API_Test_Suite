package com.orangehrm.api.config;

/**
 * PAGE 3 - ENDPOINTS
 *
 * Every API path exercised by the suite, relative to {@link Credentials#API_BASE}.
 * Paths were harvested from the application's own frontend bundle
 * (/web/dist/js/app.js) and verified against the live instance.
 */
public final class Endpoints {

    private Endpoints() { }

    /* ---------------- Core / meta ---------------- */
    public static final String ABOUT                  = "/core/about";
    public static final String VALIDATION_UNIQUE      = "/core/validation/unique";
    public static final String OPENID_PROVIDERS       = "/auth/openid-providers";
    public static final String PASSWORD_VALIDATION    = "/auth/public/validation/password";

    /* ---------------- Admin: job ---------------- */
    public static final String JOB_TITLES             = "/admin/job-titles";
    public static final String JOB_TITLE_BY_ID        = "/admin/job-titles/{id}";
    public static final String JOB_CATEGORIES         = "/admin/job-categories";
    public static final String JOB_CATEGORY_BY_ID     = "/admin/job-categories/{id}";
    public static final String EMPLOYMENT_STATUSES    = "/admin/employment-statuses";
    public static final String EMPLOYMENT_STATUS_BY_ID= "/admin/employment-statuses/{id}";
    public static final String PAY_GRADES             = "/admin/pay-grades";
    public static final String PAY_GRADE_BY_ID        = "/admin/pay-grades/{id}";
    public static final String PAY_GRADE_CURRENCIES   = "/admin/pay-grades/{id}/currencies";
    public static final String WORK_SHIFTS            = "/admin/work-shifts";
    public static final String WORK_SHIFT_BY_ID       = "/admin/work-shifts/{id}";

    /* ---------------- Admin: organization ---------------- */
    public static final String ORGANIZATION           = "/admin/organization";
    public static final String LOCATIONS              = "/admin/locations";
    public static final String LOCATION_BY_ID         = "/admin/locations/{id}";
    public static final String SUBUNITS               = "/admin/subunits";
    public static final String SUBUNIT_BY_ID          = "/admin/subunits/{id}";

    /* ---------------- Admin: qualifications ---------------- */
    public static final String SKILLS                 = "/admin/skills";
    public static final String SKILL_BY_ID            = "/admin/skills/{id}";
    public static final String EDUCATIONS             = "/admin/educations";
    public static final String EDUCATION_BY_ID        = "/admin/educations/{id}";
    public static final String LICENSES               = "/admin/licenses";
    public static final String LICENSE_BY_ID          = "/admin/licenses/{id}";
    public static final String LANGUAGES              = "/admin/languages";
    public static final String LANGUAGE_BY_ID         = "/admin/languages/{id}";
    public static final String MEMBERSHIPS            = "/admin/memberships";
    public static final String MEMBERSHIP_BY_ID       = "/admin/memberships/{id}";
    public static final String NATIONALITIES          = "/admin/nationalities";

    /* ---------------- Admin: users & config ---------------- */
    public static final String USERS                  = "/admin/users";
    public static final String USER_BY_ID             = "/admin/users/{id}";
    public static final String MODULES                = "/admin/modules";
    public static final String LOCALIZATION           = "/admin/localization";
    public static final String THEME                  = "/admin/theme";
    public static final String EMAIL_CONFIGURATION    = "/admin/email-configuration";
    public static final String EMAIL_SUBSCRIPTIONS    = "/admin/email-subscriptions";
    public static final String LDAP_CONFIG            = "/admin/ldap-config";
    public static final String LDAP_USER_SYNC         = "/admin/ldap/user-sync";
    public static final String I18N_LANGUAGES         = "/admin/i18n/languages";
    public static final String I18N_GROUPS            = "/admin/i18n/groups";
    public static final String OAUTH_CLIENTS          = "/admin/oauth-clients";

    /* ---------------- PIM ---------------- */
    public static final String EMPLOYEES              = "/pim/employees";
    public static final String EMPLOYEE_BY_ID         = "/pim/employees/{empNumber}";
    public static final String EMPLOYEE_COUNT         = "/pim/employees/count";
    public static final String PERSONAL_DETAILS       = "/pim/employees/{empNumber}/personal-details";
    public static final String JOB_DETAILS            = "/pim/employees/{empNumber}/job-details";
    /** Note: singular "employee" - the plural form 404s. */
    public static final String CONTACT_DETAILS        = "/pim/employee/{empNumber}/contact-details";
    public static final String EMERGENCY_CONTACTS     = "/pim/employees/{empNumber}/emergency-contacts";
    public static final String DEPENDENTS             = "/pim/employees/{empNumber}/dependents";
    public static final String EMP_EDUCATIONS         = "/pim/employees/{empNumber}/educations";
    public static final String WORK_EXPERIENCES       = "/pim/employees/{empNumber}/work-experiences";
    public static final String EMP_SKILLS             = "/pim/employees/{empNumber}/skills";
    public static final String EMP_LANGUAGES          = "/pim/employees/{empNumber}/languages";
    public static final String EMP_LICENSES           = "/pim/employees/{empNumber}/licenses";
    public static final String EMP_MEMBERSHIPS        = "/pim/employees/{empNumber}/memberships";
    public static final String IMMIGRATIONS           = "/pim/employees/{empNumber}/immigrations";
    public static final String SUPERVISORS            = "/pim/employees/{empNumber}/supervisors";
    public static final String SUBORDINATES           = "/pim/employees/{empNumber}/subordinates";
    public static final String EMP_CUSTOM_FIELDS      = "/pim/employees/{empNumber}/custom-fields";
    public static final String TERMINATIONS           = "/pim/employees/{empNumber}/terminations";
    public static final String EMP_SALARY_COMPONENTS  = "/pim/employees/{empNumber}/salary-components";
    public static final String EMP_PHOTO              = "/pim/employees/{empNumber}/photo";
    public static final String CUSTOM_FIELDS          = "/pim/custom-fields";
    public static final String REPORTING_METHODS      = "/pim/reporting-methods";
    public static final String TERMINATION_REASONS    = "/pim/termination-reasons";
    public static final String OPTIONAL_FIELD         = "/pim/optional-field";
    public static final String PIM_REPORTS            = "/pim/reports/defined";

    /* ---------------- Leave ---------------- */
    public static final String LEAVE_TYPES            = "/leave/leave-types";
    public static final String LEAVE_TYPE_BY_ID       = "/leave/leave-types/{id}";
    public static final String LEAVE_TYPES_ELIGIBLE   = "/leave/leave-types/eligible";
    public static final String LEAVE_ENTITLEMENTS     = "/leave/leave-entitlements";
    public static final String LEAVE_ENTITLEMENT_BY_ID= "/leave/leave-entitlements/{id}";
    public static final String LEAVE_REQUESTS         = "/leave/leave-requests";
    public static final String EMP_LEAVE_REQUESTS     = "/leave/employees/leave-requests";
    public static final String EMP_LEAVE_ENTITLEMENTS = "/leave/employees/leave-entitlements";
    public static final String LEAVE_BALANCE          = "/leave/leave-balance/leave-type/{id}";
    public static final String HOLIDAYS               = "/leave/holidays";
    public static final String HOLIDAY_BY_ID          = "/leave/holidays/{id}";
    public static final String WORKWEEK               = "/leave/workweek";
    public static final String LEAVE_PERIOD           = "/leave/leave-period";
    public static final String LEAVE_PERIODS          = "/leave/leave-periods";
    public static final String OVERLAP_LEAVES         = "/leave/overlap-leaves";

    /* ---------------- Time & Attendance ---------------- */
    public static final String ATTENDANCE_RECORDS     = "/attendance/records";
    public static final String ATTENDANCE_RECORD_BY_ID= "/attendance/records/{id}";
    public static final String ATTENDANCE_LATEST      = "/attendance/records/latest";
    public static final String ATTENDANCE_CONFIGS     = "/attendance/configs";
    public static final String ATTENDANCE_DATETIME    = "/attendance/current-datetime";
    public static final String ATTENDANCE_TIMEZONES   = "/attendance/timezones";
    public static final String ATTENDANCE_SUMMARY     = "/attendance/employees/summary";
    public static final String EMP_ATTENDANCE_RECORDS = "/attendance/employees/{empNumber}/records";
    public static final String PROJECTS               = "/time/projects";
    public static final String CUSTOMERS              = "/time/customers";
    public static final String TIMESHEETS             = "/time/timesheets";
    public static final String TIMESHEET_PERIOD       = "/time/time-sheet-period";
    public static final String PROJECT_ADMINS         = "/time/project-admins";
    public static final String EMP_TIMESHEETS         = "/time/employees/{empNumber}/timesheets";

    /* ---------------- Recruitment ---------------- */
    public static final String VACANCIES              = "/recruitment/vacancies";
    public static final String VACANCY_BY_ID          = "/recruitment/vacancies/{id}";
    public static final String PUBLIC_VACANCIES       = "/recruitment/public/vacancies";
    public static final String CANDIDATES             = "/recruitment/candidates";
    public static final String CANDIDATE_BY_ID        = "/recruitment/candidates/{id}";
    public static final String CANDIDATE_HISTORY      = "/recruitment/candidates/{id}/history";
    public static final String CANDIDATE_SHORTLIST    = "/recruitment/candidates/{id}/shortlist";
    public static final String CANDIDATE_REJECT       = "/recruitment/candidates/{id}/reject";
    public static final String CANDIDATE_STATUSES     = "/recruitment/candidates/statuses";
    public static final String HIRING_MANAGERS        = "/recruitment/hiring-managers";
    public static final String INTERVIEWERS           = "/recruitment/interviewers";

    /* ---------------- Performance ---------------- */
    public static final String KPIS                   = "/performance/kpis";
    public static final String KPI_BY_ID              = "/performance/kpis/{id}";
    public static final String TRACKERS               = "/performance/trackers";
    public static final String TRACKER_BY_ID          = "/performance/trackers/{id}";
    public static final String TRACKER_LOGS           = "/performance/trackers/{id}/logs";
    public static final String MANAGE_REVIEWS         = "/performance/manage/reviews";
    public static final String MY_REVIEWS             = "/performance/reviews";
    public static final String EMP_REVIEWS            = "/performance/employees/reviews";
    public static final String EMP_TRACKERS           = "/performance/employees/trackers";
    public static final String PERF_SUPERVISORS       = "/performance/supervisors";
    public static final String CONFIG_TRACKERS        = "/performance/config/trackers";

    /* ---------------- Claim ---------------- */
    public static final String CLAIM_EVENTS           = "/claim/events";
    public static final String CLAIM_EVENT_BY_ID      = "/claim/events/{id}";
    public static final String EXPENSE_TYPES          = "/claim/expenses/types";
    public static final String EXPENSE_TYPE_BY_ID     = "/claim/expenses/types/{id}";
    public static final String CLAIM_REQUESTS         = "/claim/requests";
    public static final String EMP_CLAIM_REQUESTS     = "/claim/employees/requests";

    /* ---------------- Buzz ---------------- */
    public static final String BUZZ_FEED              = "/buzz/feed";
    public static final String BUZZ_POSTS             = "/buzz/posts";
    public static final String BUZZ_POST_BY_ID        = "/buzz/posts/{id}";
    public static final String BUZZ_SHARES            = "/buzz/shares";
    public static final String BUZZ_SHARE_BY_ID       = "/buzz/shares/{id}";
    public static final String BUZZ_SHARE_LIKES       = "/buzz/shares/{id}/likes";
    public static final String BUZZ_SHARE_COMMENTS    = "/buzz/shares/{id}/comments";
    public static final String BUZZ_ANNIVERSARIES     = "/buzz/anniversaries";

    /* ---------------- Dashboard / Directory ---------------- */
    public static final String DASH_ACTION_SUMMARY    = "/dashboard/employees/action-summary";
    public static final String DASH_LEAVES            = "/dashboard/employees/leaves";
    public static final String DASH_LOCATIONS         = "/dashboard/employees/locations";
    public static final String DASH_SUBUNIT           = "/dashboard/employees/subunit";
    public static final String DASH_TIME_AT_WORK      = "/dashboard/employees/time-at-work";
    public static final String DASH_SHORTCUTS         = "/dashboard/shortcuts";
    public static final String DASH_LEAVE_CONFIG      = "/dashboard/config/employee-on-leave-today";
    public static final String DIRECTORY_EMPLOYEES    = "/directory/employees";

    /* ---------------- Maintenance ---------------- */
    public static final String MAINTENANCE_CANDIDATES = "/maintenance/candidates";
    public static final String MAINTENANCE_PURGE      = "/maintenance/purge";
}
