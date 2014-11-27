With more and more CPU cores available on developer desktops and CI servers, it is important that Gradle is able to fully utilise these processing resources. By executing
separate project builds within a multiproject build in parallel, we provide a relatively simple mechanism to better utilise available CPU resources.

Parallel project execution will allow the separate projects in a *decoupled* multi-project build to be executed in parallel. A multi-project build is considered *decoupled*
if none of the project objects directly or indirectly access another project object at configuration or execution time. A full description of what is considered project
coupling and our plan for allowing decoupled projects can be found in ./project-configuration-model.md.

While parallel _execution_ does not strictly require decoupling at _configuration_ time, the long-term goal is to provide a powerful set of features that will be available for fully decoupled
projects. Such features will include configuration of projects in parallel, re-use of configuration for unchanged projects, project-level up-to-date checks, and using pre-built artifacts in the
place of building dependent projects. See [project-configuration-model](project-configuration-model.md) for details.

# Use cases

- Reduce total build time for a multi-project build where execution is IO bound or otherwise does not consume all available CPU resources
- Provide faster feedback for execution of small projects without awaiting completion of other projects

# User visible changes

## Command-line option

To execute a multi-project build in parallel the user must declare that they would like their projects executed in parallel, via a command-line switch:

    --parallel
        \\ Tells Gradle to execute decoupled projects in parallel. Gradle will attempt to determine the optimal number of executors to use.
    --parallel-threads=4
        \\ Tells Gradle to execute decouple projects in parallel, using the specified number of executors.

## Build environment property

Similar to the Gradle Daemon, it should be possible to enable/configure parallel execution on a per-user and per-project basis.
This could be done via a build environment property set in `gradle.properties`:

    org.gradle.parallel: When set to `true`, Gradle will execute with the parallel executer

    org.gradle.parallel.threads: Specify the maximum number of threads to use for parallel execution. This property does not in itself enable parallel execution,
                                 but the value will be used whether Gradle is executed with `--parallel` or `org.gradle.parallel=true`.

## Project decoupling

To execute a multi-project build in parallel the user must first declare which projects are/should-be decoupled.
This will be done via the settings DSL in the `settings.gradle` file.

Two ways to define coupled projects in settings.gradle. In both cases A and B are coupled and cannot be executed in parallel, as are C & D.

    include {':A', ':B'}, {':C', ':D'}, ':E'

or

    include ':A', ':B', ':C', ':D', ':E'
    coupled {':A', ':B'}, {':C', ':D'}

Decoupled projects will get a fail-fast copy of the multi-project model, that will prohibit access to the model of other projects except via declared dependencies.

For backward-compatibility and transition we will need to provide a model that warns about inter-project coupling, but does not fail the build.
This will be available via a command-line option: `undeclared-project-coupling=warn` (other options are `fail` and `ignore`).
The default value of this option will be  `ignore` if the build is executed sequentially (default).

Since configuration-time decoupling is _not_ strictly required for parallel project execution, the `warn` option will allow many existing multi-project builds
(which are actually coupled at configuration-time and not execution-time) to take advantage of parallel project execution. For a transitional period, the default value for this option will be
`warn` when run with the parallel execution option. When we have provided the necessary infrastructure for most basic project coupling use-cases to be solved without coupling, we will switch the
default value for this option to be `fail` when the build is executed in parallel (and possibly even when the build is executed sequentially). (This will be done via a deprecation cycle).

## Handling Build Failures

Similar to sequential execution, by default Gradle will attempt to stop execution as soon as possible after a task failure occurs. Any tasks currently in progress will be completed, but no new
tasks will be scheduled once a task failure is detected.

When a task fails, the task will be reported as FAILED in the regular task logging:

    > :A:somedep
    > :A:task1 FAILED
    > :B:task1
    > :B:task2 FAILED
    > :C:task1

Once a task has failed, the console status bar will report that the build is currently 'failed' and the number of failures detected:

    > Failed > :B:task1
    > 3 Failed > :C:task1

This logging and status bar output would be useful for `--continue` as well.

We should also

## Reporting multiple build failures

Parallel execution implies that multiple failures may occur for a single build execution. The build output will need to handle this possibility.

#### Display composite output with all failures reported:

    FAILURE: Build completed with 2 failures.

    1: Task failed with exception
    -----------
    * Where:
    Build file '/Users/daz/dev/gradlex/parallel/build.gradle' line: 31

    * What went wrong:
    Execution failed for task ':A:prepareA'.
    > java.lang.AssertionError: Failed!!!. Expression: false

    * Try:
    Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.
    ==============================================================================

    2: Task failed with exception
    -----------
    * Where:
    Build file '/Users/daz/dev/gradlex/parallel/build.gradle' line: 31

    * What went wrong:
    Execution failed for task ':B:prepareB'.
    > java.lang.AssertionError: Failed!!!. Expression: false

    * Try:
    Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.
    ===============================================================================


    BUILD FAILED

#### When run with --stacktrace, include the stack trace output for each exception:

    FAILURE: Build completed with 2 failures.

    1: Task failed with exception
    -----------
    Build file '/Users/daz/dev/gradlex/parallel/build.gradle' line: 31

    * What went wrong:
    Execution failed for task ':A:prepareA'.
    > java.lang.AssertionError: Failed!!!. Expression: false

    * Exception is:
    org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':A:prepareA'.
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:68)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:46)
    Caused by: org.codehaus.groovy.runtime.InvokerInvocationException: java.lang.AssertionError: Failed!!!. Expression: false
        at org.gradle.api.internal.AbstractTask$ClosureTaskAction.execute(AbstractTask.java:447)
        at org.gradle.api.internal.AbstractTask$ClosureTaskAction.execute(AbstractTask.java:431)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:60)
        ... 77 more
    Caused by: java.lang.AssertionError: Failed!!!. Expression: false
        at build_5kojn66d37ehujo7g60o3qupvs$_run_closure2_closure11.doCall(/Users/daz/dev/gradlex/parallel/build.gradle:31)
        ... 80 more

    * Try:
    Run with --info or --debug option to get more log output.
    ==============================================================================

    2: Task failed with exception
    -----------
    Build file '/Users/daz/dev/gradlex/parallel/build.gradle' line: 31

    * What went wrong:
    Execution failed for task ':B:prepareB'.
    > java.lang.AssertionError: Failed!!!. Expression: false

    * Exception is:
    org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':B:prepareB'.
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:68)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:46)
    Caused by: org.codehaus.groovy.runtime.InvokerInvocationException: java.lang.AssertionError: Failed!!!. Expression: false
        at org.gradle.api.internal.AbstractTask$ClosureTaskAction.execute(AbstractTask.java:447)
        at org.gradle.api.internal.AbstractTask$ClosureTaskAction.execute(AbstractTask.java:431)
        at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:60)
        ... 77 more
    Caused by: java.lang.AssertionError: Failed!!!. Expression: false
        at build_5kojn66d37ehujo7g60o3qupvs$_run_closure2_closure11.doCall(/Users/daz/dev/gradlex/parallel/build.gradle:31)
        ... 80 more

    * Try:
    Run with --info or --debug option to get more log output.
    ==============================================================================

    BUILD FAILED

Any enhancement to report multiple failures should work equally well for `--continue`.

## Logging

Completely interleved logging may be very confusing:

    [:A:task1] About to start 'A1'
    [:B:task1] About to start 'B1'
    [:A:task1] Doing some work for 'A1'
    [:B:task1] Doing some work for 'B1'
    [:A:task1] Doing more work for 'A1'
    [:B:task1] Finishing 'B1'
    [:A:task1] Finishing 'A1'

By buffering log messages and flushing to console in a smart way we could provide
a reasonable user experience. For example, we could flush when no message has arrived for 500ms, or when we have not flushed for 5s.

    :A:task1
    About to start 'A1'
    Doing some work for 'A1'
    :B:task1
    About to start 'B1'
    Doing some work for 'B1'
    :A:task1
    Doing more work for 'A1'
    :B:task1
    Finishing 'B1'
    :A:task1
    Finishing 'A1'

The output could be enhanced with some extra structure, if it proved useful:

    [:A:task1]
    | Doing some work for 'A1'
    | Doing some more work for 'A1'
    [:B:task1]
    | Doing some work for 'B1'
    | Doing some more work for 'B1'
    [:A:task1]
    | Even more work for 'A1'
    [:B:task1]
    | Finishing 'B1'
    [:A:task1]
    | Finishing 'A1'

Alternatively, we could flush the log messages to console only when the task has completed, providing a similar output to the sequential build. This would have the benefit of making post-hoc analysis
of the build output simpler, with the downside of providing no feedback during task execution. Execution feedback could be provided by enhancing the logging DSL so that it is easier to generate
progress logging and send messages to the console status bar.

## Status Bar

The current status bar text displays the current state of execution of a single build process.
Parallel execution will require a summary progress line that reports the number of currently executing build threads, and their status:

` > Building > 23/34 completed > 2 running`
` > 2 Failed > 23/34 completed > 1 running`

So the first section will be the status of entire build (same for sequential execution). The remainder is the summary of parallel execution, which replaces the task
execution report from sequential execution.

In addition, for a low number of executing threads (<=4), we could display the task execution report in a separate status line per build process:

    1 > :A:task1
    2 > :B:task1 > Resolving dependencies ':runtime'
    3 > :C:task2 > 4Mb / 10Mb downloaded
    > Building > 23/34 Tasks completed > 3 running

# Integration test coverage

We will introduce new GradleExecuter implementation that chooses the parallel executer, and run the full coverage build with this executer.
In addition to this, we should beef up our coverage for multi-project build scenarios:
Happy-day:

- Multiple, unrelated projects
- A->B->C
- A->[B,C] where B->C
- A->[B,D] where B->C->D
- A->[B,C] where B->D and C->D
- Project dependency cycle with no task dependency cycle A:task1->B:task1->C:task1->A:task2

Sad-day:

- Task dependency cycle across projects: A:task1->B:task1->C:task1->A:task1
- Project dependency fails: A:1->B:1 and B:1 fails
- One of a set of dependencies fails: A->[B,C] and C->D and B fails
- Multiple independent dependencies fail: A->[B,C] and both B & C fail

# Implementation approach

- Add an TaskGraphExecuter implementation that executes multiple tasks at once. All tasks for a particular project should be executed within the same execution thread.
- Add a command-line option to enable the parallel executer, and specify the number of build threads that should be started.
- Introduce a GradleExecuter test fixture that will execute a build using the parallel option. Configure an experimental CI build that uses this executer.
- Increase integration test coverage for multi-project builds, for all forms of execution. Add additional integration tests for cases specific to parallel execution.
- Report on all failures for a build: for --continue and --parallel

- Added build environment properties for enabling/configuring parallel execution: `org.gradle.parallel` and `org.gradle.parallel.threads`.
- Update status bar to provide feedback on current build status for --parallel and --continue
- Fix thread-safety of compiler daemon by serialising access. Only one project can utilise the compiler daemon at any one time.
- Review and fix thread-safety of: profiler, task up-to-date checking, dependency resolution and publication.

- Warn when project coupling is detected while executing projects in parallel
- When using the parallel executer, give each project a copy of the model. This will prevent things sporadically failing due to timing issues.
- Buffer all output for a task and only flush to console when it is complete. No special handling for large amounts of console output.
- Provide basic summary status bar for parallel project execution
- Determine a sensible default for number of parallel threads of execution when none is specified.
    Currently, it's based on available CPUs but capped to 4. We should remove the cap or make further tests when we implement various cache access performance tweaks.
- Add a command-line option to specify that projects are decoupled: both warning and failing modes should be supported.
- More sophisticated task logging: interleaved project output with some buffering and pretty-printing
- Display multiple status bars, one for each build executor and one summary (up to a certain threshold).
- Link up the number of parallel task executers and the number of parallel test executers.

# Open issues

# Out of scope

This feature only deals with performing the execution phase of decoupled projects in parallel threads within a single process.
Each project build will be executed in a different thread in the same build process. Out of scope for this feature:

- Distributing parallel execution across multiple processes
- Distributing parallel execution across multiple machines
- Distributing execution on different OS/JVM environments
- Parallel execution of tasks within a single project
- Allowing decoupled projects to switch between downloading binary artifacts and rebuilding artifacts from source for dependencies
- Providing features that enable projects to be more easily decoupled at configuration and execution time
- Configuration of projects in parallel. See [project-configuration-model](project-configuration-model.md)
