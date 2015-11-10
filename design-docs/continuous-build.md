# Continuous Build improvements

## Story: Continuous build will trigger a rebuild when an input file is changed during build execution

### Current implementation

Currently continuous build registers inputs for watching after the build is completed. 
Changes that happen after a task is executed, but before the file watching starts, will remain unnoticed.

The current interfaces of `FileSystemChangeWaiter` is:
```
public interface FileSystemChangeWaiter {
    void wait(FileSystemSubset taskFileSystemInputs, BuildCancellationToken cancellationToken, Runnable notifier);
}
```

### Implementation plan

To be able to detect changes during the running build, the file watching should start before the task is executed.
The current `FileSystemChangeWaiter` interface should be replaced (or modified) to support this.

The inputs for a task are registered for watching just before the task is executed in the build.

Usage of `FileSystemSubset.Builder` in `registerWatchPoints` method of `FileCollectionInternal` interface should be replaced with an interface that has the registration methods on it and so that registering inputs doesn't have to be coupled with `FileSystemSubset`.

The current code in [ContinuousBuildActionExecuter](https://github.com/gradle/gradle/blob/fe03c3d452b6c04a152f4485e7598c0a4f295340/subprojects/launcher/src/main/java/org/gradle/launcher/exec/ContinuousBuildActionExecuter.java#L112-L117):

```java
.           FileSystemSubset.Builder fileSystemSubsetBuilder = FileSystemSubset.builder();
            try {
                lastResult = executeBuildAndAccumulateInputs(action, requestContext, actionParameters, fileSystemSubsetBuilder, buildSessionScopeServices);
            } catch (ReportedException t) {
                lastResult = t;
            }
```

`FileSystemSubset.Builder` should be replaced with a handle that registers the inputs immediately for watching. 

The current implementation in `DefaultFileSystemChangeWaiter` (which might be renamed) should be modified to expose a registration interface. The `FileWatcher` should also support registering new inputs after it has been initialized. 

Implementation details can be planned further after spiking the changes in the areas mentioned above.

### Test coverage

- all current continuous build tests should pass

- add test scenario with a build with tasks A, B, C, D each having it's own directory as input. Tasks have dependencies so that B depends on A, C on B and D on C. Request building task D.
  - change input files to tasks after each task has been executed, but before the build has completed
    - check that a new build gets triggered
  - change input file for task during the task is executed
    - check that a new build gets triggered
  - build executes and fails in task C
    - change input file to D
      - no build will be triggered because of the way how inputs are registered. 
        - similar behaviour in current continuous build
    - change input file of task B
      - a new build gets triggered
    - change input file of task C
      - a new build gets triggered

### Open issues

- Self modifying tasks will cause a loop
- Builds with tasks that change inputs of tasks executed earlier in the build will cause a loop
- Jar task might need this fix: https://github.com/gradle/gradle/commit/4bc280cff6745e78e0b764c604c19214c5b9c946

## Story: Currently running build is canceled before triggering a new build

This is an extension to the previous story "Continuous build will trigger a rebuild when an input file is changed during build execution". When a change is detected, the currently executing build is canceled and a new build is triggered.

### Test coverage

- all current continuous build tests should pass

- additional test cases for test scenario with a build with tasks A, B, C, D each having it's own directory as input. Tasks have dependencies so that B depends on A, C on B and D on C. Request building task D.
  - change input files to tasks after each task has been executed, but before the build has completed
    - check that the currently running build gets canceled and a new build gets triggered
  - change input file for task during the task is executed
    - check that the currently running build gets canceled and a new build gets triggered


## Story: Detecting changes to build files or files that are input to the build configuration phase

TBD


# Open issues

- When running with the daemon, use of EOT to cancel the build requires an extra 'enter' due to the line buffering performed by the daemon client

> In practice, this isn't too big of an issue because most shells intepret ctrl-d as closure of stdin, which doesn't suffer this buffering problem.

- Interactive cancellation on windows requires pressing enter (https://issues.gradle.org/browse/GRADLE-3311)
