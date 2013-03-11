
This spec defines some improvements to improve incremental build and task up-to-date checks

# Use cases

## Plugin author implements a task that does work only on input files that are out-of-date

Some tasks take multiple input files and transform them into multiple output files. When this transformation is expensive,
the task author may wish to implement the task such that it can perform the transformation only on those input files
that are out-of-date WRT their output files.

An example of such a task is a C++ compilation task. C++ source files are compiled individually to separate object files. Only those
source files which have changed (or whose output file has changed) since last time the compilation task has executed need to be compiled.
When the compile settings change, then all source files must be recompiled.

Gradle should provide some mechanism that allows an incremental task to be implemented.

## Plugin author implements a task that can accurately describe its output files

For some tasks, the exact output files are not known until after the task has completed. For these tasks, Gradle scans the output directories
both before and after task execution and determines the output files based on the differences. This has a couple of drawbacks:

- It's potentially quite slow, and often the task implementation can ddetermine the output files more cheaply.
- It's not very accurate if the task does any kind of up-to-date checking itself or if it changes file timestamps.

The set of output files is currently used for:

- Detecting changes in the outputs when determining whether the task is up-to-date or not.
- Removing stale output files. Currently, this is something that a task must implement itself. More on this below.

An example of such a task is the Java compilation task. It uses the Java compiler API to perform the compilation. This API can be queried to
determine the output files that will be generated. The Java compilation task could make this available to Gradle, rather than
requiring two scans of the output directory to calculate the output files (one scan will still be necessary to check for changed
output files).

Gradle should provide some mechanism that allows a task to notify Gradle of its actual output files during task execution. We
should also investigate better mechanisms for detecting the output files of a task (eg native file change notifications).

## Plugin author implements a task that removes stale output files

For most tasks that produce output files, stale output files from previous executions of the task should be removed when the task is executed.
For example, old class files should be removed when the source file is renamed. Old test result files should be removed when the tests
no longer exist. And so on.

Accurate handling of stale output files makes running `clean` much less useful.

For a task with `@SkipWhenEmpty` applied to its inputs, all output files should be removed when the task has no inputs.

Gradle should provide some simple mechanism to declare that stale outputs for a given task (type) be removed.

## Simplify the process of writing task implementations

Currently, the task implementation classpath is not treated as an input of the task. This means that changing the task implementation
does not trigger task execution, which in turn makes developing the task implementation awkward.

Gradle should invalidate a task's outputs when its implementation changes.

## Fix up-to-date issues on copy tasks

# Implementation plan

## Plugin author implements incremental task

TBD

# Open issues

None yet.
