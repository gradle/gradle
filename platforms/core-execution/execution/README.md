# Execution Engine

> [!TIP]
> For general information on the execution platform and a glossary of the main terms in the [platform readme](../README.md).

The execution engine is the main component of the execution platform.
Its purpose is to provide a simple, unified way of declaring _units of work_ that the engine can execute, producing their outputs safely and efficiently in a concurrent environment.[^concurrent-environment]

[^concurrent-environment]: This means the engine is fully thread-safe and ensures that parallel execution of work does not cause problems, even when multiple processes are executing units of work simultaneously.

It is best to think about "execution" as a way of _manifesting_ the outputs of a given unit of work.
This can happen by executing the work, but some or all of the effort of execution can be saved via one of several optimization methods employed by the execution engine.

> [!NOTE]
> The execution engine is not responsible for deciding which units of work should be executed, nor is it concerned with scheduling executions (see the scheduler about that).

Any action with well-defined inputs and outputs, where safe execution or output reuse is required, can be implemented using the execution engine.
Indeed, it is our aspiration in Gradle for all such work to be executed via the execution engine.[^work-to-be-migrated]

[^work-to-be-migrated]: Currently several work-like entities in Gradle exist that are not executed via the execution engine.

It is also our aspiration for the execution engine to be independent of Gradle concepts.
In part this is a good way to ensure wider usability in the future.
But the primary reason for keeping the execution engine self-contained is to keep the architecture clean and the API contracts simple and understandable.

## Unit of Work

A **unit of work** in the context of the execution engine is defined by an **identifiable** **action** with a set of known **inputs** and expected **outputs**.
The _action_ is defined by code that is a pure function with regard to its _inputs_.
It produces equivalent _outputs_ for equivalent _inputs._
_Inputs_ and _outputs_ can be scalar values or files and directories.[^output-types]
_Inputs_ are expected to be immutable after the execution of the unit starts.
_Outputs_ should only be changed by the executing _action._
When work produces files, it is executed in a **workspace** directory only accessible to the work action as a means of **sandboxing**.[^task-workspace]

[^output-types]: Currently, only file-like outputs are supported, but this is a historical limitation that can be lifted if needed.

[^task-workspace]: Gradle tasks are currently not executed in an isolated workspace, but that is a legacy issue and should be addressed.

Examples of units of work include:

- Gradle task actions and artifact transforms
- Compilation of build scripts
- Generation of dependency accessors for build scripts
- Maven goals[^maven]

[^maven]: The execution engine is not currently used to execute Maven goals directly, but there is nothing Gradle-specific about it that would prevent such use.

### Identifying Work

To implement safe and optimized execution, the execution engine needs to identify units of work.
Each unit has two identifiers:

- **Identity**: A locally unique identifier of the work with respect to the current execution scope (e.g., the Gradle build tree).
  For tasks this is the task path (e.g. `:subproject:taskName`); for every other type of work it is a hash calculated from the **immutable inputs** of the work (see below).
- **Build cache Key**: A global identifier used to store and retrieve the outputs of the work across time and space.
  This is calculated using all inputs.

### Execution State

The **execution state** of a unit of work consists of:

- Its inputs:
    - Its implementation – the FQCN of the work's implementation (e.g. `org.gradle.api.tasks.compile.JavaCompile`) and the classloader hash of its classpath
    - The value snapshots of its input properties
    - The fingerprints of its file inputs
- Its outputs:
    - The file-system snapshots of its output files
    - Whether the execution was successful
    - The origins of the outputs (freshly produced or reused from a previous build)

Previous execution states are stored in the **execution history** indexed by the work's _identity._

## Execution Process

The execution engine employs a _pipeline of nested steps_ to process execution requests.
This structure is similar to filter chains are implemented in servlet applications.

Execution of a step generally follows the recipe:

1. The step receives the _unit of work_ that is being executed, and an immutable data object called the **context** that contains information gathered by previous steps.

2. The step can do some meaningful work, e.g. look things up about the _unit of work_ in some external service, alter the workspace etc.

3. It can decide to invoke the next step in the pipeline, passing the _context_ to it.
    It can pass the _context_ verbatim, or it can also attach additional data to the _context._

    The step can also decide to **short-circuit**: it does not invoke the next step, and returns early instead.

4. The step returns with a **result**: an immutable data object describing what happened.
    This can be the verbatim _result_ returned by the invoked next step.
    It can also be an _enriched_ version of it with additional data attached to it, or it can be a completely new object created by the step.

![](Execution%20Engine%20Schematic.drawio.svg)

> [!TIP]
> There is a lot of useful detail in this schematic.
> To open a zoomable version right-click on the image above and choose "Open Image in New Tab".

> [!TIP]
> Use [draw.io](https://draw.io/) to make changes to the diagram. The linked SVG file can be opened directly in draw.io. Do not edit as raw SVG.

## Optimizations

When provided with well-defined units of work, the execution engine can employ several safe optimizations to produce the outputs as efficiently as possible:

- **Identity Caching**: If work has already been executed with the same inputs in the context of the current tool invocation, an in-memory cache is consulted for results
    Identity caching is only available for transforms; see [Deferred Execution](#deferred-execution-the-special-case-of-artifact-transforms) below.
- **Up-to-Date Checks**: If the _execution state_ of the work is **up-to-date**, the execution is skipped.
- **Build Cache**: If the output is not up-to-date locally, the engine searches for stored results in the _build cache,_ checking the local cache first and then the remote cache if necessary.
- **Execution**: If no result is found in the build cache, the work is executed.

### Origin Metadata

Each produced result carries with it some **origin metadata**. This is a map of key-value pairs that describe which build created the result, how long it took, and the cache key associated with the result.
When a result is produced by the execution engine the origin metadata can be consulted to understand if the result was produced during this build, or if it was reused from a previous one.

### Up-to-Date Checks (aka Incremental Build)

> [!NOTE]
> Incremental _build_ and incremental _work_ refer to different kinds of incrementality.
> We should probably stop using incremental build as a term.

This way we can compare the state of a unit of work before executing it to its state after its previous execution.
If there are no changes to either its inputs or outputs, executing the work is not needed, and is thus skipped.

* Input value changes are detected by comparing the `ValueSnapshot`s input value properties.

* Input file changes are detected by comparing fingerprints of the input file properties.

* Output changes are detected by comparing the snapshots of the output properties.

### The Build Cache

If the execution state of the work is out-of-date, we try to load outputs from the build cache.
If we are allowed to use the cache, then first the local cache is consulted.
If there is no entry with the cache key in the local cache, then we ask the remote cache.

If there is a cache hit, we delete and unpack results to the output location.
Local state is also cleared up when a result is loaded.

If there's no cache hit, or we were not allowed to load from cache, we continue with execution.
Once execution finished, we check if we are allowed to store in the local cache.
If we are allowed, we store in the local and remote caches.

> [!NOTE]
> For tasks we have an internal mechanism that can prevent storing the results of an executed task in the build cache.
> This internal feature was added and is only used by an optimization in Develocity's predictive test selection.

### Execution Modes

When it comes to actually execution work, the execution engine supports two kinds of work: _mutable_ and _immutable._ _Mutable_ work can be executed _incrementally_ or _non-incrementally_ based on which of its inputs have changed compared to a previous execution.

- **Immutable Work** All inputs are **immutable inputs**; the complete input set defines the work.
  Examples: accessor generation and non-incremental artifact transforms.

- **Mutable Work**: Only a subset of inputs are **immutable inputs**; the rest are **mutable inputs** that may change between executions.
  Examples: tasks and incremental artifact transforms.

Work is executed in (i.e. its output is produced in) a **workspace**: a dedicated directory assigned to the work by its identity. Execution within these workspaces happens under a lock to prevent multiple units of work from colliding.[^task-workspace]

[^task-workspace]: For historical reasons, Gradle tasks do get a dedicated workspace assigned.
They also lack mutual exclusion, and two separate Gradle daemons running the same task on the same build can collide.

If an immutable input changes compared to a previous local execution, the execution engine treats the work as a new, distinct unit, and assigns a different workspace.
Changing mutable inputs does not change the mutable work's identity, and hence it will be executed in the same workspace.[^task-identity]

[^task-identity]: This is currently not true for tasks, whose identity is the task's full path.
The plan is to change this and have tasks execute in workspaces similar to artifact transforms.

#### Workspace Allocation

Mutable work is executed within the same **mutable workspace** as long as only mutable inputs are changing. (Hence the name.)

In contrast, _immutable work_ is assigned a **temporary workspace** directory upon execution.
Once execution finishes (or results are loaded from the cache), the workspace directory is atomically moved to an **immutable workspace** that is not modified further.

### Incremental Execution

For _mutable work_ we distinguish between the following _behaviors_ of mutable inputs:

- **Non-incremental** -- Any change to the property value always triggers a full rebuild of the work.
- **Incremental** (annotated with `@Incremental` in tasks and transforms) -- Changes to the property value can cause an **incremental execution** of the work where the work is responsible for updating any previous outputs.
  To facilitate this, the execution engine provides the action with a list of changes to any incremental input since the last execution in the same workspace.
- **Primary** (annotated with `@SkipWhenEmpty` in tasks) -- These are the same as _incremental_ inputs with the added feature that if all primary inputs are empty, execution of the work is skipped, and its outputs are deleted.

If non-incremental execution is chosen, the mutable workspace is first cleaned up.[^task-output-cleanup]

[^task-output-cleanup]: Once again for historical reasons this does not happen for tasks.
The main reason is some incremental tasks do not declare themselves as incremental, and removing their outputs would be a breaking change.

### Deferred Execution: The Special Case of Artifact Transforms

Work with the same identity is typically only executed once during a build.
Artifact transforms are somewhat special, as they are defined in the consuming project.
This can cause transforms with the same identity to be invoked several times, often concurrently, during the same build.
(Think of "minimize the Guava JAR" that is invoked for every subproject requiring Guava.)

To handle this use case and make sure there is no race condition the execution engine has a "deferred" path.
In this mode the execution engine will return eagerly with a `Deferrable<T>` that either holds an already cached result, or it can be completed synchronously at a later time.
When work is completed, it gets stored in the identity cache.
When execution happens via the synchronous, non-deferred way, the identity cache is ignored.

## Legacy: The Special Case of Gradle Tasks

Some invariants that apply to other units of work do not yet apply to tasks in Gradle.
This is mostly historical and should be addressed.

### Identity

A task's identity is its path in the build it is defined in (e.g., `:project-name:taskName`).
Crucially, this identity is independent of non-incremental inputs.
For non-incremental tasks, when one of their (non-incremental) inputs changes, previous outputs are still presented to the action, making it the action's responsibility to clean them up.

For incremental tasks, the execution engine automatically cleans up outputs when non-incremental inputs change.
The plan is to extend this behavior to non-incremental tasks as well (or execute them via the same immutable workspaces used for non-incremental transforms).
However, some tasks do not declare themselves as incremental yet exploit the ability to update their outputs.
A prime example is the Kotlin compilation tasks for Gradle, which would lose incremental capabilities if we simply enforced this change.

### No Exclusive Workspace

Tasks do not have their own workspace.
Instead, their file outputs can point anywhere on the file system.
In practice, they typically produce outputs in a single directory or file.
The future plan is to make tasks more like artifact transforms, requiring them to produce outputs within a workspace.

### Overlapping Outputs

Tasks are allowed to produce outputs in directories where other tasks also produce outputs.
This makes it difficult to determine which output file was produced by which execution, and makes parallel execution unsafe.
Consequently, execution optimizations are disabled for tasks detected to produce overlapping outputs.
The plan is to forbid tasks from producing overlapping outputs.

Note that for future support of Maven goals (where overlapping outputs are common), some support for overlapping outputs will likely remain in the engine.
