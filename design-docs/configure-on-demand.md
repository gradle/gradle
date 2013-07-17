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

- subject to change as work on the spike continues
- when build is issued *not* all projects are evaluated. Evaluated are:
    - the root project because typically it contains some global configuration
    - all projects from the current directory *if* name-matching execution takes place. Example:
        - when running task ':aaa:foo', then only project ':aaa' is evaluated
        - when running task 'foo', then current project+children recursively are evaluated
        - consequently, when running task 'foo' from the root project, all projects are evaluated
    - as Gradle prepares the list of tasks to get executed, more projects are evaluated on demand. Example:
        - java projectA compile-depends on java projectB. Running projectA:build will cause projectB to evaluate and run projectB:build
        - task dependencies to other project tasks causes other project to evaluate
- the "configure-on-demand" mode is enabled by system property (e.g. gradle properties)
- not supported in this story:
    - evaluationDependsOn
    - decent error reporting when build contains unconventional project couplings
    - probably other things, as the work on spike continues

### Sad day cases

TBD

### Test coverage

- root is evaluated when building from subproject
- compile dependency between java projects A->B.
    - `:a:build` causes evaluation of a and b, build passes
    - `:b:build` does not evaluate a, build passes
- task dependency to other project
- name matching execution recurses from current dir
- TBD

### Implementation approach

- consider a new listener when task is added to the graph. The configuration on demand feature uses it.

## Project owner toggles daemon, parallel execution and configure-on-demand for a build in a consistent way

- can enable each of these using a command-line option.
- can enable each of these using a system property (e.g. -Dxxx=foo)
- can enable via GRADLE_OPTS=-Dxxx=foo
- can enable each of these in $rootDir/gradle.properties
- can enable each of these in ~/.gradle/gradle.properties

### User visible changes

- new 'org.gradle.parallel' gradle property
- new '--configure-on-demand' command line option

### Consider other changes for consistency:

- add `--no-configure-on-demand` command line option
- add `--no-parallel` command line option
- consider `--parallel=false` or `--configure-on-demand=false` to avoid proliferation of command line options (not sure if I like this idea)

### Consider refactorings:

- Make StartParameter class an interface

### Coverage:

- parallel build is used when org.gradle.parallel is 'true'
- configure-on-demand mode is on when org.gradle.configureondemand is 'true'

### Sad day cases:

1. Conflicting command line arg and gradle.properties:
    - `--no-daemon` && `org.gradle.daemon=true`
    - `--parallel` && `org.gradle.parallel=false`
    - `--daemon` && `org.gradle.daemon=false`
    - `--parallel-threads=0` and `org.gradle.parallel=true`
    - in general, command line should win

### Open questions:

1. Should StartParameter contain the 'updated' values? E.g. merged content of command line args and content of gradle.properties?
 I think it should be a part of this story.
2. Should we add 'isDaemonEnabled' method to the StartParameter (for consistency)?
 I'd say yes but not just yet. Nobody asked about it.
3. Should we add both 'org.gradle.parallel' and 'org.gradle.parallel.threads' properties?
 I think I'll just add 'org.gradle.parallel' and let's see how it goes.

## Parallel execution mode implies configure on demand

- configure on demand with configuration decoupling is to eventually become the default Gradle model.
- rename 'configure on demand' to something that reflects that this will be the new Gradle configuration model.

## Configure target project when project dependency is resolved

This is to fix common ordering issues where a dependency is resolved at configuration time. This should
happen for both the legacy and new configuration models.

### Coverage

- Project A depends on project B, which depends on some external dependency.
    - Verify that when the configuration is resolved in project A's build script, project B and the external
      dependency are present.

## `buildSrc` project is correctly built in configure-on-demand mode

## Fix profile report configuration times in configure-on-demand mode

## Support tasks that are declared on a given project but work on multiple projects

For example, 'tasks' task provides task info for given project but also for all child projects, recursively.
In COD mode, running ':tasks' causes only root to be evaluated so the output of the task will not contain tasks of child projects.

This problem applies to any task that interrogates multiple projects (see task types that extend public AbstractReportTask type).

How do we want to tackle this problem?

1. fix the AbstractReportTasks (or any other built-in tasks that are coupled with external projects).
 Document that this approach is one of the couplings not supported by the COD mode.
2. make it possible to declare that task needs project(s), for example via some input annotation.
 This way, the new Gradle model (or COD mode) may know when to make the required projects evaluated.
3.  ?

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

## Build author injects configuration into projects without direct coupling between projects

- Root project is no longer configured by default.

## Build author does conditional configuration without relying on the contents of the task graph

- Introduce the concept of build types as a replacement for using the task graph.
- Deprecate mutating `StartParameters.taskNames`.

## Rename usages of `evaluate` to `configure`

## Warn build author when projects are coupled

- This will be split into a number of stories as there are a few ways that projects can be coupled. We might also interleave warning about a
  particular type of coupling with one of the following stories, which add alternative ways so solve the use cases without coupling.
- Possibly offer 'legacy', 'transitional' and 'strict' modes, where legacy ignores coupling, transitional warns and strict fails.
- Use transitional as the default when configure on demand is enabled, otherwise use legacy.

Need to detect:

- Tasks.getByPath()
- More TBD, see also [project-configuration-model.md](project-configuration-model.md)

## Build author uses artifacts produced by another project without direct coupling between projects

## Build author uses model objects configured in another project without direct coupling between projects

## Transition to use configure on demand as the default

- Use strict mode as the default when configure-on-demand is used.
- Use transitional mode as the default when configure-on-demand is not used.

## IDE plugins work with configure on demand

The IDE plugins currently use tightly coupled models and hooks such as `projectsEvaluated`. To work correctly, we'd need to do one of:

- Have some way for the IDE plugins to opt-in to tight coupling, but have this trigger configure-everything when the IDE model is requested
- Change the IDE plugins to use a decoupled model.
- Replace the existing IDE plugins with new decoupled plugins.

## Improve how tooling api deals with multi-project builds

There are several problems with how the tooling API deals with multi-project builds.

Implementation-wise, currently only running tasks takes advantage of COD. Requesting a model always causes everything to get configured.
There's a forum request from Attila (NetBeans) to improve that.

The tooling API project model also allows navigation from one project to all others in the hierarchy, which means that all projects must be configured.

## Configure on demand respects `--no-rebuild`

When running with `--no-rebuild`, the target project of any project dependency should not be configured, or we should remove `--no-rebuild`.

## Allow build and project and task lifecycle events to be received in decoupled mode

- Deprecate `BuildListener` and others.

## Make gradle CoD 'load' projects on demand

    * Currently, CoD makes projects configured on demand. However, all project instances are created beforehand.
    In gigantic projects this means that a lot of memory is used for projects that may not be used at all.
    * Profiling large builds is inconvenient due the extra instances hanging around

### User visible changes

Faster & less memory-hungry builds when CoD is used and the user builds only a subset of projects

### Coverage

performance tests, we probably need dedicated performance tests for CoD

### Implementation options

    * Project instances are slim and lazily initiated
    * If we start loading projects on demand, the 'allprojects' and 'subprojects' script blocks will stop working.
    So the prerequisite to this story might be starting with our Gradle 2.0 idea of pushing allprojects / subprojects / common settings to settings.gradle.
    * Perhaps it's time to make projects a domain object container and allprojects can be replaced with 'projects.all'