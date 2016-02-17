# Continuous build: Improved responsiveness

- [ ] Continuous build does not trigger rebuild when an intermediate file is changed
- [ ] Provide optimal feedback to user when continuous build is triggered when build is in process

## Backlog / Open Issues / Not in scope

- Certain inputs might be known to be immutable (e.g. cached repository dependencies have a checksum in their path and will not change, system header files)

## Stories

## Story: Provide optimal feedback to user when continuous build is triggered when build is in process

This is an extension to the story "Continuous build will trigger a rebuild when an input file is changed during build execution".

There are several options for providing the feedback quickly to the user:

1. A) When a change is detected, the currently executing build is canceled. After a quiet period a new build is triggered.
2. B) When a change is detected and a build is currently executing, use an adaptive solution to complete the current build.
  - skip tasks that are non-incremental such as test execution tasks
  - complete incremental tasks such as compilation tasks

The implementation strategy will be chose when this story is designed and reviewed.

### Test coverage

- all current continuous build tests should pass

Assuming A) implementation strategy is chosen:
- additional test cases for test scenario with a build with tasks A, B, C, D each having it's own directory as input. Tasks have dependencies so that B depends on A, C on B and D on C. Request building task D.
  - change input files to tasks after each task has been executed, but before the build has completed
    - check that the currently running build gets canceled and a new build gets triggered
  - change input file for task during the task is executed
    - check that the currently running build gets canceled and a new build gets triggered

## Story: Continuous build does not trigger rebuild when an intermediate file is changed

The current implementation of continuous build registers to watch changes to the input files of all tasks that are executed.

Benefits of not watching for changes in intermediate files:
- Some builds might end up in a loop when their intermediate files keep changing. These builds would be able to use continuous build mode without changing the build logic.

- It would reduce file system I/O when we watch for changes in less files.

Intermediate files are outputs from one task and inputs to another task in the same task graph.

TBD
