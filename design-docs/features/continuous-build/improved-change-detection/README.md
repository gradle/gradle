# Continuous build: Improved change detection

- [ ] Continuous build will trigger a rebuild when an input file is changed during build execution
- [ ] Developer is able to easily determine the task/file that triggered a rebuild

## Sub-Feature: Continuous build will trigger a rebuild when build configuration changes

- [ ] Continuous build will trigger a rebuild when build script changes
- [ ] Continuous build will trigger a rebuild when `buildSrc` changes
- [ ] Continuous build will trigger a rebuild when a script plugin changes
- [ ] Continuous build will trigger a rebuild when `settings.gradle` changes
- [ ] Continuous build will trigger a rebuild when `gradle.properties` or `~/.gradle/gradle.properties` changes
- [ ] Continuous build will trigger a rebuild when any file read as input by configuration script logic changes


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


### Story: Developer is able to easily determine the file(s) that triggered a rebuild

The absolute path of the first 3 changed files should be printed to the console when a new build gets triggered. When there are more than 3 changes, the number of remaining changes is shown.

#### Examples of the desired console output

A)
```
Waiting for changes to input files of tasks... (ctrl-d to exit)
new file: /full/path/to/file
modified: /full/path/to/file2
deleted: /full/path/to/file3
Change detected, executing build...
```
B)
```
Waiting for changes to input files of tasks... (ctrl-d to exit)
new file: /full/path/to/file
new file: /full/path/to/file2
new file: /full/path/to/file3
and some more changes
Change detected, executing build...
```

#### Special cases

When a file has been added, it should not be listed in the modifications. Usually both an addition and a modification event get reported in the file system events for each new file.

Directories are handled as ordinary files in the reporting with the exception that modified directories aren't reported at all since a directory modification is usually caused by a file change in that directory and that file change is the actual reason for the change.

#### Out of scope

It is possible that a continuous build keeps re-triggering new builds when the user isn't doing any changes. This happens when a task changes it's own inputs during execution or when a task changes the inputs of a task that has been executed in the build before it. Build users can use info or debug level logging in finding the problematic task in such problematic builds. It was decided that this story doesn't add any specific features to debug such problems.

It's also out of scope to report the path of the task whose input file the changed file is. It was decided to leave that out of the scope of this story.

#### Implementation plan

A new build gets triggered in continuous build after there is a quiet period in the file system event reception. The plan is to aggregate the events during the event reception and filter the events when the report of changes gets printed out just before the new build gets triggered. To clarify, changes won't be printed out until there has been a "quiet period" and the new build has been triggered.

#### Test coverage

Main scenario:

- add a test scenario with a build with tasks A, B, C, D each having it's own directory as input. Tasks have dependencies so that B depends on A, C on B and D on C. Request building task D.

Test cases:

- should report the absolute file path of the file added when 1 file is added to the input directory of each task (A, B, C, D).

- should report the absolute file path of the files added when 3 files are added to the input directory of each task (A, B, C, D).

- should report the absolute file path of the first 3 changes and report the number of other changes when more that 3 files are added to the input directory of each task (A, B, C, D).

Variations:
- vary the tests by removing 1, 3 or 11 files from an input directory
- vary the tests by modifying 1, 3 or 11 files in the input directory
- vary the tests by adding, removing and modifying 3 or 11 files in the input directory
- vary the tests by adding new directories
- vary the tests by removing directories
- vary the test by making changes in multiple task input directories (tasks A,B,C,D)

Special considerations:

- check that directories aren't reported as modified
- check that new files aren't reported as modified

- should report changes that happen when the build is executing

### Story: Continuous build will trigger a rebuild when a build script changes
- build.gradle / project.buildFileName of all projects in a build

### Story: Continuous build will trigger a rebuild when `buildSrc` changes
- buildSrc itself is a gradle build. The inputs of the tasks of the buildSrc build should be monitored for changes in the same way as an ordinary Gradle build is monitored in continuous build.

### Story: Continuous build will trigger a rebuild when a script plugin changes
- all "apply from: '/file/path'" including nested scripts

### Story: Continuous build will trigger a rebuild when `settings.gradle` changes
### Story: Continuous build will trigger a rebuild when `gradle.properties` or `~/.gradle/gradle.properties` changes
### Story: Continuous build will trigger a rebuild when any file read as input by configuration script logic changes



