# Continuous build: basic implementation

- [x] Continuous build with rebuild triggered by timer
- [x] Continuous build with rebuild triggered by file change
- [x] Continuous build with rebuild triggered by task input changes
- [x] Continuous build is executed via the Tooling API
- [x] Command line user exits continuous build mode without killing the Gradle process

## Stories

### Story: Add continuous Gradle mode triggered by timer

Gradle will be able to start, run a set of tasks and then wait for a retrigger before re-executing the build.

#### Implementation

See spike: https://github.com/lhotari/gradle/commit/969510762afd39c5890398e881a4f386ecc62d75

- Gradle CLI/client process connects to a daemon process as normal.
- Gradle Daemon knows when we're in "continuous mode" and repeats the last build until cancelled.
- Gradle CLI waits for the build to finish (as normal).
- Instead of returning after each build, the daemon goes into a retry loop until cancelled triggered by something.
- Initial implementation will use a periodic timer to trigger the build.
- Add new command-line option (`--continuous`)
    - Add a separate Parameters option
    - Think about how to introduce a new internal replacement for StartParameter
- Decorator for `InProcessBuildExecutor` changes to understand "continuous mode"
    - Similar to what the spike did
    - Build loop delegates to wrapped BuildActionExecuter
    - After build, executor waits for a trigger from somewhere else
- On Ctrl+C, Gradle exits and cancels build.

```
pseudo:

interface TriggerDetails {
    String reason
}
interface TriggerListener {
    void triggered(TriggerDetails)
}
interface Triggerable {
    TriggerDetails waitForTrigger()
}

// In run/execute()
while (not cancelled) {
    delegateExecuter.execute(...)
    triggerable.waitForTrigger()
}

// Triggerable
def waitForTrigger() {
    sync(lock) {
        while(!triggered) {
            lock.wait()
        }
    }
}

def triggered(TriggerDetails) {
    sync(lock) {
        lock.notify()
    }
}
```

#### Test Coverage

- ~~If Gradle build succeeds, we wait for trigger and print some sort of helpful message.~~
- ~~If Gradle build fails, we still wait for trigger.~~
- ~~Configuration errors should be treated in the same way as execution failures.~~
- ~~When "trigger" is tripped, a build runs.~~
- ~~Add coverage for a build that succeeds, then fails, then succeeds (eg a compilation error)~~
- ~~Fail when this is enabled on Java 6 builds, tell the user this is only supported for Java 7+.~~

### Story: Continuous Gradle mode triggered by file change

Gradle will be able to start, run a set of tasks and then monitor one file for changes without exiting.  When this file is changed, the same set of tasks will be re-run.

#### Implementation

- Watch project directory (`projectDir`) for changes to trigger re-run
- Add `FileWatchService` that can be given Files to watch
- When files change, mark the file as out of date
- Re-run trigger polls the watch service for changes at some default rate ("quiet period")
- ~~Ignore build/ .gradle/ etc files.~~

#### Test Coverage

- ~~When the project directory files change/are create/are delete, Gradle re-runs using the same set of task selectors.~~

#### Open Issues

N/A

### Story: Continuous Gradle mode triggered by task input changes

After performing a build, Gradle will automatically rerun the same logical build if the file system inputs of any task that was executed change.

#### Implementation

1. A logical description of the input files for each task that is executed is captured during the build
2. At the end of the build, Gradle will monitor the file system for changes
3. If any change occurs to a file/directory described by #1, the build will execute again after shutting down all file system listeners

Constraints:

1. There is no way to stop listening for filesystem changes other than use of ctrl-c or similar
2. Changes that after the task executes, but before the build completes and starts watching the file system, are “ignored” (i.e. do not trigger a rebuild)
3. Builds are triggered as soon as a change to a relevant file is noticed (i.e. no quiet period)
4. Continuous mode is not supported by Tooling API (build fails eagerly as unsupported)
5. Symlinks are treated as regular files and changes “behind” symlinks are not respected
6. Only explicit file system inputs are respected (i.e. no consideration given to build logic changes)
7. Changes to `buildSrc` are not respected
8. Only changes to inputs of task that were executed in the immediately preceding build are respected (`A.dependsOn(B.dependsOn(C))` - if C fails, changes to inputs of `A` and `B` are not respected)

#### Test Coverage

##### General

1. ~~Reasonable feedback when using continuous mode (e.g. incubating message, suitable message at end of build)~~
1. ~~If build fails before any task executes, build exits and does not enter continuous mode~~
1. ~~Can trigger rebuild by changing input file to simple task (i.e. basic smoke test)~~
1. ~~Continuous mode utilises class reuse (e.g. same build script class instance is used for subsequent builds)~~
1. ~~Change to non source input file of task with empty source does not trigger build~~

##### Simple Java plugin usage scenarios

For a conventional `apply plugin: 'java'` project:

1. ~~Can successfully build in continuous mode when there is no source (i.e. `src/main/java` does not exist)~~
1. ~~Can successfully build in continuous after source dir `src/main/java` is removed~~
1. ~~Addition of empty directories to `src/main/java` does not trigger rebuild (i.e. source is defined as `include("**/*.java")`)~~
1. ~~After compile failure of Java file, correcting file to be compilable triggers a successful build~~
1. ~~When running `test` in continuous mode:~~
    1. ~~Change to source file causes execution of tests~~
    1. ~~Change to test file causes execution of tests~~
    1. ~~Change to resource file (`src/main/resources`)~~
1. ~~Change to local filesystem compile dependency triggers rebuild (e.g. `lib/some.jar`, not a repo dependency)~~
1. ~~Remove of a local filesystem compile dependency triggers rebuild~~
1. ~~In a multi project, changes to Java source of upstream projects trigger the build of downstream projects~~
1. ~~Project that utilises external repository dependencies can be built in continuous mode (i.e. exercise dependency management layers)~~
1. ~~When main source fails to compile, changes to test source does not trigger build~~
1. ~~Creation of initial source file after initial “empty” build triggers building of jar (i.e. add `/src/main/java/Thing.java`)~~
1. ~~Addition of a local filesystem compile dependency triggers rebuild (e.g. `dependencies { compile fileTree("lib") }`)~~

##### Verifying constraints

1. ~~Continuous mode can be used successfully on a project with a `buildSrc` directory~~
2. ~~Attempting to run a continuous mode build from the Tooling API yields an error immediately~~

##### Edge cases

1. ~~Failure to determine file system inputs for tasks yields reasonable error message (e.g. `javaCompile.src(files( { throw new Exception("!") }))`)~~
1. ~~Task can specify project directory as a task input; changes are respected~~
1. ~~Task can specify root directory of multi project build as a task input; changes are respected~~
1. ~~Continuous mode can be used on reasonable size multi project Java build in conjunction with --parallel~~
1. ~~Can use a symlink as an input file~~
1. ~~Symlinks are not followed for watching purposes (i.e. contents of symlinked directory are not watched)~~

#### Archives

1. ~~With zip task whose contents are directory `src`, adding a new empty directory causes rebuild~~
1. ~~Changes to input zips are respected~~
1. ~~Changes to input tars are respected (compressed and uncompressed)~~

### Story: Continuous build is executed via the Tooling API

This story adds support for executing continuous builds via the Tooling API.
It does not address continually building/providing tooling models.
It also does not improve the general capabilities of continuous mode.

#### Test Coverage

- ~~client executes continuous build that succeeds, then responds to input changes and succeeds~~
- ~~client executes continuous build that succeeds, then responds to input changes and fails, then … and succeeds~~
- ~~client executes continuous build that fails, then responds to input changes and succeeds~~
- ~~client can cancel during execution of a continuous build~~
- ~~client can cancel while a continuous build is waiting for changes~~
- ~~client can receive appropriate logging and progress events for subsequent builds in continuous mode~~
- ~~client receives appropriate error if continuous mode attempted on unsupported platform~~
- ~~logging does not include message to use `ctrl-d` to exit continuous mode~~
- ~~All tooling API clients that support cancellation (>=2.1) can run continuous build~~
- ~~Attempt to run continuous build with tooling api client that does not support cancellation fails eagerly~~
- ~~client can request continuous mode when building a model, but request is effectively ignored~~

### Story: Command line user exits continuous build mode without killing the Gradle process

Prior to this story, the only way for a command line user to exit continuous mode is to kill the process.
This story makes the use of continuous mode more effective by allowing better utilisation of warm Gradle daemons.

`ctrl-d` will replace `ctrl-c` as the advertised mechanism for escaping wait state when using continuous build.

#### Test Coverage
- ~~should cancel continuous build by [EOT](http://en.wikipedia.org/wiki/End-of-transmission_character) (ctrl-d)~~
- ~~should cancel build when System.in is closed~~
- ~~should cancel build when System.in contains some other characters, then closes~~
- ~~does not cancel on EOT or System.in closing when not interactive~~
- ~~does not cancel continuous build when other than EOT is entered~~
- ~~can cancel continuous build by EOT after multiple builds~~

