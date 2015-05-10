
# Java library consumes local Java library

- Same project
- Other project
- Not external
- Not legacy plugins
- Consumes API dependencies at compile time
- Consumes runtime dependencies at runtime
- Select jar binary or classes binary with compatible platform, fail if not exactly one
- Need an API to query the various classpaths.
- Need to be able to configure the resolution strategy for each usage.
- Declare dependencies at component, source set and binary level
- Reporting

# Custom Java based component local library

- Java source only
- Custom component consumes Java library
- Custom component consumes custom library
- Java library consumes custom library
- Reporting

# Java library consumes external Java library

- Reporting

# Legacy JVM language plugins declare and consume JVM library

- JVM component can consume legacy JVM project
- Legacy JVM project can consume JVM library
- Change dependency reporting to present project as a JVM component

# Domain specific usages

- Custom component declares additional usages and associated dependencies.
- Custom binary provides additional usages
- Reporting
