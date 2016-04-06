## Configurable isolated Ant tasks for provided Gradle tasks

### Overview

- Currently, Ant tasks run in the Gradle process. This can cause problems with Ant tasks that require particular settings (e.g., lots of memory) or are not parallel safe.
- Users are unable to configure these Ant tasks separately, so their only choice is to modify the Gradle process's settings
- A worker process, in a similar way to Gradle's compiler daemons, would provide the separation between Gradle and Ant tasks.
- Using a worker process should be transparent to existing build scripts
- Initial implementation will follow the compiler daemon model of spawning a separate worker for each unique "tool" classpath
- Using the worker process should be the default at the end, but an option to go back to the old behavior should be available.

### Out Of Scope

- Wrapping _any_ Ant task and running it in the worker process
- Reusing a single worker for any Ant task
- Providing public API for plugin authors to use the worker process
- Providing a way to use a different version of Ant than the one bundled with Gradle

### API

Initially, we will not expose a public API for arbitrarily sending Ant tasks to a worker process.

For Gradle tasks that are wrapping Ant tasks (e.g., Checkstyle), we will provide fork options.

### Implementation notes

- Create a `DaemonAntCompiler` that works similar to the existing Daemon compiler classes but works with a generic `AntSpec` and delegates to a `Compiler<T extends AntSpec>`
- Create a `AntSpec extends CompilerSpec`. Has "ant classpath" and "shared packages" property
- Create a `CheckstyleSpec extends AntSpec`. Spec should contain anything needed to configure the Ant task in the worker process
- Create a `CheckstyleCompiler extends Compiler<CheckstyleSpec>`
- When configuring the daemon forking options, use "ant classpath" and "shared packages" from Spec
- In `CheckstyleCompiler`, create AntBuilder and execute Ant task with it. Ant classpath should already be available
- AntBuilder does not need to do Ant classpath caching in the worker process

### Test Coverage

- Existing coverage should work for forking and non-forking modes

### Documentation

- Need to provide documentation about fork options in the tasks that change

### Open issues

- modeling "Ant" as the tool chain or the individual tools?
- Do we need to rename some of the `Compiler` classes to be less compiler specific? 
- Which subproject should this new work go into? Maybe a new subproject
- Do we need some sort of "services" injector on the worker process side (see the way we build services in ZincScalaCompiler)?