command line options & task configuration

# Use cases

1. User selects the dependency criteria to run dependency reports for.
2. User selects which tests to include/exclude for test execution.
3. User requests that tests be executed with debugging enabled.
4. User specifies which Gradle version to perform an upgrade comparison build against.
5. User specifies which Gradle version the wrapper should use.

## State of things

State of things now: There's an internal `@CommandLineOption(options='theOption', description='some description')` annotation that:

1. Makes `theOption` usable on command line via `--theOption`
2. Works only with Strings and booleans setters (e.g. `setTheOption()`), which are annotated with `@CommandLineOption`

# Stories

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

## Add task validator for task options

### User visible changes

When task options that have unsupported option values, will throw an Exception pointing to the wrong assigned option value and hints
what values are supported.

### Implementation approach

- Add a task validator that validates a string property has a legal value at execution time.

### Test cases

- A reasonable error message is provided when user specified an illegal value for an enum property from the command-line.
- A reasonable error message is provided when user specified an illegal value for an string property from the command-line.

## Support camel-case matching for task commandline property values

### Test coverage
- A reasonable error message is provided when a string property is configured with an illegal value in the build script.

### User visible changes

The user can run `gradle init --type java-lib` instead of `gradle init --type java-library`

### Test coverage

- Use camel-case matching for a commandline property that accepts an enum type
- Use camel-case matching for a commandline property that accepts an string type
- Error message for illegal enum value includes candidate matches
- Error message for illegal string value includes candidate matches

### Implementation approach

- Use NameMatcher in commandline configuration.

## Add command-line options to more tasks

### User visible changes

- Add `@OptionValues` annotations for the options on `DependencyInsightReportTask`
- Add `@OptionValues` annotations for the options on `DependencyReportTask`
- Add `@OptionValues` annotations for the options on `Help`
- Probably more - see use cases above.

## Include the command-line options in the generated DSL reference

The reference page for a task type should show which command-line options are available for the type.

## Add an API to allow command-line options for a task to be declared programmatically

TBD

## Support additional property types

- Collection of any supported scalar type
- Conversion to `File`
- Conversion to `Number` or subclass

# Open issues

- Figure out what to do if multiple tasks of different types are selected *and* there are clashing command line options.
For example, 'foo' option that requires a string value in one task type but is a boolean flag in some other task type.
This is not a blocker because we have very little command line options, yet.
- Decide on precedence order if task is configured from the command line and in the build script. Add coverage, etc.
- If a method marked with `@Option` accepts varargs or a Collection type as parameter, allow the command-line option to be specified multiple
  time on the command-line.
- Output of `gradle help --task x` provides link to task documentation.
- Remove the 'chrome' from the output of `gradle help` and other command-line tasks.
- Remove the `implicitTasks` container from Project.
