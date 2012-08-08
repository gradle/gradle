With more and more CPU cores available on developer desktops and CI servers, it is important that Gradle is able to fully utilise these processing resources. By executing
separate project builds within a multiproject build in parallel, we provide a relatively simple mechanism to better utilise available CPU resources.

Parallel project execution will allow the separate projects in a *decoupled* multi-project build to be executed in parallel. A multi-project build is considered *decoupled*
if none of the project objects directly access another project object at configuration or execution time.
While parallel _execution_ does not strictly require decoupling at _configuration_ time, the long-term goal is to provide a powerful set of features that will be available for fully decoupled
projects. Such features will include configuration of projects in parallel, re-use of configuration for unchanged projects, project-level up-to-date checks, and using pre-built artifacts in the
 place of building dependent projects.

# Use cases

- Reduce total build time for a multi-project build where execution is IO bound or otherwise does not consume all available CPU resources
- Provide faster feedback for execution of small projects without awaiting completion of other projects

# User visible changes

## Project decoupling

To execute a multi-project build in parallel the user must first declare that their projects are decoupled via a command-line switch:

    --decoupled-projects 
        // Indicates that the direct access between all projects should be prohibited (1)
    --decoupled-projects=warn
        // Indicates that Gradle should warn about project coupling, but allow the build to continue (2)

Projects will get a fail-fast copy of the multi-project model, that will prohibit direct, undeclared access to the model of other projects (--decoupled-projects).
Since configuration-time decoupling is _not_ strictly required for parallel project execution, it will also be useful to provide a model that warns about inter-project coupling,
but does not fail the build (--decoupled-projects=warn). This will allow many existing multi-project builds (which are coupled at configuration-time and not execution-time) to take
advantage of parallel project execution.

In the future, a DSL will allow finer-grained configuration of project coupling, where a subset of projects may be coupled but that subset could be decoupled from other projects. Coupled projects
would need to be executed sequentially, while decoupled project groups could be executed in parallel.

## Parallel executors

To execute a multi-project build in parallel the user must also declare that they would like their projects executed in parallel, via a command-line switch:

    --parallel-executor
        \\ Tells Gradle to execute decoupled projects in parallel. Gradle will attempt to determine the optimal number of executors to use.
    --parallel-executor-threads=4
        \\ Tells Gradle to execute decouple projects in parallel, using the specified number of executors.


## Handling Build Failures

Similar to sequential execution, by default Gradle will attempt to stop execution as soon as possible after a task failure occurs. Any tasks currently in progress will be completed, but no new
tasks will be scheduled once a task failure is detected.

Once a task has failed, the console status bar will report that the build is currently 'failed' and the number of failures detected. 
`> Build Failed (1) > :A:producerA`
This console output would be useful for `--continue` as well.

## Reporting multiple build failures

Parallel execution implies that multiple failures may occur for a single build execution. The build output will need to handle this possibility.

#### Display composite output with all failures reported:

    FAILURE: Build failed with 2 exceptions.

    * Exception 1 at:
        Build file '/Users/daz/dev/gradlex/parallel/build.gradle' line: 31

        * What went wrong:
        Execution failed for task ':A:prepareA'.
        > java.lang.AssertionError: Failed!!!. Expression: false

    * Exception 2 at:
        Build file '/Users/daz/dev/gradlex/parallel/build.gradle' line: 31

        * What went wrong:
        Execution failed for task ':B:prepareB'.
        > java.lang.AssertionError: Failed!!!. Expression: false

    * Try:
    Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.

    BUILD FAILED

#### When run with --stacktrace, include the stack trace output for each exception:

    FAILURE: Build failed with 2 exceptions.

    * Exception 1 at:
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

    * Exception 2 at:
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
of the build output simpler, with the downside of providing no feedback during task execution. Execution feedback could be provided by enhancing the logging DSL so that it was easier to push
messages to the console status bar.

## Status Bar

The current status bar text displays the current state of execution of a single build process. We will add a summary progress line that reports the number of currently executing build threads, and their status:
` > Building > 23 Tasks completed > 2 running [:A:task1, :B:task1]`
` > Build failed > 23 Tasks completed > 1 failed [:A:task1] > 1 running [:B:task1]`

In addition, for a low number of executing threads (<=4), we could display a separate status line per build process:

    1 > Build failed > :A:producerA
    2 > Building > :B:task1 > Resolving dependencies ':runtime'
    3 > Building > :C:task2 > 4Mb / 10Mb downloaded
    > Build failed > 23 Tasks completed > 1 task failed [:A:task1] > 2 running [:B:task1, :C:task2]

# Integration test coverage

We will introduce new GradleExecuter implementation that chooses the parallel executer, and run the full coverage build with this executer.
In addition to this, we should beef up our coverage for multi-project build scenarios:
Happy-day:

- Multiple, unrelated projects
- A depends on published artifact of B
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

This will be a new executer implementation, that should be a plug-in replacement for the existing implementation. This will require some refactoring of the GradleTaskExecuter
to extract common functionality, but once done there should be little or no further impact on the existing functionality.
The new executer will not support all of the existing features permitted in a multi-project build, and should fail-fast whenever a project attempts to directly access the model
of another project during execution.
Any new features for declaring project inputs/outputs will be available to default and parallel build execution.

# Open issues

# Out of scope

This feature only deals with performing the execution phase of decoupled projects in parallel threads within a single process.
Each project build will be executed in a different thread on the same machine. Out of scope for this feature:

- Distributing parallel execution across multiple processes
- Distributing parallel execution across multiple machines
- Distributing execution on different OS/JVM environments
- Parallel execution of tasks within a single project
- Allowing decoupled projects to switch between downloading binary artifacts and rebuilding artifacts from source for dependencies
- Providing features that enable projects to be more easily decoupled at configuration and execution time
- Configuration of projects in parallel

## Project outputs

In many cases, a ProjectA will depend on artifacts that are generated by another ProjectB where these artifacts are not explicitly published by ProjectB. An example is a
version number generated by a subproject and consumed by all other subprojects, or the raw test output from a set of projects which are then aggregated by a separate aggregating
project into a coherent test report.

We plan to introduce a number of new features that will make it easy to support these common use cases. (Design TBD)

- Declaring a simple value as a project output, and depending on that value as a project dependency
- Declaring a file or directory as a project output, and depending on that value as a project dependency

