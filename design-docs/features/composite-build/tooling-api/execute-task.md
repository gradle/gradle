# Milestone: Tooling client can define a composite and execute tasks

## Tooling client executes task in a single project within a composite

### Overview

This story adds support for executing tasks in a single project within a composite.

### API

The GradleConnection interface gets a new method for creating a `BuildLauncher` for a particular project.
```
BuildLauncher newBuild(BuildIdentity buildIdentity)
```

### Implementation notes

This story will only add support for executing a task.
Forwarding console output (`setStandardOutput`) is not implemented as part of this story.


### Test coverage

- test that a task can be executed in a build with a single participant.
- test that a task can be executed in a build with multiple participants.
- throws exception when task execution requested on a build with is not part of the composite
- test that a task can be selected with a Launchable
- test that progress listener events get forwarded to TAPI client
  - compare events from task execution from a composite build and a regular build

### Documentation

### Open issues

- forwarding standard input, output & error
- setting JVM arguments for execution
