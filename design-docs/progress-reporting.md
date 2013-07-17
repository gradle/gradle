
This spec describes some fixes for build progress reporting.

# Use cases

1. Integration test coverage for artifact download and upload progress reporting.
2. Correct rendering on terminals with limited width.
3. Allow Gradle to work when native libraries are not present, to assist in porting Gradle to Linux distributions where these libraries do not exist.
4. Progress reporting when uploading to Maven repositories.

# Implementation plan

## Integration test coverage for artifact download and upload

1. Add a test fixture class which, given a `GradleExecuter`, can configure the executer so that progress events are captured.
2. Change the implementation of `AbstractProgressLoggingHandler.logProgress()` so that progress event is generated only every n bytes (say 1k).
3. Add integration test cases listed below

One possible implementation for the test fixture is to have it generate an init script or build script that adds an `OutputEventListener` to the
global logging manager. For example:

    import org.gradle.logging.internal.*

    gradle.services.get(LoggingOutputInternal).addOutputEventListener(new OutputEventListener() {
        void onOutput(OutputEvent event) {
            if (event instanceof ProgressStartEvent) {
                println "[START $event.description]"
            } else if (event instanceof ProgressEvent) {
                println "[$event.status]"
            } else if (event instanceof ProgressCompleteEvent) {
                println "[END]"
            }
        }
    })

This listener can write progress messages to a file and the test fixture can load these from the file.

### Test coverage

1. Change the existing test case that covers downloading from an Ivy HTTP repository to ensure progress is logged.
2. Change the existing test case that covers uploading to an Ivy HTTP repository to ensure progress is logged.
3. Change the existing test case that covers downloading from a Maven HTTP repository to ensure progress is logged.
4. Change the existing test case that covers downloading from a custom Ivy resolver to ensure progress is logged.
5. Change the existing test case that covers uploading to a custom Ivy resolver to ensure progress is logged.
6. Change the existing test case that covers a failed download from a Maven HTTP repository to ensure progress is logged until the failure occurs.

Progress reporting for uploading to a Maven repository is currently not supported, so there is no test coverage for this.

## Correct rendering on terminals with limited width

For this story, the status bar text is simply trimmed at the right-hand edge of the terminal. This addresses the garbled output when the status bar
text wraps at the right-hand edge, but can potentially lose useful information. Later stories will address this.

To implement this, we start migrating to the [native-platform](https://github.com/adammurdoch/native-platform) library as an eventual replacement
for the jna, jna-posix and jansi libraries. The native-platform library will be used to detect the terminal and to determine the width of the
terminal, falling back to jna where native-platform is not available. Jansi will continue to be used to generate the terminal output.

1. Extract a `StatusBarFormatter` interface out of `ConsoleBackedProgressRenderer.updateText()`, that is responsible for rendering the status bar
   contents. This formatter would take the stack of `Operation` objects and format this to a String for the status bar text. The formatter
   would be injected into `ConsoleBackedProgressRenderer`.
2. Introduce a `ConsoleMetaData` interface that is responsible for determining the console width. Add an initial implementation for UNIX platforms that
   uses the `$COLUMNS` environment variable, and an implementaiton for Windows that just returns `null`. Make an instance available via the
   `NativeServices` registry.
3. Change the `StatusBarFormatter` implementation to use the `ConsoleMetaData` service, to trim the status bar text at the console width. If the console
   width is not known, then do not trim the status bar text.
4. Add the [native-platform](https://github.com/adammurdoch/native-platform) library as a dependency for the native project. Change
   `NativeServices.initialize()` to initialize the native-plaform `Native` class.
5. Add a `ConsoleDetector` implementation that is backed by native-platform's `Terminals` class. Change `NativeServices` to use this in preference to
   the existing detectors when `Terminals` is available.
6. Add a `ConsoleMetaData` implementation that is backed by native-platforms's `Terminal` class. Change `NativeServices` to use this in preference to the
   existing implementations when `Terminal` is available.

### Test coverage

Manual testing:
* Windows. Check console is detected and status bar is trimmed when running under Windows command prompt. Check that console is not detected when
  running under the Cygwin terminal (this is not supported yet).
* Linux. Check terminal is detected and status bar is trimmed. Check that terminal is not used when not attached to a terminal (eg when piping output
  to `cat`).
* OS X. As for Linux.
* One platform currently not supported by native-platform, such as Solaris or FreeBSD.

## Allow Gradle to work when native libraries are not present

TBD

## Progress reporting when uploading to Maven repositories

TBD

# Later stories

* When the native-platform integration is stable:
  * Switch to using native-platform to generate the terminal output and remove jansi.
  * Switch to using native-platform for handling unix file permissions and remove jna-posix.
  * Switch to using native-platform for handling the native integrations used by the daemon and remove jna.
* Improve formatting of status bar text on terminal with limited width.
* Handle terminal size changes. This will require improvements to the native-platform library.
* Handle runnng under Cygwin terminal. This will require improvements to the native-platform library.

# Open issues

Migration strategy for removing the existing native integrations.

# Story: add basic notion of progress to the command line output

    * While working on very large projects I really need to know what's build progress.
    * "Loading" is especially annoying because at lifecycle log level the user may see the "Loading" message for a long time
    (e.g. the build may be perceived as hanging)
    * The intention of this story is to add small and cheap improvements to the progress messages without overdoing it.
    There are lots of things we could do to improve the progress information.

## User visible changes

The progress info in the console contains extra information:

1. Build progress based on tasks completed:

> Building 23%
> Building 45% > :someProject:someTask > Resolving configuration

2. Load progress based on projects configured:

> Loading 14%

3. Perhaps add project path informing which project is being configured:

> Loading 44% > :someProject:foo

## Coverage

* unit test coverage
* find out how this stuff is integ tested at the moment
* make sure the feature works well in parallel build, configure on demand

## Open issues / limitations

    * A much more accurate approach would be using some kind of task history to estimate remaining time.
    * In configure-on-demand mode the loading % progress will be slightly incorrect
    because we don't know how many projects will be configured. So it may happen that all projects will be loaded at 4% of progress.
    However, I think it is still useful to see what's the percentage of projects that were configured during given build run.
    Alternatively, we can drop the % progress for "Loading".
    * Inaccurate/awkward when we mix configuring and building, for example, when you use the output of a project to configure the other projects.
    * Problematic when in future we start mixing configuration and execution in a parallel build.