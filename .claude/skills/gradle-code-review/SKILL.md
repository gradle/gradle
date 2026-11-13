---
name: gradle-code-review
description: The code review workflow for this Gradle repository. Use whenever the user asks to review code, perform/do a code review, review a change, diff, branch, or PR, or check changes before they reach the target branch. Prefer this over any generic code-review skill when working in this repo.
---

# Code Review

Apply the repository's review judgement — the source of truth for *what* to
look for and *what* to ignore is **`contributing/CodeReview.md`**.

All git access goes through the companion script **`gradle-code-review.sh`**, so only
that one script needs execute permission — never call `git` directly. It is
read-only and exposes:

- `.claude/skills/gradle-code-review/gradle-code-review.sh scope [TARGET_REF]` — summary of what is in scope
- `.claude/skills/gradle-code-review/gradle-code-review.sh diff  [TARGET_REF]` — full review diff (committed + working tree)
- `.claude/skills/gradle-code-review/gradle-code-review.sh log   PATH [TARGET_REF]` — commit history with patches for a path
- `.claude/skills/gradle-code-review/gradle-code-review.sh blame PATH` — `git blame` for a path

**Always invoke it by exactly that repo-relative path** — Bash runs from the
repo root, so it resolves — and do **not** rewrite it to an absolute path. The
project permission rule allows the relative form; an absolute path would not
match it and would trigger a prompt.

`TARGET_REF` is auto-detected when omitted — the base branch of an open PR for
the current branch (via `gh`), else the canonical remote's default branch. Pass
it explicitly only to override (e.g. `origin/release`); `scope` prints which
target it used and how it was chosen.

The actual analysis is delegated to a **sub-agent with a fresh context
window** so that reading the diff and the touched files does not fill up this
session's context. This session only resolves scope, launches the sub-agent,
and relays its findings.

## Step 1 — Resolve scope (this session)

Run `.claude/skills/gradle-code-review/gradle-code-review.sh scope <target>` to see the
target branch, fork point, the commits since it, the files changed, and whether
the working tree is dirty.

The principle: **everything that will reach the target branch needs review** —
the commits since the fork point plus any uncommitted/untracked changes. Some
commits may already have been pushed and reviewed, so the intended range can
be narrower.

**If scope is ambiguous — or it's unclear whether to include uncommitted work
— ask the user which range to review before continuing.** (This must happen
here: the sub-agent cannot ask questions.) End this step knowing the target
ref and whether uncommitted work is in scope.

## Step 2 — Delegate the analysis to a fresh-context sub-agent

Launch one sub-agent (Agent tool) to perform the review in its own context.
Give it a self-contained prompt containing:

- The scope from step 1: the target ref, and whether uncommitted/untracked
  changes are in scope.
- Instructions to gather the change via the companion script — run
  `.claude/skills/gradle-code-review/gradle-code-review.sh diff <target>` for the full
  diff, and `gradle-code-review.sh log <path> <target>` / `gradle-code-review.sh blame <path>`
  for historical context on subtle code. It must not call `git` directly.
- An instruction to **read `contributing/CodeReview.md` first** and apply its
  focus areas and exclusions, plus any relevant `CLAUDE.md` files and
  `contributing/` guides.
- The read-only constraint: only the companion script, Read, Glob, and Grep —
  do not build, type-check, or modify code (CI handles build signal
  separately).
- The output contract below — the sub-agent must return **only** the
  findings, not its intermediate reading or reasoning and not a summary of
  the change, so this session's context stays small.

Do not read the diff or the touched files in this session yourself — that is
the sub-agent's job, and doing it here defeats the purpose.

The Agent tool call returns the sub-agent's findings directly as its result;
use that return value — there is nothing to wait or poll for.

## Step 3 — Relay the findings (this session)

Present the sub-agent's findings to the user verbatim (lightly formatted).
Each finding:

- **`path:Lstart-Lend`** from the repo root
  (e.g. `platforms/jvm/scala/.../ScalaForkOptions.java:L40-L52`),
- a clear description — quoting the offending code or including relevant data
  flow paths, preconditions, unexpected sideeffects,
- a severity: `critical` / `major` / `minor` / `suggestion`.

Output findings only — no preamble, no summary of the change, no overall
verdict or wrap-up. If the sub-agent found no correctness issues, report a
single line that there is nothing to report and stop — do not invent findings
or pad the output.
