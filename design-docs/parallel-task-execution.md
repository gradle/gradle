This spec outlines the implementation of intra-project parallel task execution.

# Background

Gradle currently only supports parallel execution of tasks along the project boundary.
That is, tasks of independent projects may be executed in parallel… by way of the `--parallel` flag.
Intra-project parallel task execution has not been added to date due to concerns about the overwhelming potential for data corruption due to immutability.

Task objects implicitly have access to all objects of the project model, via their access to the `Project` instance via `AbstractTask`.
Moreover, they also often implicitly mutate state due to convention mapped values that having caching calculations.
As such, enabling parallel execution was deferred until a plan was in place to provide a completely safe way to write tasks that can be executed in parallel.
The “new configuration model” is effectively this plan.

In addition to the coming new way of writing safe-to-parallelize tasks, we will provide a mechanism for tasks written the existing way to opt in to parallel execution.
Such tasks will be effectively promising that they conform to certain constraints (e.g. no mutation of non local data during execution).
Some enforcement of this will be implemented, but it will not be possible to provide complete safety.

Parallel execution will have to be explicitly enabled by the build user, as it is now.
That is, the `--parallel` and `--parallel-threads` command line flags will enable intra project parallelism.
If this proves to be to unstable initially, an extra flag may be added to allow existing users of project based parallel execution to continue to use it.

In addition to the specific tests written while implementing this, the existing parallel execution build will enable intra-project parallel task execution.

# Stories

## Milestone 1 - announce-able

### ~~`DefaultTask` based task implementation opts in to parallel execution via annotation~~

    @org.gradle.api.tasks.ParallelizableTask
    class MyTask extends DefaultTask {
    
    }
    
- Annotation is incubating
- No validation that the task is actually safe is provided at this stage
- `--parallel` and `--parallel-threads` enables parallel execution (same semantics for degree of parallelism are used)
- Progress and error reporting are unchanged from existing implementation for project based parallelism
- Non parallelizable tasks must never be executed in parallel with parallelizable tasks (i.e. they are executed exclusively)
- Annotation is not inherited (i.e. parallelizable super type does not imply parallel safe)
- If an action is added to a task (e.g. `doLast()`) it is not parallelizable, regardless of the presence of `@ParallelizableTask` (i.e. no guarantees that the action is safe)
- `DefaultTask` should be annotated with `@ParallelizableTask` (i.e. allow empty lifecycle tasks to be executed in parallel)

#### Test coverage

- ~~Given parallelizable tasks `:a` and `:b` with no relationship, tasks are executed in parallel~~
- ~~Given parallelizable tasks `:a:a`, `:a:b` and `:b:a` with no relationship, tasks are executed in parallel~~
- ~~Given parallelizable tasks `:a` depends-on `:b`, tasks are not executed in parallel~~
- ~~Given parallelizable tasks `:a` must-run-after `:b`, tasks are not executed in parallel~~
- ~~Given parallelizable tasks `:a` should-run-after `:b`, tasks are executed in parallel~~
- ~~Given parallelizable tasks `:a` depends-on `:b` and `:c`, `:c` and `:b` are executed in parallel, `:a` is not executed until both `:b` and `:c` complete~~
- ~~Given parallelizable tasks `:a` depends-on `:b` and `:c`, `:c` and `:b` are executed in parallel, where `:c` fails, `:a` is not executed~~
- ~~Given parallelizable tasks `:a`, `:b`, `:c`, `:d`, and `--parallel-threads=2`, only 2 tasks are ever executed in parallel~~
- ~~Given task `:a`, depends on parallelizable tasks (`:b`, `:c`) and non parallelizable task `:d`, `:d` is not executed until `:b` and `:c` have completed~~
- ~~Given task `:a`, depends on parallelizable tasks (`:c`, `:d`) and non parallelizable task `:b`, `:c` and `:d` are not executed until `:b` has completed~~
- ~~Task type `B extends A` were `A` has annotation, instances of `B` are not executed in parallel~~
- ~~Given parallelizable tasks `:a` and `:b`, if `:b` has a custom action (an action added using `doLast()`) then tasks are not executed in parallel~~

### Suitable tasks of `JavaPlugin` are parallel enabled

Candidates:

- `JavaCompile`
- `Javadoc`
- `Test`
- `Jar`
- `Copy`

The implementation of these tasks needs to be examined/changed to ensure they are safe to parallelize.

#### Notes

It's likely that we will encounter concurrency issues with our infrastructure in this story.
For example, we can quite easily end up with attempted concurrent resolution of the same dependency configuration.
Much of this story may be fixing such problems.
We may find that the amount of problems means that we need to temporarily not enable intra-project task parallelism via `--parallel` etc. so as not to destabilize existing users of project based parallelism.

#### Test Coverage

- ~~`./gradlew clean compileJava` is safe, in that `clean` is run exclusively~~ 
- ~~Ensure that existing parallel CI builds are executing all integration tests using Java plugin with intra project parallel task execution~~

### Some level of validation/enforcement for tasks declaring to be parallel safe

What exactly can be/should be verified and/or enforced is yet to be determined.

Ideally, the parallelizable tasks of the JavaPlugin (previous story) conform to the requirements without breaking changes.

### Suitable tasks of native plugins are parallel enabled

Candidates:

- `Assemble`
- `CCompile`
- `CppCompile`
- `ObjectiveCCompile`
- `ObjectiveCppCompile`
- `WindowsResourceCompile`
- `CreateStaticLibrary`
- `InstallExecutable`
- `LinkExecutable`
- `LinkSharedLibrary`
- `RunTestExecutable`

The implementation of these tasks needs to be examined/changed to ensure they are safe to parallelize.

### Suitable tasks of Java code quality plugins are parallel enabled

Checkstyle etc.

### Two tasks are not executed in parallel if their outputs are declared as overlapping parts of the filesystem

It's possible that two task write to overlapping parts of the file system.
This might cause data corruption as well as exceptions being thrown while 
This is indeed the case for `Test` tasks used in a single project because Java plugin defaults the report directory of all `Test` tasks to the same directory.
Overlapping outputs declared by two tasks should be detected and such tasks should not be executed in parallel.
 
#### Test coverage

- Two parallelizable tasks `:a` and `:b` don't run in parallel if the same file `x` is output of both task `:a` and task `:b`
- Two parallelizable tasks `:a` and `:b` don't run in parallel if directory `x` is an output of task `:a` and file `x/y/z` is output of task `:b`
- Two non-parallelizable tasks `:a:a` and `:b:a` don't run in parallel if the same file `x` is output of both task `:a:a` and task `:b:a`
- Two non-parallelizable tasks `:a:a` and `:b:a` don't run in parallel if directory `x` is an output of task `:a:a` and file `x/y/z` is output of task `:b:a`

### Document how to write parallelizable tasks

- How to enable
- Explanation of progress output and failure output
- How to limit parallelization as build author (i.e. consumer of task implementations)
- How to implement parallelizable tasks
    - Constraints
    - Design considerations

### Enable parallel execution of all applicable tasks shipped with Gradle

## Milestone 2 - improved task scheduling

## Milestone 3 - improved feedback/reporting when executing tasks in parallel

# Backlog

## Misc known concurrency issues

- DefaultIsolatedAntBuilder (build scoped & mutable)
- `JdkJavaCompiler` relies on setting `java.home` system property to call `ToolProvider.getSystemJavaCompiler()`
- Java plugin defaults the report directory of all `Test` tasks to the same directory
- Old Maven plugin is not safe to use concurrently
- Incorrect logging from ProgressLogEventGenerator if start and end events for different operations interleave leading to reporting incorrect task status, e.g. saying that a task was up-to-date when actually a different task was up-to-date.

## Task inputs

- Tasks may call convention mapped getters that are not threadsafe, which the task impl can't know about (e.g. default dependency pattern used by code quality plugins to provide default impl)
- Input data types may not be safe to read concurrently (though, task implementors should be able to determine this, or specify that any impls/specializations of input types are thread safe and effectively immutable at execution)

## Usability/Configurability

- ~~Can't enable parallel execution with specified thread pool size via “env” (i.e. no sys prop, `gradle.startParameter.parallelThreadCount = «N»` does not work because value is read very early)~~
- Profile report should include information on how parallelization played out (e.g. what was prevented from being parallelized due to custom actions/overlapping outputs etc.)
- Should be some kind of help available for improving parallel-ness of the build (e.g. being able to visualize the potential graphs to spot choke points etc.)