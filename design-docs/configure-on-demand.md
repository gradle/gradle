Gradle evaluates all projects even though it might not be necessary.
Imagine a 300-module build and someone just wants to run a single task from a single subproject.
It would be nice if Gradle only evaluated the projects that are necessary for the selected task to run.

# Use cases

Very large multi-project builds can have long evaluation time.
Even 100-200 millisecond overhead per project makes the total evaluation slow.
Basically we want to improve Gradle's scalability and performance for very large builds.

# Implementation plan

## "configure-on-demand" experimental mode for running Gradle builds

The user can run Gradle build in an experimental "configure-on-demand" mode.

### User visible changes

-subject to change as work on the spike continues
-when build is issued *not* all projects are evaluated. Evaluated are:
    -the root project because typically it contains some global configuration
    -all projects from the current directory *if* name-matching execution takes place. Example:
        -when running task ':aaa:foo', then only project ':aaa' is evaluated
        -when running task 'foo', then current project+children recursively are evaluated
        -consequently, when running task 'foo' from the root project, all projects are evaluated
    -as Gradle prepares the list of tasks to get executed, more projects are evaluated on demand. Example:
        -java projectA compile-depends on java projectB. Running projectA:build will cause projectB to evaluate and run projectB:build
        -task dependencies to other project tasks causes other project to evaluate
-the "configure-on-demand" mode is enabled by system property (e.g. gradle properties)
-not supported in this story:
    -evaluationDependsOn
    -decent error reporting when build contains unconventional project couplings
    -probably other things, as the work on spike continues

### Sad day cases

TBD

### Test coverage

-root is evaluated when building from subproject
-compile dependency between java projects A->B.
    -:a:build causes evaluation of a and b, build passes
    -:b:build does not evaluate a, build passes
-task dependency to other project
-name matching execution recurses from current dir
-TBD

### Implementation approach

- consider a new listener when task is added to the graph. The configuration on demand feature uses it.

## Project owner toggles daemon, parallel execution and configure-on-demand for a build in a consistent way

- can enable each of these in ~/.gradle/gradle.properties.
- can enable each of these in $projectDir/gradle.properties.
- can enable each of these using a system property.
- can enable each of these using a command-line option.

### User visible changes

- new 'org.gradle.parallel' gradle property
- new '--configure-on-demand' command line option

### Consider other changes for consistency:

- add 'daemonEnabled' property to StartParameter
- add '--no-configure-on-demand' command line option
- add '--no-parallel' command line option
- consider --parallel=false or --configure-on-demand=false to avoid proliferation of command line options (not sure if I like this idea)

### Consider refactorings:

- Make StartParameter class an interface

### Coverage:

- parallel build is used when org.gradle.parallel is 'true'
- configure-on-demand mode is on when org.gradle.configureondemand is 'true'

### Sad day cases:

1. Conflicting command line arg and gradle.properties:
    --no-daemon && org.gradle.daemon=true
    --parallel && org.gradle.parallel=false
    --daemon && org.gradle.daemon=false
    in general, command line should win

### Open questions:

1. Should StartParameter contain the 'updated' values? E.g. merged content of command line args and content of gradle.properties?

## Parallel execution mode implies configure on demand

- configure on demand with configuration decoupling is to eventually become the default Gradle model.
- rename 'configure on demand' to something that reflects that this will be the new Gradle configuration model.

## Allow project dependencies in build script classpath

This is a replacement for the `buildSrc` project. When in configure-on-demand mode, allow project dependencies
to appear in the classpath of a build script. The target project must be configured and the target configuration
built before the referring project can be configured.

One situation that will not be supported at this story is where the root project build script has a dependency
on a subproject *and* the root project injects some configuration into that project. This is not
possible with `buildSrc`, either. A later story will add support for decoupling the configuration injection from
the root project so that this will be possible.

### Coverage

- Root project has a dependency on a build project.
- A build project has a dependency on another project.

## Configure target project when project dependency is resolved

This is to fix common ordering issues where a dependency is resolved at configuration time. This should
happen for both the legacy and new configuration models.

## Coverage

- Project A depends on project B, which depends on some external dependency.
    - Verify that when the configuration is resolved in project A's build script, project B and the external
      dependency are present.

## Build author injects configuration into projects without direct coupling between projects

- Root project is no longer configured by default.

## Rename usages of `evaluate` to `configure`

## Warn build author when projects are coupled

- This will be split into a number of stories as there are a few ways that projects can be coupled. We might also interleave warning about a
  particular type of coupling with one of the following stories, which add alternative ways so solve the use cases without coupling.
- Possibly offer 'legacy', 'transitional' and 'strict' modes, where legacy ignores coupling, transitional warns and strict fails.
- Use transitional as the default when configure on demand is enabled, otherwise use legacy.

## Build author uses artifacts produced by another project without direct coupling between projects

## Build author uses model objects configured in another project without direct coupling between projects

## Transition to use configure on demand as the default

- Use strict mode as the default when configure-on-demand is used.
- Use transitional mode as the default when configure-on-demand is not used.

More to come ...
