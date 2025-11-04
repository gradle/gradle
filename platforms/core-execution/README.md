# Core Execution

Tools used to execute work in the context of a Gradle build.

## Detailed guides

* [Execution Engine](execution/README.md).
* [Snapshotting and fingerprinting](snapshots/README.md).

## Glossary of terms

The **execution engine** is used to manifest the outputs of **units of work** efficiently. It is used in Gradle, and in the Develocity Maven and sbt extensions.

The _execution engine_ optimizes execution by tracking the **execution state** of different _units of work_ like tasks and artifact transforms. Optimizations include reusing results from the **build cache**, skipping execution when outputs are **up-to-date**, or when inputs are empty, and executing work **incrementally**.

The most important thing we use for these optimizations is the state of a unit’s **inputs** and **outputs**. Some examples are:

- task properties marked as `@Input` are **scalar inputs** (or _non-file inputs_),
- task properties marked with `@InputFile`, `@InputFiles` and `@InputDirectory` **file inputs**[^file-inputs] 
- a task's output property marked as `@OutputFile` or `@OutputDirectory`.
- the `@InputArtifact` of an artifact transform is another example of a _file input_

[^file-inputs]: In general a _file input_ can be a single file, a directory, or even a collection of files and directories.

Inputs can be **input files** and non-file **input values**. Outputs currently can only be files[^non-file-outputs].

[^non-file-outputs]: The reason for only file outputs is purely historical, and support for non-file output values can be added if needed.

_Capturing of the execution state_ happens via **snapshotting** and **fingerprinting** the _inputs_ and _outputs_ of the work at a specific point in time; typically before and after execution. 

**Snapshotting** an input or output means to create an immutable record of its **verbatim state** at a certain point in time. Such **snapshots** can then be used to check for modifications against snapshots of the same thing taken at another time.

While _snapshotting_ captures the _verbatim state,_ **fingerprinting** captures the **normalized state** of an input[^scalar-normalization].
**Normalization** helps ignore irrelevant information and boost cache hit ratios.
It depends on the **usage** of the input: the same class files are normalized differently when used as part of a compile classpath for a compile task vs in a runtime classpath when running a test.
Outputs are never normalized.

[^scalar-normalization]: Normalization is currently only supported for file inputs; scalar inputs are not normalized.
