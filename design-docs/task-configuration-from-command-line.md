command line options & task configuration

# Use cases

1. User selects the dependency criteria to run dependency reports for.
2. User selects which tests to include/exclude for test execution.
3. User requests that tests be executed with debugging enabled.
4. User specifies which Gradle version to perform an upgrade comparision build against.
5. User specifies which Gradle version the wrapper should use.

## State of things

State of things now: There's an internal `@CommandLineOption(options='theOption', description='some description')` annotation that:

1. Makes `theOption` usable on command line via `--theOption`
2. Works only with Strings and booleans setters (e.g. `setTheOption()`), which are annotated with `@CommandLineOption`

# Stories

## Configure dependency and configuration for the dependency insight report

There should be some simple way to run the report from the command line.
While doing that consider adding support for selecting configuration for the regular 'dependencies' report from command line.

### User visible changes

1. When the dependencyInsight task is issued from the command line, it is possible to configure extra command line parameters.
It's not 100% decided what will be the naming of the parameters, currently it would be:
 dependencyInsight --include org.foo:bar --configuration runtime
 When we decide on exact naming of the task and the parameters, this spec needs to be updated.
1. Each parameter must be applicable to at least one task specified at command line. If any of the parameters cannot be applied to any of the tasks
 then the command should fail fast. Examples:
    'dependencyInsight --include x --configuration y' - works fine
    'clean build dependencyInsight --include x --configuration y' - works fine, applies the configuration only to dependencyInsight task
    'clean build --include x --configuration' - breaks as the dependencyInsight is not included so the parameters should not be used
1. If multiple tasks match given parameters (for example, when name-matching execution schedules multiple dependency insight tasks)
 then *all* of the tasks should be configured with given parameters.
1. The command line parameter takes precedence over the build script configuration

### Test coverage

1. uses command line parameter to configure task
1. the command line parameter takes precedence over the build script configuration
1. configures multiple tasks if parameters match multiple tasks
1. deals with the scenario when value of the parameter matches some existing task name
1. multiple different tasks configured at single command line
1. works when there are other, not-configurable tasks scheduled
1. nice error messages on incorrect use are fine. More in a separate story.

## Improve error handling

### User visible changes

Nice messages when user incorrectly uses a command line option.

### Test coverage

1. all acceptance criteria
1. works if there are extra unrelated, non-configurable tasks
1. works in a multi-project build

### Implementation approach / acceptance criteria

1. None of the potential user mistakes listed below should lead to an internal error.
1. Current command line options are not handled consistently at the moment.
    For example, --offline works same as -offline, but --ofline (typo) does not work the same as -ofline
1. Fix issue where an unknown option leads to an internal error.
1. Nice error reporting for:
    1. misspelled option(s).
    1. options required but not provided.
    1. option needs a value (e.g. String) but none provided.
    1. options that must not have value (e.g. boolean options) but value was provided
    1. single '-' used instead of '--'
    1. option used but there are no tasks that accept this configuration option
    1. clashing options, e.g. no-value boolean option in one task is a string option in other task
1. When a value has not been specified for a required task property and that property has a `@CommandLineOption` annotation, then the validation error
   message should inform the user that they can use the specified option to provide a value.

## Make the dependencies' report 'configuration' configurable via cmd line

### User visible changes

It is possible to show the dependencies for a single configuration.
This way there's way less noise in the report (many times, the user is only interested in compile dependencies).
Consider defaulting the dependencies report to 'compile' dependencies if java plugin applied.

### Test coverage

TBD

### Implementation approach

TBD

## Make the feature public

### User visible changes

TBD

### Test coverage

TBD

### Implementation approach

1. Make sure we're happy with the notation. Current one is:
 `some_task some_configurable_task --some_option --some_option2 optional_option2_value some_other_task`
 I'm worried that without some kind of namespacing we'll run into problems in the long run.
2. Move the `@CommandLineOption` annotation to the public API and mark `@Incubating`.
3. Add documentation to 'implementing custom tasks` chapter.
4. Use `NotationParser` to convert from command-line option to method parameter type.
5. Support annotating a Groovy task field.
6. Support multiple values for `CommandLineOption.options()` or replace `options()` with singular `option()`.
7. Support zero args methods annotated with `@CommandLineOption`.
8. Add validation for methods with `@CommandLineOption`:
    * The method must take exactly zero or one parameters.
    * The parameter must be of type boolean or assignable from String or assignable from Boolean.
9. Add error reporting for:
    * Configuration method throws an exception.
    * Annotation is missing 'options' value.
    * Annotation is missing 'description' value.

## Allow command-line options to be discovered

Add some command line interface for discovering available command-line options.

### User visible changes

Running `gradle --help test` shows a usage message for the `test` task.

The resolution message (ie the `*Try: ....` console output) for a problem configuring tasks from the command-line options should suggest that the user
run `${app-name} --help <broken-task>` or `${app-name} --help`

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

## Add an API to allow command-line options for a task to be declared programmatically

TBD

# Open issues

1. Figure out what to do if multiple tasks of different types are selected *and* there are clashing command line options.
For example, 'foo' option that requires a string value in one task type but is a boolean flag in some other task type.
This is not a blocker because we have very little command line options, yet.
1. Decide on precedence order if task is configured from the command line and in the build script. Add coverage, etc.
1. If a method marked with `@CommandLineOption` accepts varargs or a Collection type as parameter, allow the command-line option to be specified multiple
   time on the command-line.
1. Add support for more types in the conversion from command-line option value to property value, in particular File.
