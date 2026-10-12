# OrangeHRM OS - API Test Suite

Automated API regression suite for **OrangeHRM OS 5.9**, built with
**Java 25 + Maven + TestNG + REST Assured**, with Allure and PDF reporting.

Target instance (default): `https://59ostfs-os-kord.orangehrm.com`

Every test asserts **both the HTTP status code and the response body**.

---

## Quick start

```bash
mvn test                       # run the whole suite against the default host
```

Point it at another environment or account without touching code:

```bash
mvn test \
  -Dohrm.baseUrl=https://your-instance.orangehrm.com \
  -Dohrm.username=admin \
  -Dohrm.password='your-password'
```

Environment variables work too (`OHRM_BASE_URL`, `OHRM_USERNAME`, `OHRM_PASSWORD`) — use
these in CI so the password never lands in the build log or in version control.

Run a single module:

```bash
mvn test -Dtest=PimTest -DfailIfNoSpecifiedTests=false
```

---

## Project layout

Configuration is split out of the test classes, so credentials and data live in one place
each and the tests just call them.

```
src/test/java/com/orangehrm/api/
├── config/
│   ├── Credentials.java   # host, login endpoints, admin + invalid credentials
│   ├── UserData.java      # employee / system-user / candidate dummy data
│   ├── TestData.java      # master-data names and every JSON request payload
│   ├── Endpoints.java     # every API path used by the suite
│   └── StatusCodes.java   # the status codes this API actually returns
├── core/
│   └── BaseAPITest.java   # auth, request specs, assertions, cleanup, reporting
└── tests/
    ├── AuthTest.java              # TC-AUTH
    ├── AdminTest.java             # TC-ADM
    ├── PimTest.java               # TC-PIM
    ├── LeaveTest.java             # TC-LEV
    ├── TimeAttendanceTest.java    # TC-ATT / TC-TIM
    ├── RecruitmentTest.java       # TC-REC
    ├── PerformanceTest.java       # TC-PRF
    ├── ClaimTest.java             # TC-CLM
    ├── BuzzTest.java              # TC-BZZ
    ├── DashboardCoreTest.java     # TC-DSH
    └── NegativeSecurityTest.java  # TC-NEG
```

Every test case carries an id and a name, e.g.

```java
@Test(description = "TC-ADM-002 | POST creates a job title and echoes it back with an id")
public void tcAdm002_createJobTitle() { ... }
```

Those names appear verbatim in the console output, the PDF report and Allure.

---

## Built for a daily run

The suite is designed to be run on a schedule against a live instance:

- **No collisions.** Every record it creates is named `QA_AUT_<kind>_<timestamp>`, generated
  once per JVM, so consecutive runs never trip the API's uniqueness constraints.
- **No residue.** Each test deletes what it created. Anything missed is caught by a central
  cleanup registry that runs in `@AfterSuite`, plus per-class safety nets.
- **Safe after an aborted run.** A run killed mid-way (Ctrl+C, a wedged JVM) never reaches
  `@AfterSuite`. So before any test, `BaseAPITest` sweeps the instance and deletes every row this
  suite marks as its own: `QA_AUT_` names/titles/usernames, employees with middle name `QA_AUT`,
  candidates with a `qa.candidate.` email. Nothing unmarked is ever touched. Also: holidays pick
  a date that is still free, the attendance tests close a punch-in left open by an earlier run,
  and the delete tests skip (not fail) when their create step never ran. Skip the sweep with
  `-Dohrm.skipPurge=true`. **Don't run two suites against one instance at the same time** — each
  sweeps the other's data.
- **No stuck state.** The attendance tests punch in at the *server's* current time, then punch
  out via `PUT /attendance/records`, so the account is never left "Punched In" — which would
  block the next day's run. The record is also deleted where `canUserModifyAttendance` allows
  it; where it doesn't (DELETE is 403), one punched-out record per run remains.
- **Fixtures, not fixed ids.** Reference ids (job titles, employees, skills) are looked up at
  runtime. A test whose prerequisite is genuinely absent is *skipped*, not failed.

Schedule it with cron:

```bash
0 6 * * *  cd /home/administrator/orangehrm-api-test && \
           OHRM_PASSWORD="$OHRM_PASSWORD" mvn -B test > /var/log/ohrm-api-$(date +\%F).log 2>&1
```

---

## Reports

| Report | Location |
|---|---|
| PDF summary (every test, name, verdict, failure detail) | `OrangeHRM-API-TestReport.pdf` |
| Surefire | `target/surefire-reports/` |
| Allure results | `allure-results/` → `allure serve allure-results` |

`testFailureIgnore` is on, so the build still produces a report when tests fail. Flip it off
in `pom.xml` if you want CI to go red on failure.

---

## How authentication works

OrangeHRM OS exposes **no bearer-token API**. Its Vue SPA authenticates like a browser, and
so does this suite (`BaseAPITest.login()`):

1. `GET /web/index.php/auth/login` and scrape the CSRF token out of the
   `:token="&quot;...&quot;"` attribute on the `<auth-login>` element.
2. `POST /web/index.php/auth/validate` as form data with `_token`, `username`, `password`.
   Success is a **302 to `/dashboard/index`** — a 302 back to `/auth/login` means bad credentials.
3. Reuse the resulting session cookie on every `/api/v2/...` call.

This runs once per suite in `@BeforeSuite`.

---

## API behaviours this suite pins down

Verified against the live 5.9 instance — several differ from common REST convention:

| Behaviour | Detail |
|---|---|
| Create returns **200** | Not 201. |
| Delete returns **200** | Body echoes the deleted ids: `{"data":[4]}`. |
| Bulk delete needs a **JSON body** | `{"ids":[…]}`. A `?ids[]=` query string 404s misleadingly. |
| Validation errors are **422** | `error.data.invalidParamKeys` names the offending fields — always read it rather than guessing. |
| Contact details are **singular** | `/pim/employee/{id}/contact-details`, not `employees`. |
| Subunit create needs `parentId` | Omitting it returns **404**, not 422. |
| Holiday `length` | Only `0` (Full Day) and `4` (Half Day); `8` is rejected. |
| Buzz uses two ids | Create returns `data.post.id`; the feed lists **share** ids, and deletion goes through `/buzz/shares/{id}`. |
| `subunitId` | Lowercase "u". `subUnitId` is rejected. |

---

## Known product issues the suite documents

These are pinned to the **current** behaviour so a daily run stays green. Each will fail
loudly if the product changes — that failure is the signal to update the expectation.

1. **TC-ADM-011 — soft-deleted job titles are still readable by id.**
   After `DELETE /admin/job-titles`, the record disappears from the collection and a second
   delete correctly 404s, but `GET /admin/job-titles/{id}` still returns the full record.
   Employment statuses hard-delete and 404 correctly, so the two are inconsistent.

2. ~~**TC-ATT-014 — punch-out reports success but does not persist.**~~ **Withdrawn
   2026-09-24 — test bug, not a product defect.** The suite was punching out through
   `PUT /attendance/records/{id}`, which is the admin *edit* endpoint. The real punch-out is
   `PUT /attendance/records` (no id) with `date`/`time`/`timezoneOffset`/`timezoneName`/`note`,
   and it persists correctly. TC-ATT-014 now tests that.

3. **TC-PIM-025 — an employee education record cannot be deleted.**
   `GET .../educations` lists the row, but `DELETE` with `{"ids":[rowId]}` returns
   **404 "Records Not Found"**; the singular route is 405 and a query string also 404s.
   There is no working delete. The sibling `work-experiences` sub-resource deletes correctly
   with the identical call shape, so this is specific to educations. The rows only disappear
   when the parent employee is deleted.

4. **TC-NEG-041 — pagination has no server-side ceiling.**
   `GET /pim/employees?limit=999999` returns 200 with the entire table in one response.

5. **TC-NEG-010 — 404 vs 422 is inconsistent across modules.**
   An unknown PIM `empNumber` is reported as `422 Invalid Parameter`, while admin/users,
   leave-types and vacancies all return a plain `404`. Clients cannot handle "not found"
   uniformly.

6. **TC-TIM-021/022/023 — the Time module is 403 for an Admin account.**
   `admin/modules` reports `time: true` and the account holds the Admin role, yet
   `/time/projects`, `/time/customers` and `/time/timesheets` all return
   `403 Unauthorized`. Time coverage is limited to `/time/time-sheet-period` until the
   permission is granted.

---

## Extending the suite

To add a resource:

1. Add its paths to `Endpoints.java`.
2. Add its payload builder to `TestData.java` (and any person data to `UserData.java`).
3. Add a test class in `tests/`, extend `BaseAPITest`, give each case a `TC-XXX-NNN | name`.
4. Register the class in `testng.xml`.

Use `registerForCleanup(collection, id)` for anything you create — that is what keeps the
daily run from accumulating data.

When an endpoint's payload shape is unclear, the fastest way to find the real contract is to
read the app's own frontend bundle: fetch `/web/dist/js/app.js` and grep it for the API path
or a UI label. Every endpoint in this suite was harvested that way and then confirmed live.
