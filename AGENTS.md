# Agent Instructions

Guidance for AI coding agents of any vendor working in the Gradle repository.
Human contributors should start from [CONTRIBUTING.md](CONTRIBUTING.md).

## Before you start

- Follow the contributor guidelines in [CONTRIBUTING.md](CONTRIBUTING.md),
  including its [Code change guidelines](CONTRIBUTING.md#code-change-guidelines)
  and the topical guides under [`contributing/`](contributing/).

## Reviewing code

When asked to review a change, diff, branch, or pull request, follow the
[Code Review guide](contributing/CodeReview.md). It defines *what* to look
for (correctness bugs, API-contract violations, edge cases, security) and
*what* to ignore (style, formatting, and naming).
It applies to human and AI reviewers alike.

Scope: review everything that will reach the target branch — the commits
since this branch's fork point, plus uncommitted and untracked changes. If
the intended range is ambiguous, ask which range to review.

Output: report findings only. Give each as `path:Lstart-Lend`, a short
description (quoting the offending code or violated rule where useful), and a
severity (`critical` / `major` / `minor` / `suggestion`). Do not add a summary
of the change or an overall verdict. If there are no correctness issues, say so
in one line and stop.

## Tooling notes

A `gradle-code-review` skill under [`.claude/skills/`](.claude/skills/) wraps
the guide above with the review mechanics (scope detection, output format). It
is written in Claude Code's skill format (a `SKILL.md` with frontmatter), but
its instructions are plain Markdown that any agent can read and follow.

- If your tool supports Claude Code skills, it is discovered automatically —
  invoke it with `/gradle-code-review`.
- Otherwise, read the skill's `SKILL.md` and
  [contributing/CodeReview.md](contributing/CodeReview.md) directly and follow
  them by hand.
