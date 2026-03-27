---
name: mark-flaky-test
description: Mark a Gradle test class or selected test cases as flaky by adding `@Flaky(because = "<issue link>")` and the required import. Use when the user provides a test class name and an issue link and wants help annotating the whole class or specific tests, optionally followed by a guarded branch-and-commit workflow.
---

# Mark Flaky Test

## When to use

Use this skill when the user wants to mark a test as flaky and provides:

- A test class name
- An issue link to place in `because`

## Inputs

Collect or confirm:

- `test class name`
- `issue link`

Do not proceed until both are known.

## Workflow

1. Find the class file for the provided class name.
2. Read the class file before making any change.
3. Identify all test cases in that file.
4. Ask the user: `do you want to mark the whole class name or only a few test cases?`
5. After the user confirms the scope, add `@Flaky(because = "<issue link>")` to the class or to the selected test methods.
6. Add `import org.gradle.test.fixtures.Flaky` if it is not already present.
7. Ask the user to review the diff, then ask: `do you want me to commit?`
8. Only if the user says yes, run the guarded branch-and-commit flow below.

## Finding the class file

- Search for the exact class name first.
- If multiple files match, show the matches and ask the user which file to update.
- Do not guess when more than one plausible class file exists.
- Once the file is selected, read the file in full or in the relevant sections before editing.

## Finding test cases

After reading the file, enumerate all test cases you found so the user can choose.

Common patterns in this repository:

- Groovy/Spock string-named tests such as `def "does something"()`
- JUnit-style methods annotated with `@Test`
- Kotlin or Java test methods annotated with `@Test`

When the user chooses method-level marking:

- Present the discovered test names in a simple numbered list.
- Ask which test cases to mark.
- Only annotate the methods explicitly chosen by the user.

## Annotation rules

Use the repository's existing annotation style where possible.

Default form:

```groovy
@Flaky(because = "https://example.com/issue")
```

Rules:

- If the whole class should be marked, place the annotation immediately above the class declaration.
- If only selected test cases should be marked, place the annotation immediately above each selected test method.
- Add `import org.gradle.test.fixtures.Flaky` with the other imports if it is missing.
- Do not duplicate an existing `@Flaky` import.
- Do not add a second `@Flaky` annotation to the same class or method. If one already exists, stop and ask the user how they want to handle it.
- Preserve surrounding formatting and local quote/import style when practical.

## Review prompt

After editing:

1. Summarize what was changed.
2. Ask the user to review.
3. Ask exactly: `do you want me to commit?`

Do not commit unless the user explicitly says yes.

## Guarded commit flow

Only run this section after explicit user approval to commit.

### Preconditions

- Check git status before creating a branch or commit.
- The working tree must be clean except for the change created by this workflow.
- If there are any unrelated modified, staged, or untracked files, abort and ask the user to clean the workspace first.

### Branch and commit steps

1. Fetch `origin/master` if needed.
2. Create a new branch from `origin/master`.
3. Stage only the file changed by this workflow.
4. Commit only that change.

Guidelines:

- Do not include unrelated files in the commit.
- Do not amend existing commits unless the user explicitly asks.
- Do not push unless the user explicitly asks.
- Do not add any `Co-authored-by: Claude` trailer or similar Claude co-author attribution.
- Use exactly this commit message format:

```text
Mark <TestClass> flaky

See <issue link>
```

## Abort conditions

Stop and ask the user what to do if any of these happen:

- Multiple candidate class files are found
- No matching class file is found
- The file already contains `@Flaky` on the selected class or method
- The requested test cases cannot be identified confidently
- The working tree contains unrelated changes when the user asks for a commit

## Response shape

Keep the interaction explicit and sequential:

1. Confirm the class file being edited.
2. List discovered test cases.
3. Ask whether to mark the whole class or selected test cases.
4. Apply the annotation and import.
5. Ask the user to review and ask whether to commit.
6. If approved and the workspace is clean, create the branch and commit.
