# Tooling

## Git

- Never use `git -C <path>` to specify the working directory. Run git commands directly without `-C`.
- Prefer to use `git grep` instead of plain `grep` when searching the codebase.
- Write commit messages following these seven rules:
  - Separate subject from body with a blank line
  - Limit the subject line to 50 characters
  - Capitalize the subject line
  - Do not end the subject line with a period
  - Use the imperative mood — e.g. "Fix bug" not "Fixed bug"
  - Wrap the body at 72 characters
  - Use the body to explain what and why, not how

## IDE Integration

- When IntelliJ is connected, use `mcp__ide__getDiagnostics` to verify compilation after making changes, instead of running Gradle compile tasks. It is much faster.
- For code search (finding files, searching patterns), continue using Glob and Grep — the IDE integration does not replace these.
- When referring to code sources nested under the current working directory, always use relative file paths to refer to them, to make them clickable and easy to navigate to.
