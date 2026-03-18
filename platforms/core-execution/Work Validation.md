# Work Validation

> [!WARNING]
> Find a better place for this document.
> It doesn't belong to execution directly, more to plugin-development or build logic creation.

While our goal is to create APIs that are easy to use correctly, invalid use is always possible.
**Work validation** is the process of checking if units of work (tasks, transforms) are defined correctly in build logic.

> [!NOTE]
> _Validation_ is specifically concerned with checking how work is _defined,_ and if its inputs and outputs are properly wired up.
> Runtime errors happening during execution are not handled here.

## Forms of Validation

Validation can happen in two forms:

- **Static validation** happens during plugin development,[^build-logic-development] and is conducted using the compiled `.class` files of the build logic accessed via ASM.
    Static validation is done via the `ValidatePlugins` task.

[^build-logic-development]: We refer to any sort of build logic creation as "plugin development."

- **Runtime validation** as the name suggests happens during runtime, while a unit of work is executing.
    This method uses information from reflection.
    Runtime validation is invoked in the execution engine in `ValidateStep`.

In both cases we are interested in checking if the work and its properties have the right annotations and method signatures, but the source and detail of the available data is different.

The main difference between the two is how types of nested properties are validated.
Consider the following property of a task:

```java
@Nested
Property<AnInterfaceType> getValue();
```

Static validation only understands that the `value` property will be _some_ implementation of `AnInterfaceType`.
So static validation can only validate `AnInterfaceType` itself.
During runtime, however, we know the _actual_ type of the object stored in the `value` property (e.g. `SomeImplementationType`).
This type will implement `AnInterfaceType`, but it might define more properties of its own that also need to be validated.
Therefore, runtime validation can produce warnings and errors for work types that have passed static validation.

> [!NOTE]
> Runtime validation can also validate properties registered via the runtime API for tasks.

## What is Being Validated

> [!NOTE]
> For historical reason we currently only have validation implemented for tasks, though all user-defined work types could benefit from validation.
> Runtime task validation is implemented in `TaskExecution.validate()`.
