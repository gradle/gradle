# Dependent components report

This feature allows a developer to get insight into the reverse dependency graph of the components of a build.

## Stories

`TBD`

## Out of scope

`TBD`

## Test Coverage

- Gracefully handle circular dependencies (we don't need to support them, we should just not fail any worse than `build` does now).
- Duplicate dependencies (do we render them twice? do we mark them as dupes, etc)
- Make sure we handle:
    - util (non-buildable) <- lib <- exe
    - util <- lib <- exe (non-buildable)
    - A custom source set in one component depending on a different source set in the same component
    - util <- utilTest and util <- libTest when util <- lib <- exe

## Open issues

- Prebuilt libraries are not components, so you cannot generate a dependent component report for them.
- Should we hide non-buildable binaries by default?
- Should we render each type of linkage differently?
- Should we render test suites differently from other types of dependencies?
- Should we be able to filter dependentComponent report any more than just by component (e.g., by type, by linkage, by flavor, etc)?
- We only create test suites for StaticLibraryBinary, so the report looks a little funny (we don't show tests for SharedLibraryBinary).  We also currently rebuild all sources, so the library isn't really used.
- Could we update the existing `components` report to include dependency info (like we have for the JVM model)?
