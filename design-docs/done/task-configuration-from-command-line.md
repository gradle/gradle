
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
