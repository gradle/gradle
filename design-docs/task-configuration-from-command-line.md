command line options & task configuration

# Use cases

1. User selects the set of dependencies to run dependency reports for.
2. User selects which tests to include/exclude for test execution.
3. User requests that tests be executed with debugging enabled.
4. User specifies which Gradle version to perform an upgrade comparision build against.
5. User specifies which Gradle version the wrapper should use.

# Implementation plan

## State of things

State of things now: There's an internal `@CommandLineOption(options='theOption', description='some description')` annotation that:

1. Makes `theOption` usable on command line via `--theOption`
2. Configures the boolean setter `setTheOption()` with `true` (this setter is annotated with `@CommandLineOption`)
3. Is pretty much limited to implict tasks only because it only configures if single task is selected.
This means that if multiple tasks with same name are selected for execution (name-matching execution) then this option will not configure any of the tasks.
It might have been implemented this way to avoid extensive reflecting on many types to find method annotated with `@CommandLineOption`.

## Support the new dependency report

Here's what is needed for the 'dependencyInsightReport' that renders the inverted dependency tree(s) for given dependency (more about that - see the the spec about improving dependency reporting).
This report needs some support on the command line.

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

1. Make the command line option can carry the `String` value. E.g. remove the current limitation of only `boolean` fields supported.
2. Work well if if name-matching selection returns multiple tasks from different projects. I assume we want to configure them all.
3. Make sure the performance does not regress when we start reflecting on types more.
4. Handle the case when the option is missing the value.
5. Use `NotationParser` to perform the conversion.

## Make the feature public

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

1. Move the `@CommandLineOption` annotation to the public API and mark `@Incubating`.
2. Add documentation to 'implementing custom tasks` chapter.
3. Support annotating a Groovy task field.

## Improve error handling

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

1. Fix issue where an unknown option leads to an internal error.
2. Nice error reporting for misspelled options.
3. Nice error reporting for options that are required but not provided.

## Allow command-line options to be discovered

Add some command line interface for discovering available command-line options. For example, perhaps `gradle help test` shows a usage message for the `test` task.

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

TBD

## Add command-line options to other tasks

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

1. Add option to `DependencyReportTask` to select the configuration(s) to be reported on.
2. Add option to `Test` task to select which tests to include, which tests to exclude, and whether to run with debugging enabled.
3. Probably more - see use cases above.

## Include the command-line options in the generated DSL reference

The reference page for a task type should show which command-line options are available for the type.

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

TBD

# Open issues

1. Figure out what to do if multiple tasks are selected.
2. Figure out what to do if multiple tasks of different types are selected *and* there are clashing command line options.
For example, 'foo' option that requires a string value in one task type but is a boolean flag in some other task type.
This is not a blocker because we have very little command line options, yet.
3. Decide on precedence order if task is configured from the command line and in the build script. Add coverage, etc.
4. Figure out how the options are documented, wheter it is a 'tasks' view, etc. Currently it will be user guide / dsl reference.
