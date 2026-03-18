# Core Execution

Tools used to execute work in the context of a Gradle build.

## Detailed guides

* [Execution Engine](execution/README.md).
* [Snapshotting and Fingerprinting](snapshots/README.md).
* [Work Validation](Work%20Validation.md)

## Glossary of terms

The **execution engine** is used to manifest the outputs of **units of work** safely and efficiently.
It is the main method of executing work in Gradle, while parts of the engine are used in the Develocity Maven and sbt extensions.

The _execution engine_ optimizes and ensures the safety of execution by tracking the **execution state** of different _units of work_ like tasks and artifact transforms.
It is also used to execute internal work efficiently such as build script compilation and accessor-generation. 

Optimizations employed by the engine include skipping execution when outputs are **up-to-date**, or when inputs are empty, reusing results from the **build cache**, and executing work **incrementally**.

The _execution state_ of a unit is composed of its **inputs** and **outputs**.
We use the _inputs_ to calculate the work unit's **local identity** and **build cache key** by hashing inputs together.

_Capturing of the execution state_ happens via **snapshotting** and **fingerprinting** the _inputs_ and _outputs_ of the work at a specific point in time; typically before and after execution. 

**Snapshotting** an input or output means to create an immutable record of its **verbatim state** at a certain point in time.
Such **snapshots** can then be used to check for modifications against snapshots of the same thing taken at another time.

While _snapshotting_ captures the _verbatim state,_ **fingerprinting** captures the **normalized state** of an input.
**Normalization** helps ignore irrelevant information and boost cache hit rates.
It depends on the **usage** of the input: the same class files are normalized differently when used as part of a compile classpath for a compile task vs in a runtime classpath when running a test.
Outputs are never normalized.
