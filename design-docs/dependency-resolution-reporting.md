Improved dependency resolution reporting

# Use cases.

1. It is hard to figure out why Gradle picks up version X of certain dependency because the dependency report
    only includes the version information *after* conflicts have been resolved.
2. User diagnoses dependency resolve failures
    (with a possible implementation being that the reporting is robust in the face of failures)
3. User views actual class paths
    (e.g currently the reports only show module dependencies.
    It should show external dependencies, self-resolving dependencies, tweaks to sourceSets.main.compileClasspath, etc).
4. User knows why certain dependencies appear / not appear in the graph (e.g. consider exclusions)

# Open issues / further work

- Resolution failure message should include selection reason.
- The dependency insight report should render dynamic requested versions in a fixed order.
- The reports should distinguish between dependencies that cannot be found and dependencies for which there was a failure.
- Dependency report should include file dependencies.
- Dependency insight report should show requested+selected+reason for child nodes, not just the top-level nodes.
- Change the `dependencies` task instance to default to show the compile configuration only.
- Finalise the API of the `DependencyInsightReportTask` type.
- If there is a single configuration in the project, should it be used by default by the dependency insight report?
- The dependency insight report needs to work with the C++ plugins in the same way it works with the java plugin.
- Dependency reports should show excluded things.
