# Live view

Watch the suite while `mvn test` is running.

```bash
python3 live-dashboard/server.py          # http://localhost:4000
python3 live-dashboard/server.py --port 8080
```

Start it any time — before, during or after a run. It reads
`target/allure-results/*.json`, which allure-testng writes one file per test as
the run progresses, and polls every 2 seconds. It never touches the suite, the
results or the instance under test.

**Runs are separated by forked-JVM pid**, taken from allure's `thread` label —
not by a time gap. `target/allure-results` is not cleaned between runs and two
runs can overlap, so a gap heuristic mixes them. When more than one run is
present, the buttons at the top switch between them; the default is whichever
run moved most recently.

Expected test counts per module come from `testng.xml` plus the `@Test` count in
each test class, so the progress bars stay right when tests are added.

States: **Running** · **Stalled** (JVM alive but nothing written for 2 min) ·
**Wrapping up** (cleanup and PDF after the last test) · **Not running**.

## Running the suite from the dashboard

The **Run suite** button in the header launches `live-dashboard/run.sh` on the server
and the panels pick the new run up on the next poll. It greys out while a run is
moving, and a second click is refused rather than starting a competing run against
the same instance.

Two deliberate limits on `POST /api/run`:

- It runs that one script with no arguments from the request, so the button cannot be
  used to run anything else.
- It requires an `X-Dashboard-Run: 1` header. The server listens on loopback only, but
  a cross-origin form post from any local page could otherwise trigger a run; a custom
  header cannot be set that way.

"In progress" means a live JVM that is still writing results, not merely a live JVM —
a wedged Maven process from an earlier run would otherwise block the button forever.

## Stopping a run

**Stop** SIGTERMs every Maven, wrapper and forked-JVM process belonging to this project,
then SIGKILLs anything still alive after five seconds, and reports what it ended. It is
enabled whenever any suite process exists - including a wedged one, which is exactly what
someone looking at a **Stalled** run wants to end.

It asks for confirmation first, because **the suite's cleanup runs at the END of a run**:
killing it part-way leaves every record it has created so far on the instance, and the
next run will not remove them (the cleanup registry lives in the JVM that created them).
After a stop, look for leftovers by the `QA_AUT_` name prefix.

`POST /api/stop` carries the same `X-Dashboard-Run: 1` requirement as the run trigger,
and is checked before the in-progress guard - a run being in progress is exactly when
stopping it matters.

## Console panel

Two views, toggled in the panel:

- **Tests** (default) — one line per test as it completes: time, PASS/FAIL/SKIP,
  duration, TC id and scenario, plus the assertion message under a failure.
- **Maven** — the real `mvn` output, tailed from `target/live-run.log`.

Maven prints nothing between `Running TestSuite` and the final summary, so its
raw console sits motionless for the whole run. That is why the per-test stream is
the default; the Maven view is there for the build output around the tests.

The Maven view needs the suite started through the wrapper:

```bash
live-dashboard/run.sh                 # whole suite
live-dashboard/run.sh -Dtest=PimTest  # args pass through to mvn
```

It runs `mvn -B test` and tees the output to `target/live-run.log`. A plain
`mvn test` still works and is still tracked — only the Maven view is unavailable.

"Follow output" sticks to the newest line; scroll up and it stops chasing so you
can read.

## PDF report

The card at the bottom downloads `OrangeHRM-API-TestReport.pdf` (served at
`/report.pdf`, saved with the run's timestamp in the filename).

The suite overwrites that one path at the end of every run, so the file on disk
always belongs to the most recently *finished* run. The card says which run
wrote it and warns when that isn't the run you're looking at — during a run it
is still the previous run's report.
