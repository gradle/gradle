# Continuous build: Improved change detection

- [ ] Continuous build will trigger a rebuild when an input file is changed during build execution
- [ ] Developer is able to easily determine the task/file that triggered a rebuild
- [ ] Continuous build will trigger a rebuild when build configuration file changes

## Backlog / Open Issues / Not in scope

- Responding to changes to “dynamic” dependencies (i.e. in the `dependencies {}` sense)
- Responding to dynamic inputs to build logic (e.g. properties file read by adhoc user code that externalises build logic)
- Changes to files behind symlinks are not respected
- Provide some way to manually trigger a rebuild (e.g. inputs changes before build finished, file system changed was captured - either correctly or incorrectly)

## Stories

### Story: Continuous build will trigger a rebuild when an input file is changed during build execution

#### Current implementation

Currently continuous build registers inputs for watching after the build is completed.
Changes that happen after a task is executed, but before the file watching starts, will remain unnoticed.

The current interfaces of `FileSystemChangeWaiter` is:
```java
public interface FileSystemChangeWaiter {
    void wait(FileSystemSubset taskFileSystemInputs, BuildCancellationToken cancellationToken, Runnable notifier);
}
```

#### Implementation plan

To be able to detect changes during the running build, the file watching should start before the task is executed.
The current `FileSystemChangeWaiter` interface should be replaced (or modified) to support this.

The inputs for a task are registered for watching just before the task is executed in the build.

Usage of `FileSystemSubset.Builder` in `registerWatchPoints` method of `FileCollectionInternal` interface should be replaced with an interface that has the registration methods on it and so that registering inputs doesn't have to be coupled with `FileSystemSubset`.

The current code in [ContinuousBuildActionExecuter](https://github.com/gradle/gradle/blob/fe03c3d452b6c04a152f4485e7598c0a4f295340/subprojects/launcher/src/main/java/org/gradle/launcher/exec/ContinuousBuildActionExecuter.java#L112-L117):

```
            FileSystemSubset.Builder fileSystemSubsetBuilder = FileSystemSubset.builder();
            try {
                lastResult = executeBuildAndAccumulateInputs(action, requestContext, actionParameters, fileSystemSubsetBuilder, buildSessionScopeServices);
            } catch (ReportedException t) {
                lastResult = t;
            }
```

`FileSystemSubset.Builder` should be replaced with a handle that registers the inputs immediately for watching.

The current implementation in `DefaultFileSystemChangeWaiter` (which might be renamed) should be modified to expose a registration interface. The `FileWatcher` should also support registering new inputs after it has been initialized.

Implementation details can be planned further after spiking the changes in the areas mentioned above.

#### Test coverage

- all current continuous build tests should pass

- add test scenario with a build with tasks A, B, C, D each having it's own directory as input. Tasks have dependencies so that B depends on A, C on B and D on C. Request building task D.
  - change input files to tasks after each task has been executed, but before the build has completed
    - check that a new build gets triggered
  - change input file for task during the task is executed
    - check that a new build gets triggered
  - build executes and fails in task C
    - change input file to D
      - no build will be triggered because of the way how inputs are registered.
        - similar behavior in current continuous build
    - change input file of task B
      - a new build gets triggered
    - change input file of task C
      - a new build gets triggered



## Story: Developer is able to easily determine the task/file that triggered a rebuild

This is an extension to the story "Continuous build will trigger a rebuild when an input file is changed during build execution". When a change is detected, the path of the task whose input changed should be printed to the console.

This helps the user find out the reason which triggered the build. It is possible that a continuous build keeps re-triggering new builds when the user isn't doing any changes. This happens when a task changes it's own inputs during execution or when a task changes the inputs of a task that has been executed in the build before it. The user should fix such builds since continuous build will not prevent such loops.

## Story: Continuous build will trigger a rebuild when build configuration file changes

- Need to handle various inputs to build logic:
    - `buildSrc`
    - project build scripts
    - script plugins
    - dynamic plugin dependencies


