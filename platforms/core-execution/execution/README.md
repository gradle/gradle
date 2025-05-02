# Execution Engine

The execution engine is the main component of the execution platform.
Its purpose is to provide a simple, unified way of declaring _units of work_ that the engine can execute, producing their outputs safely and efficiently in a concurrent environment.[^concurrent-environment]
To achieve this goal, the engine utilizes a host of safeguards and optimizations.
Notably, the execution engine is not responsible for deciding which units of work require output production, nor is it concerned with scheduling executions (see the scheduler about that).

[^concurrent-environment]: This means the engine is fully thread-safe and ensures that parallel execution of work does not cause problems, even when multiple processes are executing units of work simultaneously.

Any action with well-defined inputs and outputs, where safe execution or output reuse is required, can be implemented using the execution engine.
Indeed, it is our aspiration in Gradle for all such work to be executed via the execution engine.
At the time of writing several work-like entities in Gradle exist that are not executed via the execution engine.

It is also our aspiration for the execution engine to be independent of Gradle concepts.
In part this is a good way to ensure wider usability in the future.
But the primary reason for keeping the execution engine self-contained is to keep the architecture clean and the API contracts simple and understandable.

## Unit of Work

A **unit of work** in the context of the execution engine is defined by an **identifiable** **action** with a set of known **inputs** and expected **outputs**.
The _action_ is defined by code that is a pure function with regard to its _inputs_.
It produces equivalent _outputs_ for equivalent _inputs_.
_Inputs_ and _outputs_ can be scalar values or files and directories.[^output-types]
_Inputs_ are expected to be immutable after the execution of the unit starts.
_Outputs_ should only be changed by the executing _action_.
When work produces files, it is executed in a **workspace** directory only accessible to the work action as a means of **sandboxing**.[^task-workspace]

[^output-types]: Currently, only file-like outputs are supported, but this is a historical limitation that can be lifted if needed.

[^task-workspace]: Gradle tasks are currently not executed in an isolated workspace, but that is a legacy issue and should be addressed.

Examples of units of work include:

- Gradle task actions and artifact transforms
- Compilation of build scripts
- Generation of dependency accessors for build scripts
- Maven goals[^maven]

[^maven]: The execution engine is not currently used to execute Maven goals directly, but there is nothing Gradle-specific about it that would prevent such use.

## Execution Process

When provided with well-defined units of work, the execution engine can employ several safe optimizations to produce the outputs as efficiently as possible:

- **Identity Caching**: If work has already been executed with the same inputs in the context of the current tool invocation, an in-memory cache is consulted for results.
- **Incremental Build**: If file-producing work has been executed previously with the same inputs and the outputs are **up-to-date**, the execution is skipped.
- **Build Cache Lookup**: If the output is not up-to-date locally, the engine searches for stored results in the **build cache**, checking the local cache first and then the remote cache if necessary.
- **Execution**: If no result is found in the build cache, the work is executed.

## Identifiers

To implement safe and optimized execution, the execution engine needs to identify units of work.
Each unit has two identifiers:

- **Identity**: A locally unique identifier of the work with respect to the current execution scope (e.g., the Gradle build tree).
- **Cache Key**: A globally unique identifier used to store and retrieve the outputs of the work across time and space.

### Hashing

_Hashing_ is widely used in generating these identifiers.
We use cryptographic hashing to generate universally unique identifiers for units of work (currently using the MD5 algorithm[^md5-safety][^other-crypto-hashes]).
While non-cryptographic hash algorithms like [xxHash](https://xxhash.com) or [MurmurHash](https://en.wikipedia.org/wiki/MurmurHash) are significantly faster, they lack the astronomical collision resistance we require.

[^md5-safety]: MD5 has long been compromised from a security standpoint, but our goal is not to protect against malicious intent.
To avoid accidental collisions, MD5 is still sufficiently strong.
We use it because it is very fast and universally available on the JVM, and it requires only 16 bytes per hash, which helps conserve memory compared to SHA1 (20 bytes) or SHA256 (32 bytes).

[^other-crypto-hashes]: Other cryptographic hashes could be used, and making the hashing configurable is sensible.
The [BLAKE family](https://en.wikipedia.org/wiki/BLAKE_(hash_function)) of cryptographic hash functions looks promising for performance, though more research is needed.

We use [Merkle trees](https://en.wikipedia.org/wiki/Merkle_tree) to generate a single hash representing complex inputs.
This involves hashing together hashes of individual components to create a hash for the whole.

#### Input Normalization

Not all inputs are relevant for a unit of work, and we can exploit this for better performance.
For example, in a Java compilation task, line endings in source files are irrelevant; whether files use `\n` or `\r\n` does not affect the compilation result.
Therefore, changes in line endings do not necessitate recompilation, and previously generated `.class` files can be considered up-to-date.
Ignoring such differences also allows reusing cached compilation results across different operating systems via the remote build cache.

To leverage this, the execution engine allows units of work to define how their inputs should be **normalized**.
Besides line-ending normalization for source files, Java compilation can also declare the compilation classpath to be normalized via _ABI extraction_.
This means input hashes are calculated based on the extracted ABI of the classpath rather than raw file contents.
This allows reuse of existing compilation results despite differences in dependencies, as long as those differences don't affect the ABI.

Input normalization is a key feature that significantly increases the chance of reusing existing results, boosting execution performance.

## Optimizations

### Incremental Build (Up-to-Date Checks)

_TBD_

### The Build Cache

_TBD_

### Execution Modes

Note that this differs from _incremental build,_ which involves skipping the entire execution of an up-to-date unit of work.

Fundamentally, the execution engine supports two kinds of work: _incremental_ and _non-incremental_.

- **Non-Incremental Work**: Identified by its full set of inputs.
  Changing any input effectively creates a new identity.

- **Incremental Work**: Has some **incremental inputs** that are not part of the work's identity.
  Changing these incremental inputs does not change the work's identity.
  However, changing a non-incremental input _does_ change the identity.[^task-identity]

[^task-identity]: This is currently not true for tasks, whose identity is the task's full path.
The plan is to change this and have tasks execute in workspaces similar to artifact transforms.

It is the responsibility of work to declare whether or not it is incremental.
Any type of work can be executed incrementally by the engine, though currently it is only used by some tasks and artifact transforms.
Other types of work like accessor generation never relies on incremental execution.

Incremental work is executed within the same workspace as long as only incremental inputs are changing.
This allows the work to reuse outputs from previous executions and update them instead of regenerating everything from scratch.
To facilitate this, the execution engine provides the action with a list of changes to any incremental input since the last execution in the same workspace.

#### Workspace Allocation

Incremental work is assigned a **mutable workspace**, a directory on disk reused in subsequent builds.
Execution within these workspaces happens under a lock to prevent multiple units of work from colliding.[^task-locking]

[^task-locking]: For historical reasons, Gradle tasks lack such mutual exclusion, and two separate Gradle daemons running the same task can collide.

In contrast, non-incremental work is assigned a temporary workspace directory upon execution.
Once execution finishes (or results are loaded from the cache), the workspace directory is atomically moved to an **immutable workspace** that is not modified further.

## Legacy: The Special Case of Gradle Tasks

Some invariants that apply to other units of work do not yet apply to tasks in Gradle.
This is mostly historical and should be addressed.

### Identity

A task's identity is its full path (e.g., `:project-name:taskName`).
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
