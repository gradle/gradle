# Execution engine

The execution engine is the main component of the execution platform.
Its purpose is to produce the outputs of _units of work_ efficiently and reliably in a concurrent environment.[^concurrent]
To achieve this goal, the engine utilizes a host of safeguards and optimizations.
The execution engine is not responsible for deciding for which units of work it is necessary to produce outputs for.
It is also not concerned with scheduling the executions.

[^concurrent]: This means the engine is fully thread-safe, and it ensures that parallel execution of work does not cause problems even when multiple processes are executing units of work at the same time.

Any action with well-defined inputs and outputs where safe execution or output reuse is required can be implemented using the execution engine.
Indeed, it is our aspiration in Gradle for all such work to be executed via the execution engine.

## Unit of Work

A **unit of work** in the context of the execution engine is defined by an **identifiable** **action** with a set of known **inputs**, and expected **outputs**.
The _action_ is defined by code that is a pure function with regard to its _inputs._
The _action_ produces equivalent _outputs_ for equivalent _inputs._
_Inputs_ and _outputs_ can be scalar values, or files and directories.[^output-types]
_Inputs_ are expected to be immutable from after execution of the unit starts.
_Outputs_ should only be changed by the executing _action._
When work produces files, it is executed in a **workspace** directory only accessible to the work action as a way of **sandboxing**[^task-workspace].

[^output-types]: Currently only file-like outputs are supported, but this is a historic limitation that can be lifted if needed.
[^task-workspace] Gradle tasks are currently not executed in an isolated workspace, but that is a legacy of Gradle, and should be fixed.

Examples of units of work are:

- Gradle task actions and artifact transforms
- compilation of a build scripts
- generation of dependency accessors for build scripts
- Maven goals[^maven]

[^maven]: The execution engine is not currently used to execute Maven goals directly, but there is nothing Gradle-specific about it that would prevent such use.

## Execution process

When provided with well-defined units of work, the execution engine can employ a number of safe optimizations to manifest the outputs of the work in the most efficient way.

- When work has already been executed with the same inputs in the current build, an in-memory cache is consulted for results.
  This is referred to as **identity caching**.
- When file-producing work has already been executed previously with the same inputs, and the previously produced outputs are **up-to-date**, then the execution is skipped.
  This is called **incremental build**.
- If output is not up-to-date locally, the engine tries to look up stored results in the **build cache**.
  This first looks up results in the local build cache, and if there is no hit, the remote build cache.
- If no result is found in the build cache, the work is executed.

## Identifiers

To implement safe and optimized execution, the execution engine needs to identify units of work. 
Each unit of work has two identifiers:

- Its **identity** is a locally unique identifier of the work with respect to the current execution scope (e.g. the Gradle build tree).
- Its **cache key** is a globally unique identifier to store and retrieve the outputs of the work across time and space.

### Hashing

_Hashing_ is widely used in generating identifiers.
We use cryptographic hashing to generate universally unique identifiers for units of work (currently we use the MD5 algorithm[^md5-safety][^other-crypto-hashes]).
While non-cryptographic hash algorithms like [xxHash](https://xxhash.com) or [MurmurHash](https://en.wikipedia.org/wiki/MurmurHash) would be significantly faster to generate hashes, they don't offer the astronomical collision-resistance we need, and thus cannot be used.

[^md5-safety]: MD5 has long been compromised from a security point of view, but our goal is not to protect against malicious intent.
To avoid accidental collisions MD5 is still plenty strong.
We use it because it is a very fast and universally available algorithm on the JVM.
It also requires only 16 bytes per hash, which is significantly less than SHA1 (20 bytes) or SHA256 (32 bytes), which helps preserve memory.

[^other-crypto-hashes]: There are other cryptographic hashes we could use, and making the hashing configurable makes sense, too.
The [BLAKE family](https://en.wikipedia.org/wiki/BLAKE_(hash_function)) of cryptographic hash functions look promising for performance, though more research is needed to move forward here.

We use [Merkle-trees](https://en.wikipedia.org/wiki/Merkle_tree) to generate a single hash to represent complex inputs.
This basically means that we hash together hashes of components to generate a hash for the whole.

#### Input normalization

For a unit of work not all inputs are relevant, and we can exploit this to achieve better performance.
For example, for a task performing Java compilation, line endings in the source files are irrelevant; whether the files use `\n` or `\r\n` does not influence the result of the compilation.
This means that a change in a source file's line endings does not require compilation to be re-done, and previously generated `.class` files can be assumed up-to-date.
More importantly, ignoring source file differences allows reusing cached results of compilation between Windows and other operating systems via the remote build cache.

To exploit this, the execution engine allows units of work to define how their inputs should be **normalized**.
Besides line-ending normalization for source files, Java compilation can also declare to compilation classpath to be normalized via _ABI extraction._
This means that input hashes are not going to be calculated based on raw file contents of the compilation classpath, but instead the ABI of the classpath is extracted and hashed.
This allows existing compilation results to be reused despite differences in dependencies, as long as the differences don't affect the ABI.

Input normalization is a key feature of the execution engine that significantly increases the chance for reuse of existing results, boosting execution performance.

## Optimizations

### Incremental build, aka up-to-date checks

TBD

### The build cache

TBD

### Execution modes

Note that this is different to _incremental build,_ which is about skipping the whole execution of an up-to-date unit of work.

Fundamentally, the execution engine supports two kinds of work: _incremental_ and _non-incremental._

**Non-incremental work** is identified by its full set of inputs.
This means that changing any of the inputs of the work essentially produces a new identity.

**Incremental work** can have some **incremental inputs** that are not part of the work's identity, and changing these non-incremental inputs does not result in changing the work's identity.
(Changing a non-incremental input _does_ change the work's identity.[^task-identity])

[^task-identity]: This is currently not true for tasks, whose identity is the task's full path. The plan is to change this and make tasks execute in workspaces similar to artifact transforms.

Incremental work is thus executed within the same workspace as long as only incremental inputs are changing.
This allows the work to reuse outputs from the previous execution and update it instead of generating all the outputs from scratch.
To facilitate this, the execution engine passes to the action the list of changes to any incremental input since the last execution in the same workspace.

#### Workspace allocation

Incremental work is assigned a **mutable workspace**, i.e. a directory on disk that is reused in subsequent builds.
Work execution in these workspaces happens under lock to avoid multiple units of work colliding.[^task-locking]

[^task-locking]: For historical reasons Gradle tasks have no such mutual exclusion feature, and two separate processes running the same task can actually collide.

In comparison, non-incremental work is assigned a temporary workspace directory on execution.
Once execution has finished (or the results have been loaded from cache), the workspace directory is atomically moved to an **immutable workspace** that is not modified further.

## Legacy: the special case of Gradle tasks

Some invariants that hold for all other units of work do not yet apply to the concept of tasks in Gradle.
This is mostly historical, and should be remedied.

### Identity

A task's identity is its full path (e.g. `:project-name:taskName`).
Crucially, this identity is independent of non-incremental inputs of the task.
For non-incremental tasks this means that when a (non-incremental) input changes, previous outputs are still presented to the action, and it is the action's responsibility to clean them up.
For incremental tasks this is not the case, as the execution engine automatically cleans up outputs when non-incremental inputs change.
The plan is to do the same for non-incremental tasks, too (or to execute them via the same immutable workspaces we do with non-incremental transforms).
However, there are some tasks that don't declare themselves as incremental towards Gradle, yet they exploit the ability to update their outputs.
The most important example of this is the Kotlin compilation tasks for Gradle.
These tasks would stop being able to operate incrementally should we simply make the change.

### No exclusive workspace

Tasks don't have their own workspace.
Instead, their file outputs can point anywhere on the file-system.
In practice, they typically already produce file outputs in a single directory or file.
The plan for the future is to make them more like artifact transforms and force them to produce outputs in a workspace, too.

### Overlapping outputs

Tasks are allowed to produce outputs in directories where other tasks are also producing outputs.
This makes it hard to decide which output file was produced by which execution.
Parallel execution is also unsafe.
For this reason execution optimizations are disabled for tasks that are detected to produce overlapping outputs.
The plan is to forbid tasks to produce overlapping outputs.

Note that for future support of Maven goals (where overlapping outputs are the norm) it is likely that some sort of support for overlapping outputs will remain in the engine.
