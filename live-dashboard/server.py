#!/usr/bin/env python3
"""
Live view of the OrangeHRM API suite while `mvn test` is running.

Reads target/allure-results/*-result.json, which allure-testng writes one file
per test method as the run progresses, and serves a self-refreshing dashboard.

    python3 live-dashboard/server.py            # http://localhost:4000
    python3 live-dashboard/server.py --port 8080

Read-only: it never touches the suite, the results, or the product under test.
"""

import argparse
import glob
import json
import os
import re
import signal
import subprocess
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
RESULTS_DIR = os.path.join(PROJECT, "target", "allure-results")
TESTS_DIR = os.path.join(PROJECT, "src", "test", "java", "com", "orangehrm", "api", "tests")
TESTNG_XML = os.path.join(PROJECT, "testng.xml")
# BaseAPITest.generatePDFReport writes this at the end of every run, overwriting
# the previous one - so it always belongs to the most recently FINISHED run.
REPORT_PDF = os.path.join(PROJECT, "OrangeHRM-API-TestReport.pdf")
# Written only when the suite is launched through live-dashboard/run.sh, which
# tees Maven's console here. Plain `mvn test` leaves no log, so the console panel
# falls back to a stream rebuilt from the results.
LIVE_LOG = os.path.join(PROJECT, "target", "live-run.log")
RUN_SH = os.path.join(HERE, "run.sh")
# When the Run button launched a suite, Maven needs a few seconds before its JVM exists
# and longer before the first result lands - so remember the launch to bridge that gap.
_LAST_LAUNCH = 0.0
LAUNCH_GRACE_S = 120

# A run's tests land seconds apart; anything past this gap belongs to an older run.
RUN_GAP_MS = 300_000
# No new result file and no live JVM for this long => the run is over.
IDLE_DONE_S = 90
# Alive JVM that has written nothing for this long is wedged, not working.
STALL_S = 120


def expected_counts():
    """suite name from testng.xml -> how many @Test methods that class declares."""
    order, expected = [], {}
    try:
        xml = open(TESTNG_XML, encoding="utf-8").read()
    except OSError:
        return order, expected
    for block in re.finditer(
        r'<test\s+name="([^"]+)".*?</test>', xml, re.S
    ):
        suite = block.group(1)
        order.append(suite)
        total = 0
        for cls in re.findall(r'class\s+name="([\w.]+)"', block.group(0)):
            src = os.path.join(TESTS_DIR, cls.rsplit(".", 1)[-1] + ".java")
            try:
                total += len(re.findall(r"@Test\b", open(src, encoding="utf-8").read()))
            except OSError:
                pass
        expected[suite] = total
    return order, expected


SUITE_ORDER, SUITE_EXPECTED = expected_counts()
SUITE_INDEX = {name: i for i, name in enumerate(SUITE_ORDER)}


def label(result, name):
    for lab in result.get("labels", []):
        if lab.get("name") == name:
            return lab.get("value", "")
    return ""


def load_results():
    out = []
    for path in glob.glob(os.path.join(RESULTS_DIR, "*-result.json")):
        try:
            with open(path, encoding="utf-8") as fh:
                d = json.load(fh)
        except (OSError, ValueError):
            continue  # half-written file; it will be picked up next poll
        start = d.get("start")
        if not start:
            continue
        thread = label(d, "thread")
        status = d.get("status", "unknown")
        # allure calls an assertion failure "failed" and an exception "broken";
        # both are a red test to the person watching.
        if status == "broken":
            status = "failed"
        out.append(
            {
                "name": d.get("name") or d.get("fullName", ""),
                "method": label(d, "testMethod"),
                "suite": label(d, "suite") or "(unassigned)",
                "cls": label(d, "testClass").rsplit(".", 1)[-1],
                "status": status,
                "start": start,
                "stop": d.get("stop", start),
                "duration": max(0, d.get("stop", start) - start),
                "message": (d.get("statusDetails") or {}).get("message", "") or "",
                "jvm": thread.split("@")[0] if "@" in thread else "unknown",
            }
        )
    out.sort(key=lambda r: r["start"])
    return out


def group_runs(results):
    """One run == one forked surefire JVM.

    target/allure-results is not cleaned between runs, and two runs can be in
    flight at once, so grouping by a time gap mixes them up. The allure `thread`
    label carries the forked JVM's pid, which separates them exactly.
    """
    runs = {}
    for r in results:
        runs.setdefault(r["jvm"], []).append(r)
    out = []
    for jvm, rows in runs.items():
        out.append({
            "jvm": jvm,
            "rows": rows,
            "start": min(x["start"] for x in rows),
            "last": max(x["stop"] for x in rows),
            "alive": jvm in live_jvms(),
        })
    out.sort(key=lambda g: -g["last"])
    return out


_JVM_CACHE = {"at": 0, "pids": set()}


def live_jvms():
    """pids of surefire JVMs for this project that are alive right now."""
    if time.time() - _JVM_CACHE["at"] < 2:
        return _JVM_CACHE["pids"]
    pids = set()
    for pid in os.listdir("/proc"):
        if not pid.isdigit():
            continue
        try:
            with open(f"/proc/{pid}/cmdline", "rb") as fh:
                cmd = fh.read().replace(b"\0", b" ").decode("utf-8", "replace")
        except OSError:
            continue
        if "surefirebooter" in cmd and PROJECT in cmd:
            pids.add(pid)
    _JVM_CACHE.update(at=time.time(), pids=pids)
    return pids


def report_info(groups):
    """The PDF on disk, and which run wrote it.

    The suite overwrites one fixed path each run, so the file can belong to a
    different run than the one being viewed. Say which, rather than offering a
    download that quietly hands over someone else's results.
    """
    try:
        st = os.stat(REPORT_PDF)
    except OSError:
        return {"exists": False}
    mtime = st.st_mtime * 1000
    owner = next((g["jvm"] for g in groups if g["last"] <= mtime), None)
    return {
        "exists": True,
        "mtime": mtime,
        "size": st.st_size,
        "runJvm": owner,
        "name": os.path.basename(REPORT_PDF),
    }


def build_status(selected=None):
    groups = group_runs(load_results())
    if not groups:
        return {"state": "idle", "runs": [], "done": 0, "expectedTotal": sum(SUITE_EXPECTED.values()),
                "counts": {"passed": 0, "failed": 0, "skipped": 0}, "remaining": sum(SUITE_EXPECTED.values()),
                "elapsed": 0, "eta": None, "rate": 0, "passRate": 0, "lastCompleted": None,
                "suites": [dict(name=n, expected=SUITE_EXPECTED.get(n, 0), passed=0, failed=0,
                                skipped=0, duration=0, order=i) for i, n in enumerate(SUITE_ORDER)],
                "recent": [], "failures": [], "slowest": [], "generated": time.time() * 1000,
                "resultsDir": RESULTS_DIR, "report": report_info([])}

    # Default to a live run if there is one, else the most recent.
    group = next((g for g in groups if g["jvm"] == selected), None)
    if group is None:
        # An alive JVM that stopped writing is an orphan from an earlier run, not
        # the run worth watching — fall back to whichever run moved most recently.
        now = time.time() * 1000
        group = next((g for g in groups
                      if g["alive"] and now - g["last"] <= STALL_S * 1000), groups[0])
    runs = group["rows"]
    now_ms = time.time() * 1000

    counts = {"passed": 0, "failed": 0, "skipped": 0}
    suites = {}
    for i, name in enumerate(SUITE_ORDER):
        suites[name] = {"name": name, "expected": SUITE_EXPECTED.get(name, 0), "passed": 0,
                        "failed": 0, "skipped": 0, "duration": 0, "order": i}

    for r in runs:
        key = r["status"] if r["status"] in counts else "skipped"
        counts[key] += 1
        s = suites.setdefault(r["suite"], {"name": r["suite"], "expected": 0, "passed": 0,
                                           "failed": 0, "skipped": 0, "duration": 0, "order": len(suites)})
        s[key] += 1
        s["duration"] += r["duration"]

    done = sum(counts.values())
    expected_total = sum(SUITE_EXPECTED.values()) or done
    first_start, last_stop = group["start"], group["last"]
    idle_s = (now_ms - last_stop) / 1000

    if group["alive"]:
        # Alive but silent for a long stretch means wedged, not working.
        state = "stalled" if idle_s > STALL_S else "running"
    elif idle_s < IDLE_DONE_S:
        state = "finishing"
    else:
        state = "idle"

    end_ms = now_ms if state in ("running", "stalled") else last_stop
    elapsed_s = (end_ms - first_start) / 1000
    rate = done / elapsed_s if elapsed_s > 0 else 0
    remaining = max(0, expected_total - done)
    eta_s = remaining / rate if rate > 0 and state == "running" else None

    return {
        "state": state,
        "jvm": group["jvm"],
        "idle": idle_s,
        "runs": [{"jvm": g["jvm"], "alive": g["alive"], "count": len(g["rows"]),
                  "start": g["start"], "last": g["last"]} for g in groups[:6]],
        "generated": now_ms,
        "counts": counts,
        "done": done,
        "expectedTotal": expected_total,
        "remaining": remaining,
        "elapsed": elapsed_s,
        "eta": eta_s,
        "rate": rate * 60,
        "passRate": (counts["passed"] / done * 100) if done else 0,
        "lastCompleted": runs[-1]["name"] if runs else None,
        "suites": sorted(suites.values(), key=lambda s: s["order"]),
        "recent": list(reversed(runs[-30:])),
        "failures": [r for r in runs if r["status"] == "failed"],
        "slowest": sorted(runs, key=lambda r: -r["duration"])[:10],
        "resultsDir": RESULTS_DIR,
        "report": report_info(groups),
        "suiteProcesses": len(suite_processes()),
    }


ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


def tail_lines(path, limit):
    """Last `limit` lines without reading the whole file."""
    with open(path, "rb") as fh:
        fh.seek(0, os.SEEK_END)
        end = fh.tell()
        block, data = 64 * 1024, b""
        while end > 0 and data.count(b"\n") <= limit:
            step = min(block, end)
            end -= step
            fh.seek(end)
            data = fh.read(step) + data
    text = data.decode("utf-8", "replace")
    return [ANSI.sub("", ln) for ln in text.splitlines()[-limit:]]


def build_log(selected=None, view="tests", limit=200):
    """Two console views.

    Maven prints nothing between "Running TestSuite" and the final summary, so a
    raw tail sits motionless for the whole run. The per-test stream rebuilt from
    the results is what actually moves, so it is the default; the raw console
    stays one click away for the build output around it.
    """
    groups = group_runs(load_results())
    group = next((g for g in groups if g["jvm"] == selected), None)
    if group is None and groups:
        now = time.time() * 1000
        group = next((g for g in groups
                      if g["alive"] and now - g["last"] <= STALL_S * 1000), groups[0])

    # run.sh truncates the log at launch, so Maven writes into it a few seconds
    # BEFORE the first test result lands - and then falls silent for the rest of
    # the run, leaving mtime behind. So the log belongs to the newest run as long
    # as it was touched around that run's start, and never to an older one.
    console = None
    if os.path.exists(LIVE_LOG) and groups and group is not None:
        mtime = os.path.getmtime(LIVE_LOG) * 1000
        newest = groups[0]
        if group["jvm"] == newest["jvm"] and mtime >= newest["start"] - 120_000:
            console = LIVE_LOG

    if view == "maven":
        if console:
            try:
                return {"view": "maven", "hasConsole": True,
                        "lines": tail_lines(console, limit), "path": console}
            except OSError:
                pass
        return {"view": "maven", "hasConsole": False, "path": None, "lines": [
            "No Maven console for this run.",
            "",
            "Start the suite with live-dashboard/run.sh and its output is tee'd to",
            "target/live-run.log, which this panel then tails.",
        ]}

    lines = []
    for r in (group["rows"][-limit:] if group else []):
        stamp = time.strftime("%H:%M:%S", time.localtime(r["stop"] / 1000))
        mark = {"passed": "PASS", "failed": "FAIL", "skipped": "SKIP"}.get(r["status"], "????")
        lines.append(f"{stamp}  {mark}  {r['duration'] / 1000:5.1f}s  {r['name']}")
        if r["status"] == "failed" and r["message"]:
            lines.append(f"                      {r['message']}")
    return {"view": "tests", "hasConsole": console is not None, "lines": lines, "path": None}


def suite_processes():
    """Every process belonging to a suite run of this project: (pid, kind, cmd).

    Deliberately excludes this server, which also lives under the project directory.
    """
    found = []
    me = os.getpid()
    for pid in os.listdir("/proc"):
        if not pid.isdigit() or int(pid) == me:
            continue
        try:
            with open(f"/proc/{pid}/cmdline", "rb") as fh:
                cmd = fh.read().replace(b"\0", b" ").decode("utf-8", "replace")
        except OSError:
            continue
        if PROJECT not in cmd or "server.py" in cmd:
            continue
        if "surefirebooter" in cmd and "/bin/sh" not in cmd:
            found.append((int(pid), "test JVM", cmd))
        elif "plexus-classworlds" in cmd:
            found.append((int(pid), "maven", cmd))
        elif "run.sh" in cmd:
            found.append((int(pid), "wrapper", cmd))
        elif "surefirebooter" in cmd:
            found.append((int(pid), "fork shell", cmd))
    return found


def stop_suite():
    """SIGTERM every suite process, then SIGKILL whatever is left."""
    targets = suite_processes()
    if not targets:
        return {"stopped": False, "reason": "No suite process is running"}

    for pid, _, _ in targets:
        try:
            os.kill(pid, signal.SIGTERM)
        except OSError:
            pass

    deadline = time.time() + 5
    while time.time() < deadline:
        if not any(os.path.exists(f"/proc/{pid}") for pid, _, _ in targets):
            break
        time.sleep(0.25)

    killed = []
    for pid, kind, _ in targets:
        if os.path.exists(f"/proc/{pid}"):
            try:
                os.kill(pid, signal.SIGKILL)
                killed.append(f"{kind} {pid} (forced)")
            except OSError:
                pass
        else:
            killed.append(f"{kind} {pid}")

    global _LAST_LAUNCH
    _LAST_LAUNCH = 0.0
    return {"stopped": True, "processes": killed, "count": len(killed)}


def run_in_progress():
    """True only for a run that is actually moving.

    live_jvms() alone is the wrong test: a wedged JVM from an earlier run can stay
    resident for days, and that must not block the Run button forever.
    """
    # The grace only covers Maven starting up; a launch that already died (e.g. a
    # compile failure) must not block the next click for the rest of the window.
    if time.time() - _LAST_LAUNCH < LAUNCH_GRACE_S and suite_processes():
        return True
    now = time.time() * 1000
    alive = live_jvms()
    for g in group_runs(load_results()):
        if g["jvm"] in alive and now - g["last"] <= STALL_S * 1000:
            return True
    return False


class Handler(BaseHTTPRequestHandler):
    def _send(self, body, ctype):
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        route = self.path.split("?")[0]
        if route not in ("/api/run", "/api/stop"):
            self.send_error(404)
            return
        # The server is bound to loopback, but any page in a local browser could still
        # POST here; a custom header cannot be set by a cross-origin form, so requiring
        # one keeps the trigger to this dashboard's own fetch.
        if self.headers.get("X-Dashboard-Run") != "1":
            self._send(json.dumps({"started": False,
                                   "reason": "Missing X-Dashboard-Run header"}).encode(),
                       "application/json")
            return
        # Stop is checked before the in-progress guard: a run being in progress is
        # exactly when stopping it matters.
        if route == "/api/stop":
            self._send(json.dumps(stop_suite()).encode(), "application/json")
            return
        if run_in_progress():
            self._send(json.dumps({"started": False,
                                   "reason": "A run is already in progress"}).encode(),
                       "application/json")
            return
        if not os.access(RUN_SH, os.X_OK):
            self._send(json.dumps({"started": False,
                                   "reason": "live-dashboard/run.sh is missing or not executable"}).encode(),
                       "application/json")
            return
        # Fixed command, no arguments from the request - the button runs the suite, nothing else.
        global _LAST_LAUNCH
        _LAST_LAUNCH = time.time()
        subprocess.Popen([RUN_SH], cwd=PROJECT,
                         stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT,
                         start_new_session=True)
        self._send(json.dumps({"started": True}).encode(), "application/json")

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/api/status":
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            self._send(json.dumps(build_status(q.get("run", [None])[0])).encode(), "application/json")
        elif path == "/api/log":
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            self._send(json.dumps(build_log(q.get("run", [None])[0],
                                            q.get("view", ["tests"])[0])).encode(),
                       "application/json")
        elif path == "/report.pdf":
            try:
                st = os.stat(REPORT_PDF)
                with open(REPORT_PDF, "rb") as fh:
                    body = fh.read()
            except OSError:
                self.send_error(404, "No report has been generated yet")
                return
            stamp = time.strftime("%Y%m%d-%H%M", time.localtime(st.st_mtime))
            self.send_response(200)
            self.send_header("Content-Type", "application/pdf")
            self.send_header("Content-Length", str(len(body)))
            self.send_header(
                "Content-Disposition",
                f'attachment; filename="OrangeHRM-API-TestReport-{stamp}.pdf"')
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
        elif path in ("/", "/index.html"):
            with open(os.path.join(HERE, "dashboard.html"), "rb") as fh:
                self._send(fh.read(), "text/html; charset=utf-8")
        else:
            self.send_error(404)

    def log_message(self, *args):
        pass  # the poll every 2s would drown the console


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=4000)
    args = ap.parse_args()
    print(f"OrangeHRM API suite live view: http://localhost:{args.port}")
    print(f"watching {RESULTS_DIR}")
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
