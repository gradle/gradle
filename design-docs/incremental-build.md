
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

- It's potentially quite slow, and often the task implementation can determine the output files more cheaply.
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

## Don't compile a source file when the API of its compile dependencies has not changed

Currently, changing the body of a method invalidates all class files that have been compiled against the method's class. Instead, only the method's class should be recompiled.
Similarly, changing a resource file invalidates all class files that included that resource file in the compile classpath. Instead, resource files should be ignored
when compiling.

We don't necessarily need a full incremental Java compilation to improve this. For example, the Java compilation task may consider the API of the compile classpath - if it has
changed, then compile all source files, and if it has not, skip the task (assuming everything else is up to date). This means that a change to a method body does not propagate
through the dependency graph.

# Implementation plan

## Plugin author uses changes to input files to implement incremental task

Incremental input file changes will be provided via an optional parameter on the TaskAction method for a task. The TaskExecutionContext will provide access to the set of changed input files,
as well as a flag to indicate if incremental execution is possible.
If incremental execution is not possible, then the task action will be executed with a 'rebuild' context, where every input file is regarded as "ADDED".

Incremental execution is not possible when:
- If there are no declared outputs for the task
- One or more of the output files have changed since last execution
- One or more of the input properties have changed since last execution
- There is no information available about a previous execution
- The upToDate action of the task returns false
- Other cases listed in test cases below

    class IncrementalSync extends DefaultTask {
        @InputFiles
        def FileCollection src

        @OutputDirectory
        def File destination

        @TaskAction
        void execute(TaskExecutionContext executionContext) {
            if (executionContext.rebuild) {
                FileUtils.forceDelete(destination)
            }
            executionContext.inputFileChanges { change ->
                def target = new File(destination, change.file.name)
                if (change.added || change.modified) {
                    FileUtils.copyFile(change.file, target)
                } else if (change.removed) {
                    FileUtils.forceDelete(target)
                }
            }
        }
    }

### Test coverage

1. Incremental task action is executed with rebuild context when run for the first time
2. Incremental task action is executed with a an empty incremental context when run with no changes
3. Incremental task is executed with incremental context when run with:
    - Single added input file
    - Single removed input file
    - Single modified input file
4. Incremental task action is executed with 'rebuild' context (every input file flagged as "added") when:
    - Input property value has changed since previous execution
    - Task class has changed since previous execution
    - Output file has changed since previous execution
    - Single output file removed since previous execution
    - Task has no declared outputs
    - Task.upToDate() is false
    - Gradle build executed with '--rerun-tasks'

## Java compile task specifies its output files

TBD

# Open issues

None yet.
