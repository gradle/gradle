
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

Start migrating to the [native-platform](https://github.com/adammurdoch/native-platform) library as a replacement for the jna, jna-posix and jansi
libraries. The native-platform library will be used to detect the terminal and to determine the width of the terminal, falling back to jna where not
available.

TBD

## Allow Gradle to work when native libraries are not present

TBD

## Progress reporting when uploading to Maven repositories

TBD

# Open issues

None yet.
