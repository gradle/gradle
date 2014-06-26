# Process Management

Gradle has the ability to exec external processes in a synchronous (blocking) manner through the
"project.exec(Closure c)" and "project.java.exec(Closure c)" APIs. This capability can be extended through additional
plugins which can provide mechanisms such as asynchronous (non-blocking) process execution and throttled execution (limited #
of concurrent processes).

The current process execution current relies heavily on internal APIs that shouldn't be used by plugin authors. A new set of
consistent, public APIs for processes is required.

# Use cases

- Execute non-blocking processes by executing a process and returning a ProcessHandle which can be used to identify the process
- Access identifying information such as command, env, working directory, arguments, and execution state of executed process
- Access execution critical configuration information such as 'ignoreExitValue' from process.

# Implementation plan

Provide a public API for plugins to add additional process management capabilities.

## Promote Internal APIs to be Public

Plugin author should only utilize public Gradle APIs so that the plugin is compatible across multiple versions of Gradle.
Current process control APIs are internal APIs and should not be used in a plugin. A number of these classes and interfaces
can be promoted with minimal/no impact to Gradle core.

### User visible changes

- New public ProcessHandle interface. Unused by any API in Gradle Core, but can be used by plugins to return a reference
  to a forked process.
- New public ProcessHandleState enum. Promoted from internal API, returned by ProcessHandle interface to indicate current
  process execution state.

If any plugins rely on these internal classes that are being moved/renamed, they will become incompatible with the version of
Gradle that makes these changes. The end user would see a ClassNotFoundException during a build.

### Implementation

- Define a new public API for Process interaction (org.gradle.process.ProcessHandle) using methods from ExecHandle

    interface ProcessHandle {
          /**
           * The working directory of the process.
           * @return File
           */
          File getDirectory();

          /**
           * The command to that is being executed by the process
           * @return String command
           */
          String getCommand();

          /**
           * The arguments passed to the process
           * @return String args as List
           */
          List<String> getArguments();

          /**
           * The configured environment for the process.
           * @return Map of key/value String pairs for environment
           */
          Map<String, String> getEnvironment();

          /**
           * The current execution state of the process.
           * @return execution state
           */
          ProcessHandleState getState();

          /**
           * Waits for the process to finish.
           *
           * @return result
           */
          ExecResult waitForFinish();

          /**
           * Returns if the exit value of the process should be ignored or not.
           * @return true if the exit value should be ignored. False otherwise.
           */
          boolean isIgnoreExitValue();

          /**
           * Calls process.destroy() and completes the execution
           */
          ExecResult abort();
    }

- Modify org.gradle.process.internal.ExecHandle to extend org.gradle.process.ProcessHandle, update method signatures
  to match
- Promote org.gradle.process.internal.ExecHandleState to org.gradle.process.ProcessHandleState
- Add `private boolean ignoreExitValue` to org.gradle.process.internal.DefaultExecHandle.
- Add `boolean isIgnoreExitValue()` method to org.gradle.process.internal.DefaultExecHandle.
- Update constructor in org.gradle.process.internal.DefaultExecHandle to have `boolean ignoreExitValue` argument
- Update org.gradle.process.internal.AbstractExecHandleBuilder.build() to pass `ignoreExitValue` to `new
  DefaultExecHandle(...)`
- Promote org.gradle.internal.exceptions.AbstractMultiCauseException to a public package (org.gradle.api?). This can allow
  for a multi cause exception to be used when waiting on multiple external processes in a build.
- Update org.gradle.process.internal.ExecHandleRunner.abortProcess() to call `completed(process.exitValue());` after
  calling `process.destroy();`
- Modify org.gradle.process.internal.DefaultExecHandle.abort() to `return this.getState();`
- Rename org.gradle.process.internal.DefaultExecHandle to org.gradle.process.internal.DefaultProcessHandle

### Test coverage

- All current Gradle core tests for process execution should pass with minor changes to class name and packages for some things.
- Add a test to ensure that `DefaultExecHandle` is being initialized with the correct `ignoreExitValue` from the configuration
  closure.

# Open issues
