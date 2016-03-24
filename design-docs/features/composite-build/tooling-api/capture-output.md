## User can capture stdout/stderr from a composite

### Overview

`ModelBuilder` and `BuildLauncher` API through the composite interface should support

    LongRunningOperation.setStandardOutput
    LongRunningOperation.setStandardError
    LongRunningOperation.setStandardInput
    LongRunningOperation.setColorOutput

This allows a user to capture standard output or standard error with their own OutputStream.

This also allows a user to enable/disable colorized output.

### API

The existing `ModelBuilder` and `BuildLauncher` interfaces extends from `ConfigurableLauncher` interface (which extends `LongRunningOperation`).

### Implementation notes

- A user will configure a "composite level" `ModelBuilder` or `BuildLauncher` using the existing interfaces.
- In the coordinator, when configuring the "build level" `ModelBuilder` or `BuildLauncher` (both `AbstractLongRunningOperation`), a new internal method will take a given "composite level" `ConsumerOperationParameters` and copy the standard output, standard error and color output parameters to the build level (via `ConsumerOperationParameters.Builder`).

### Test coverage

- For the following composite configurations
    - A single-project, single-participant
    - A multi-project, single-participant
    - A single-project/multi-project, multi-participant
- Test should exercise model requests and task execution.
- Build should log a message to stdout and stderr that identifies the project.  Test verifies that stdout and stderr contain the appropriate messages.  The logging can be a part of configuration (we do not need to log as part of task execution).
- A separate test should force color output and check that color escape codes are found in the output

### Out of Scope

- Parallel participant execution
