# Gradle continuous build

This story adds a general-purpose mechanism which is able to keep the output of some tasks up-to-date when source files change.
For example, a developer may run `gradle --continuous <tasks>`.

When run with continuous build, Gradle will execute a build and determine any files that are inputs to that build.
Gradle will then watch for changes to those input files, and re-execute the build when any file changes.

Input files are determined as:
- Files that are inputs to a task but not outputs of some other task
- Files that are inputs to the model

So:

- `gradle --continuous run` would build and run the Play application. When a change to the source files are detected, Gradle would rebuild and
  restart the application.
- `gradle --continuous test run` would build and run the tests and then the Play application. When a change to the source files is detected,
  Gradle would rerun the tests, rebuild and restart the Play application.
- `gradle --continuous test` would build and run the tests. When a source file changes, Gradle would rerun the tests.

Note that for this feature, the implementation will assume that any source file affects the output of every task listed on the command-line.
For example, running `gradle --continuous test run` would restart the application if a test source file changes.

## Features

- [x] [Basic usage](basic-usage)
- [ ] [Improved change detection](improved-change-detection)
- [ ] [Improved responsiveness](improved-responsiveness)

## Bugs

- When running with the daemon, use of EOT to cancel the build requires an extra 'enter' due to the line buffering performed by the daemon client
  - In practice, this isn't too big of an issue because most shells intepret ctrl-d as closure of stdin, which doesn't suffer this buffering problem.
  - Interactive cancellation on windows requires pressing enter (https://issues.gradle.org/browse/GRADLE-3311)
- Scala compiler crashing in continuous build mode
  - crash in java.util.zip.ZipFile.getEntry because Jar file handles are kept open. Modifying a m-mapped jar file crashes the java process.
  - URL caching should be disabled in the Scala compiler process when continuous build mode is enabled
    - `new URL("jar:file://valid_jar_url_syntax.jar!/").openConnection().setDefaultUseCaches(false)` can be used to disable URL caching in a Java process.

## Backlog & Open Issues

- Performance benchmarking
