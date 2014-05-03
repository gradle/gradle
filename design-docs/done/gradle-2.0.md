## Remove support for the Gradle Open API implementation (DONE)

Now that we have reasonable tooling support via the tooling API, remove the Open API.

* Implement a stub to fail with a reasonable error message when attempting to use Gradle from the Open API.
* Add integration test coverage that using the Open API fails with a reasonable error message.

Note that the `openAPI` project must still remain, so that the stubs fail in the appropriate way when used by Open API clients.
This will be removed in Gradle 3.0.

## Remove the `GradleLauncher` API (DONE)

The public API for launching Gradle is now the tooling API. The `GradleBuild` task can also be used.

* Replace internal usages of the static `GradleLauncher` methods.
* Move the `GradleLauncher` type from the public API to an internal package.

## Remove usages of JNA and JNA-Posix (DONE)

Replace all usages of JNA and JNA-Posix with native-platform. Currently, this means that console support and
UNIX file permissions with JVMs earlier than Java 7 will not be supported on the following platforms:

* Linux-ia64
* Solaris-x86, -amd64, -sparc, -sparcv9

## Misc API tidy-ups (DONE)

* Remove unused `IllegalOperationAtExecutionTimeException`.
* Remove unused `AntJavadoc`.
