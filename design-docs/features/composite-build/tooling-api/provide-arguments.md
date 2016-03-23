## User can specify command-line arguments that apply to all participants in a composite

### Overview

ModelBuilder and BuildLauncher API through the composite interface should support

    LongRunningOperation.withArguments

This allows a user to specify command-line arguments (like project properties).

### API

The existing `ModelBuilder` and `BuildLauncher` interfaces extends from `ConfigurableLauncher` interface (which extends `LongRunningOperation`).

### Implementation notes

- A user will configure a "composite level" `ModelBuilder` or `BuildLauncher` using the existing interfaces.
- In the coordinator, when configuring the "build level" `ModelBuilder` or `BuildLauncher` (both `AbstractLongRunningOperation`), a new internal method will take a given "composite level" `ConsumerOperationParameters` and copy the arguments to the build level (via `ConsumerOperationParameters.Builder`).

### Test coverage

- For the following composite configurations
    - A single-project, single-participant
    - A multi-project, single-participant
    - A single-project/multi-project, multi-participant
- Test should exercise model requests and task execution.
- Tests exercising model requests will use project properties to influence the model (e.g., looking at the model that contains the description of the Gradle project).
- Tests exercising task execution will use project properties to pass/fail the build (i.e., a task will fail execution if a property isn't set)

### Out of Scope

- Detecting non-sensical arguments early.
- Handling per-participant arguments (or the interaction between composite level and build level arguments)

## User can specify JavaHome/JVM arguments that apply to all participants in a composite

### Overview

ModelBuilder and BuildLauncher API through the composite interface should support

    LongRunningOperation.setJavaHome
    LongRunningOperation.setJvmArguments

This allows a user to specify JVM arguments (like system properties) and a separate Java Home.

### API

The existing `ModelBuilder` and `BuildLauncher` interfaces extends from `ConfigurableLauncher` interface (which extends `LongRunningOperation`).

### Implementation notes

- A user will configure a "composite level" `ModelBuilder` or `BuildLauncher` using the existing interfaces.
- In the coordinator, when configuring the "build level" `ModelBuilder` or `BuildLauncher` (both `AbstractLongRunningOperation`), a new internal method will take a given "composite level" `ConsumerOperationParameters` and copy java home and JVM arguments to the build level (via `ConsumerOperationParameters.Builder`).
- Tests require a forked executer

### Test coverage

- For the following composite configurations
    - A single-project, single-participant
    - A multi-project, single-participant
    - A single-project/multi-project, multi-participant
- Test should exercise model requests and task execution.
- Tests exercising model requests will use system properties to influence the model (e.g., looking at the model that contains the description of the Gradle project).
- Tests exercising task execution will use system properties to pass/fail the build (i.e., a task will fail execution if a property isn't set)
- Maybe check BuildEnvironment?

### Out of Scope

- Failing early if JVM arguments or java home are invalid (i.e., we'll try every participant)
