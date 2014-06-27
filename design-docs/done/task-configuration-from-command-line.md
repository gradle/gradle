
## Configure dependency and configuration for the dependency insight report (DONE)

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

## Make the dependencies' report 'configuration' configurable via cmd line (DONE)

### User visible changes

It is possible to show the dependencies for a single configuration.
This way there's way less noise in the report (many times, the user is only interested in compile dependencies).
Consider defaulting the dependencies report to 'compile' dependencies if java plugin applied.

### Test coverage

TBD

### Implementation approach

TBD

## Help task shows basic details about a task (DONE)

Add some command line interface for discovering details about a task (name, type, path, description)

### User visible changes

Running `gradle help --task test` shows a usage message for the `test` task.

If multiple tasks match, details of the matching tasks are shown

* all matched tasks have the same type
    * print one report of the task type and include all matching paths in the report

* matched tasks have different types
    * print one detail output for each different task type including all available paths

### Test coverage

* integration tests
    * `gradle help --task` on simple task
    * `gradle help --task` on task referenced by full path  (e.g. `:someProj:dependencies`)
    * `gradle help --task` on implicit task (e.g. `tasks`)
    * `gradle help --task` on task defined via placeholder
    * `gradle help --task` on non existing task displays reasonable error message, including candidate matches
    * `gradle help --task` on multiple matching tasks
    * `gradle help --task` using camel-case matching to select task

### Implementation approach

- Change the `help` task:
    - add `--task` commandline property
    - change displayHelp implementation to print task details when `--task` is set
    - lookup project tasks and implicit tasks using the task selector
    - throw decent error message when requested task cannot be found
    - task details (task name, task type, path)
    - the default message informs the user about using `gradle help --task n`

- Update the 'using Gradle from the command-line' user guide chapter to mention the help task.

## Help task shows command-line options for a task (DONE)

Commandline options of the task passed to help are listed including a description. The legal values for each property are not shown - this
is added in a later story.

### User visible changes

The usage message of running `gradle help --task <task>` lists commandline options of the selected tasks.

### Test coverage

* integration tests
    * `gradle help` on task with no commandline properties
    * `gradle help` on task with commandline properties
    * `gradle help` on implicit task no commandline properties
    * `gradle help` on implicit task with no commandline properties
    * `gradle help --tassk help` (should print hint to `gradle help --task help`)

### Implementation approach

- Change configuration error message in `CommandLineTaskConfigurer` to suggest that the user run `gradle help --task <broken-task>`.
- Update the 'using Gradle from the command-line' user guide chapter.

## Help task shows legal values for each command-line option

### User visible changes

The usage message of running `gradle help --task init` includes the available values for the task command line options (e.g --type)

### Test coverage

* integration tests
    * `gradle help` on task with enum property type mapped to commandline option
    * `gradle help` on task with boolean property type mapped to commandline option
    * `gradle help` on task with String property mapped to commandline option
    * `gradle help --task init` shows all available init types

### Implementation approach

- Introduce marker annotation `Option("optionName")` to mark a task property mapped to a commandline option.
- `@Option` with not provided "optionName" is mapped to option with same name as the annotated field
- `@Option("optionName")` annotated on Enums includes enum values as possible option values
- `@Option("optionName")` annotated on boolean includes true/false as possible option values
- `@Option("optionName")` annotated on a setter method evaluates the available options from the parameter type)
- Introduce marker annotation `OptionValues("optionName")` to to allow a dynamic value lookup in the task implementation itself.
- Adapt InitBuild task to use `@OptionValues` to map values for the `--type` command line option.
- Update the 'using Gradle from the command-line' user guide chapter.
