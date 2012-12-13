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

-consider a new listener when task is added to the graph. The configuration on demand feature uses it.