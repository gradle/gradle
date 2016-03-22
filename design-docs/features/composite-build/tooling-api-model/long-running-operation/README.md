## User can capture stdout/stderr from a composite

### Overview

ModelBuilder and BuildLauncher API through the composite interface should support 

    LongRunningOperation.setStandardOutput
    LongRunningOperation.setStandardError
    LongRunningOperation.setColorOutput

This allows a user to capture standard output or standard error with their own OutputStream.

This also allows a user to disable colorized output.

### API

The existing `ModelBuilder` and `BuildLauncher` interfaces extends from `ConfigurableLauncher` interface (which extends `LongRunningOperation`).  

### Implementation notes

- TBD

### Test coverage

- TD

### Out of Scope

- LongRunningOperation.setStandardInput

## User can specify command-line arguments that apply to all participants in a composite

### Overview

ModelBuilder and BuildLauncher API through the composite interface should support 

    LongRunningOperation.withArguments

This allows a user to specify command-line arguments (like project properties).

### API

The existing `ModelBuilder` and `BuildLauncher` interfaces extends from `ConfigurableLauncher` interface (which extends `LongRunningOperation`).  

### Implementation notes

- TBD

### Test coverage

- TBD

### Out of Scope

- Detecting non-sensical arguments early.

## User can specify JavaHome/JVM arguments that apply to all participants in a composite

### Overview

ModelBuilder and BuildLauncher API through the composite interface should support 

    LongRunningOperation.setJavaHome
    LongRunningOperation.setJvmArguments

This allows a user to specify JVM arguments (like system properties) and a separate Java Home.

### API

The existing `ModelBuilder` and `BuildLauncher` interfaces extends from `ConfigurableLauncher` interface (which extends `LongRunningOperation`).  

### Implementation notes

- TBD

### Test coverage

- TBD

### Out of Scope

- TBD


