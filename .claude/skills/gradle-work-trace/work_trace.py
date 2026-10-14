#!/usr/bin/env python3
"""Query a Gradle work trace (plan.jsonl + work.jsonl).

See platforms/documentation/docs/src/docs/design/work-trace-specification.md for the format.
Uses only the Python standard library.
"""

import argparse
import json
import os
import sys
from collections import deque

DEFAULT_DIR = os.path.join("build", "reports", "work-trace")


def read_jsonl(path):
    records = []
    if not os.path.isfile(path):
        return records
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError:
                # A failed or killed build can leave a truncated last line.
                pass
    return records


class Trace:
    def __init__(self, trace_dir, root_dir):
        self.root_dir = os.path.abspath(root_dir)
        self.plans = [r for r in read_jsonl(os.path.join(trace_dir, "plan.jsonl")) if r.get("kind") == "plan"]
        self.tasks = {r["id"]: r for r in read_jsonl(os.path.join(trace_dir, "work.jsonl")) if r.get("kind") == "task"}
        self.plan_tasks = {}
        for plan in self.plans:
            for t in plan.get("tasks", []):
                self.plan_tasks[t["id"]] = t
        if not self.plans and not self.tasks:
            sys.exit(f"No work trace found in {trace_dir}. Run the build with -Dorg.gradle.internal.work.trace=")

    # -- locations -------------------------------------------------------

    def normalize(self, path):
        """Turn a user-supplied path into the trace's location form."""
        if os.path.isabs(path):
            path = os.path.normpath(path)
            if path == self.root_dir:
                return ""
            if path.startswith(self.root_dir + os.sep):
                return os.path.relpath(path, self.root_dir).replace(os.sep, "/")
            return path
        path = os.path.normpath(path).replace(os.sep, "/")
        return "" if path == "." else path

    @staticmethod
    def contains(location, path):
        """True if location is path or an ancestor of it."""
        if location == path:
            return True
        if location == "":
            return not os.path.isabs(path)
        return path.startswith(location.rstrip("/") + "/")

    @staticmethod
    def join(location, file):
        if file == "":
            return location
        return f"{location}/{file}" if location else file

    # -- task lookup -----------------------------------------------------

    def resolve_task(self, task):
        if not task.startswith(":"):
            task = ":" + task
        if task in self.tasks or task in self.plan_tasks:
            return task
        candidates = sorted(t for t in set(self.tasks) | set(self.plan_tasks) if t.endswith(task))
        if len(candidates) == 1:
            return candidates[0]
        if candidates:
            sys.exit(f"Ambiguous task {task}: {', '.join(candidates)}")
        sys.exit(f"Task {task} is not in the trace")

    def requested(self):
        result = {t for t, r in self.tasks.items() if r.get("scheduling", {}).get("requested")}
        for plan in self.plans:
            result.update(plan.get("requestedTaskPaths", []))
        return result

    def scheduling_edges(self):
        """Edges X -> (Y, reason) meaning 'X caused Y to be scheduled'."""
        edges = {}

        def add(src, dst, reason):
            edges.setdefault(src, {}).setdefault(dst, reason)

        for tid, rec in self.tasks.items():
            s = rec.get("scheduling", {})
            for dep in s.get("explicitDependsOn", []):
                add(tid, dep, "dependsOn")
            for entry in s.get("inputDependencies", []):
                for dep in entry.get("tasks", []):
                    add(tid, dep, f"input property '{entry.get('property')}'")
            for finalized in s.get("finalizerOf", []):
                add(finalized, tid, "finalizedBy")
        # The plan covers tasks that were scheduled but never ran.
        for tid, t in self.plan_tasks.items():
            if tid in self.tasks:
                continue
            for dep in t.get("dependencies", []):
                add(tid, dep, "dependency (plan only: dependsOn or input)")
            for fin in t.get("finalizedBy", []):
                add(tid, fin, "finalizedBy (plan only)")
        return edges

    def find_path(self, sources, target):
        edges = self.scheduling_edges()
        queue = deque(sorted(sources))
        parent = {s: None for s in queue}
        while queue:
            node = queue.popleft()
            if node == target:
                chain = []
                while parent[node] is not None:
                    prev, reason = parent[node]
                    chain.append((prev, reason, node))
                    node = prev
                return list(reversed(chain)), node
            for dst, reason in sorted(edges.get(node, {}).items()):
                if dst not in parent:
                    parent[dst] = (node, reason)
                    queue.append(dst)
        return None, None


# -- commands ----------------------------------------------------------------

def cmd_producer(trace, args):
    path = trace.normalize(args.path)
    exact, covering = [], []
    for tid, rec in sorted(trace.tasks.items()):
        for out in rec.get("outputs", []):
            loc = out.get("location", "")
            if not trace.contains(loc, path):
                continue
            # Files of this output that are the path itself or lie under it (when the path is a directory).
            matching = [f for f in out.get("files", []) if trace.contains(path, trace.join(loc, f))]
            if matching or path == loc:
                exact.append((tid, out, len(matching)))
            else:
                covering.append((tid, out))
    if exact:
        for tid, out, n in exact:
            print(f"{tid}  ({rec_impl(trace, tid)})  output '{out['property']}' {out['type']} "
                  f"at {out['location'] or '<root>'}  [{n} produced file(s) at or under the path]")
        if len({e[0] for e in exact}) > 1:
            print("WARNING: several tasks claim this path (overlapping outputs)")
    elif covering:
        print(f"No task lists {path} as a produced file, but these declared outputs cover it:")
        for tid, out in covering:
            print(f"  {tid}  output '{out['property']}' {out['type']} at {out['location'] or '<root>'}")
    else:
        print(f"No recorded task produces {path}.")
        print("It may be a source file, an artifact-transform output, or produced by a task that did not reach execution.")


def skipped_inputs(trace):
    return sorted(t for t, r in trace.tasks.items() if r.get("inputsCapturingSkipped"))


def input_dependencies_error(trace, tid):
    return trace.tasks.get(tid, {}).get("scheduling", {}).get("inputDependenciesError")


def rec_impl(trace, tid):
    return trace.tasks.get(tid, {}).get("implementation", "?")


def cmd_consumers(trace, args):
    path = trace.normalize(args.path)
    found = False
    for tid, rec in sorted(trace.tasks.items()):
        for inp in rec.get("inputs") or []:
            for root in inp.get("roots", []):
                if trace.contains(root, path) or trace.contains(path, root):
                    found = True
                    rel = "root is the path" if root == path else ("root contains the path" if trace.contains(root, path) else "root is under the path")
                    print(f"{tid}  input '{inp['property']}'  root {root or '<root>'}  ({rel})")
    if not found:
        print(f"No recorded task consumes {path}.")
    blind = skipped_inputs(trace)
    if blind:
        print(f"Note: {len(blind)} task(s) have no captured inputs and are invisible to this query: {', '.join(blind)}")


def cmd_why(trace, args):
    target = trace.resolve_task(args.task)
    requested = trace.requested()
    if target in requested:
        print(f"{target} was requested directly (command line or default tasks).")
        return
    chain, start = trace.find_path(requested, target)
    if chain is None:
        print(f"No scheduling chain from the requested tasks ({', '.join(sorted(requested))}) to {target}.")
        unresolved = sorted(t for t in trace.tasks if input_dependencies_error(trace, t))
        if unresolved:
            print(f"Input dependencies could not be resolved for: {', '.join(unresolved)}; the chain may go through them.")
        return
    print(f"{start}  [requested]")
    for prev, reason, node in chain:
        print(f"  -> {node}  via {reason} of {prev}")


def cmd_depends(trace, args):
    a, b = trace.resolve_task(args.a), trace.resolve_task(args.b)
    reasons = []
    rec_a, rec_b = trace.tasks.get(a), trace.tasks.get(b)
    if rec_a:
        s = rec_a.get("scheduling", {})
        if b in s.get("explicitDependsOn", []):
            reasons.append("explicit dependsOn (or a non-file task dependency)")
        for entry in s.get("inputDependencies", []):
            if b in entry.get("tasks", []):
                reasons.append(f"input property '{entry.get('property')}' consumes files built by {b}")
        if b in s.get("mustRunAfter", []):
            reasons.append("mustRunAfter (ordering only, does not schedule)")
        if b in s.get("shouldRunAfter", []):
            reasons.append("shouldRunAfter (ordering only, does not schedule)")
    elif a in trace.plan_tasks:
        t = trace.plan_tasks[a]
        if b in t.get("dependencies", []):
            reasons.append("dependency per plan.jsonl (dependsOn or input; A never ran so the kind is unknown)")
    if rec_b and a in rec_b.get("scheduling", {}).get("finalizerOf", []):
        reasons.append(f"{b} finalizes {a} (finalizedBy)")
    error = input_dependencies_error(trace, a)
    if error:
        print(f"Note: input dependencies of {a} could not be resolved, so input edges may be missing: {error}")
    if reasons:
        print(f"{a} -> {b}:")
        for r in reasons:
            print(f"  - {r}")
        return
    chain, _ = trace.find_path({a}, b)
    if chain:
        print(f"{a} does not depend on {b} directly; transitive chain:")
        for prev, reason, node in chain:
            print(f"  {prev} -> {node}  via {reason}")
    else:
        print(f"{a} does not depend on {b}.")


def cmd_task(trace, args):
    tid = trace.resolve_task(args.task)
    rec = trace.tasks.get(tid)
    if rec is None:
        print(f"{tid} was scheduled but has no task record (did not reach execution). Plan entry:")
        rec = trace.plan_tasks[tid]
    print(json.dumps(rec, indent=2))


def cmd_summary(trace, args):
    for plan in trace.plans:
        print(f"plan for build {plan.get('buildPath')}: requested {plan.get('requestedTaskPaths')}, "
              f"excluded {plan.get('excludedTaskPaths', [])}, {len(plan.get('tasks', []))} scheduled task(s)")
    print(f"work.jsonl: {len(trace.tasks)} task record(s)")
    not_run = sorted(set(trace.plan_tasks) - set(trace.tasks))
    if not_run:
        print(f"scheduled but no task record: {', '.join(not_run)}")
    blind = skipped_inputs(trace)
    if blind:
        print(f"inputs not captured: {', '.join(blind)}")
    for tid in sorted(trace.tasks):
        error = input_dependencies_error(trace, tid)
        if error:
            print(f"input dependencies of {tid} not resolved: {error}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dir", default=None, help=f"trace directory (default: <root>/{DEFAULT_DIR})")
    parser.add_argument("--root", default=".", help="root build directory, used to relativize absolute paths (default: cwd)")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("summary", help="overview of the trace").set_defaults(func=cmd_summary)
    p = sub.add_parser("producer", help="which task produces a path")
    p.add_argument("path")
    p.set_defaults(func=cmd_producer)
    p = sub.add_parser("consumers", help="which tasks consume a path")
    p.add_argument("path")
    p.set_defaults(func=cmd_consumers)
    p = sub.add_parser("why", help="why a task was scheduled")
    p.add_argument("task")
    p.set_defaults(func=cmd_why)
    p = sub.add_parser("depends", help="why task A depends on task B")
    p.add_argument("a")
    p.add_argument("b")
    p.set_defaults(func=cmd_depends)
    p = sub.add_parser("task", help="print the raw record of a task")
    p.add_argument("task")
    p.set_defaults(func=cmd_task)
    args = parser.parse_args()
    trace_dir = args.dir or os.path.join(args.root, DEFAULT_DIR)
    args.func(Trace(trace_dir, args.root), args)


if __name__ == "__main__":
    main()
