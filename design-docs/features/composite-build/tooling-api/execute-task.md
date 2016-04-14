## Tooling client executes task within a composite using `Launchable` instance

### Overview

This story adds support for executing tasks in a specified project within a composite.
The focus is on task execution from an IDE, so uses a `Launchable` instance as the key for execution.

### API

The `Launchable` interface has a `ProjectIdentifier`, making it possible to use as the key for task execution.

```
public interface Launchable {
    ProjectIdentifier getProjectIdentifier();
}
```

An internal API will be added for scripting task execution, taking a build root directory and task name as target for execution.

```
public interface CompositeBuildLauncher extends BuildLauncher {
    BuildLauncher forTasks(File buildDirectory, String... tasks);
}
```

### Test coverage

- test that a task can be executed in a build with a single participant.
- test that a task can be executed in a build with multiple participants.
- throws exception when task execution requested on a build with is not part of the composite
- test that a task can be selected with a Launchable
- test that progress listener events get forwarded to TAPI client
  - compare events from task execution from a composite build and a regular build

