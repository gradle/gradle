# Dependent components report

This feature allows a developer to get insight into the reverse dependency graph of the components of a build.


## Stories

### (Story) Simplest possible project-wide dependent component report

A `dependentComponents` task registered in the help plugin allows to ask for a dependent components report.
This task outputs the report for all components of the current project.

For each reported components, the report shows a graph of the components that depend on it.

#### Test coverage

- [x] Outputs a reasonable report for a project with no components
- [x] Reports all components of the project, excluding test suites
- [x] Reports all dependents, excluding test suites
- [x] Duplicated dependencies are reported only once

#### Implementation notes

- Introduce a `DependentBinariesResolver` service that makes use of strategies contributed by ecosystem supports.
- Implement a dependents resolution strategy for the Native ecosystem.
- The report output should be built by reusing existing ascii graph rendering infrastructure.


### (Story) Simplest possible component-specific dependent component report

The `dependentComponents` task gets a `--component` option added to specify a component to report dependents on.
The option can be repeated to get a report for several components.

#### Test coverage

- [x] Can use `--component` to get a report for a single component
- [x] Can repeat `--component` to get a report for a list of components
- [x] Fails when component given to `--component` is not found
- [x] `gradle help --task dependentComponents` describes the `--component` task option


### (Story) Hide non-buildable components by default

By default, non-buildables components are not shown in the report.
A `--non-buildable` option on `dependentComponents` task allows to show them all, annotated, so one can distinguish them from buildable ones.
The `--all` option on `dependentComponents` task also allows to show them.

#### Test coverage

- [x] Non-buildable components are hidden from the report by default
- [x] When `--non-buildable` or `--all` task option is provided, non-buildable components are shown in the report
- [x] Reported non-buildable components are annotated to distinguish them from buildable components 
- [x] Make sure we handle `util (non-buildable) <- lib <- exe`, `util <- lib (non-buildable) <- exe` and `util <- lib <- exe (non-buildable)`
- [x] `gradle help --task dependentComponents` describes the `--all` task option 

#### Implementation notes

- Enrich dependents resolution result with the buildability of components


### (Story) Report dependent test suites, hidden by default

The report shows test suites components and include them in dependents graphs.
The test suites components are annotated so one can distinguish them from regular components.

By default, test suites components are not shown in the report.
A `--test-suites` option on `dependentComponents` task allows to show them, annotated, so one can distinguish them from other components.
The `--all` option on `dependentComponents` task also allows to show them.

#### Test coverage

- [x] Test suites components are hidden from the report by default
- [x] When `--test-suites` or `--all` task option is provided, test suites are shown in the report
- [x] Test suites are reported as dependent of their target components
- [x] Reported test suites are annotated to distinguish them from regular components
- [x] Make sure we handle util <- utilTest and util <- libTest when util <- lib <- exe

#### Implementation notes

- Enrich dependents resolution result with a way to discriminate test suites from regular components.


### (Story) Report dependents across projects dependencies

The report should include dependents across projects. 

#### Test coverage

-  [x] dependencies across projects are reported properly, advertising the project path

#### Implementation notes

- Enhance dependents resolution so it crosses projects boundaries


### (Story) Fail gracefully on circular components dependencies

When invoked on a build with circular dependencies, the `dependentComponents` task should fail gracefully and provide a meaningful error message that helps fix the cycle.

#### Test coverage

- [x] Meaningful error message on circular components dependencies
- [x] Handle direct and indirect circular dependencies
- [x] Handle circular dependencies across projects

#### Implementation notes

- Add a cycle barrier into dependents resolution
- Reuse existing graph cycle reporting infrastructure.


## Open issues

- Should we break the dependents graph at non-buildable nodes?
- Make sure we handle a custom source set in one component depending on a different source set in the same component
- Prebuilt libraries are not components, so you cannot generate a dependent component report for them.
- Should we render each type of linkage differently?
- Should we be able to filter dependentComponent report any more than just by component (e.g., by type, by linkage, by flavor, etc)?
- We only create test suites for StaticLibraryBinary, so the report looks a little funny (we don't show tests for SharedLibraryBinary).  We also currently rebuild all sources, so the library isn't really used.
- Could we update the existing `components` report to include dependency info (like we have for the JVM model)?
