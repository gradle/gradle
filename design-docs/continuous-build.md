# Continuous Build improvements

## Story: Detecting changes during a build

The continuous build should detect changes to inputs during a running build. 
When a change is detected, the current build should be cancelled and a new build should be re-triggered.

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

Usage of `FileSystemSubset.Builder` in `registerWatchPoints` method of `FileCollectionDependency` interface should be replaced with an interface that has the registration methods on it and so that registering inputs doesn't have to be coupled with `FileSystemSubset`.

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
- add test scenario with a simple build with tasks A, B, C, D , each having it's own directory as input
  - change input files to tasks after each task has been executed, but before build has completed
    - check that build gets cancelled and a new build gets triggered
  - change input file for task before a task has been executed
    - check that build doesn't get cancelled and continues executing
  - remove 'C' task from build and trigger a build by changing A's input file
    - check that build doesn't get re-triggered after completion when the non-existing's C task's inputs are changed
 

## Story: Detecting changes to build files or files that are input to the build configuration phase

TBD


# Open issues

- When running with the daemon, use of EOT to cancel the build requires an extra 'enter' due to the line buffering performed by the daemon client

> In practice, this isn't too big of an issue because most shells intepret ctrl-d as closure of stdin, which doesn't suffer this buffering problem.

- Interactive cancellation on windows requires pressing enter (https://issues.gradle.org/browse/GRADLE-3311)
