---
name: sanity-check
description: Run `./gradlew sanityCheck` from the repository root, inspect failures, apply the relevant fix instructions, and retry until the build passes or 5 attempts have been made. Use when the user asks to run the sanity check workflow or iteratively resolve failures reported by `sanityCheck`.
---

# Sanity Check

Run this workflow from the repository root.

## Goal

Get `./gradlew sanityCheck` to pass.

## Workflow

1. Set `maxAttempts=5`.
2. Set `attempt=1`.
3. Run `./gradlew sanityCheck` with a timeout that is long enough for the repository, or run it in the background and monitor the output until it completes. Note that it may take as long as 10m to finish.
4. Example:

```bash
./gradlew sanityCheck
```

5. If the command passes, stop and report success.
6. If the command fails, inspect the output.
7. Determine which remediation instructions apply to the failure type.
8. Apply the smallest fix that addresses the current failure.
9. Re-run `./gradlew sanityCheck` using the same long-timeout or background-monitoring approach.
10. Repeat until the build passes or `maxAttempts` attempts have been used.
11. If `maxAttempts` is reached without success, stop and summarize the remaining failure.

## Fixing Rules

- Before fixing code style issues, first check whether the failure includes compilation errors.
- When fixing compilation errors, only fix obvious problems such as a missing semicolon, a missing import, or similarly mechanical mistakes.
- If the compilation failure is not obvious or the correct fix is uncertain, stop and ask for human intervention.
- Prefer minimal formatting or structural edits.
- Do not revert unrelated user changes.

## Failure-Specific Remediation

For any supported failure type:

1. Identify the failing task or report from the build output.
2. Find the relevant console log, build log, or referenced report.
3. Before fixing code style issues, check whether the output shows a compilation error that should be addressed first.
4. If there is a compilation error, only apply an obvious fix. If the right fix is uncertain, stop and ask for human intervention.
5. Use the output to locate the specific file, rule, or error.
6. Make the smallest fix that resolves the reported problem.
7. If multiple issues of the same kind are already reported, fix all currently known ones before rerunning.

Use these triggers:

- Checkstyle: output contains `Checkstyle rule violations were found`.
- Javadoc: output contains `Process '.../javadoc' finished with non-zero exit value 1`.
- CodeNarc: a task named `codenarcXXX` fails and the log contains `Exceeded maximum number of priority X violations`.
- Detekt: a task named `detekt` fails.
- ProjectHealth: a task named `projectHealth` fails.

## Reporting Back

Include:

- Whether `./gradlew sanityCheck` passed
- How many attempts were used
- Which files were changed
- Any remaining failure if the workflow stopped without success

## Stop Conditions

Stop immediately when:

- `./gradlew sanityCheck` passes
- 5 attempts have been reached
