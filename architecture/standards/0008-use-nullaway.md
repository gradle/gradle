# ADR-0008 - Use NullAway for null checking

## Date

2025-04-16

## Context

Our codebase uses `null` extensively to represent an absence of value or optionality of the argument.
We utilize nullness annotations in Java code and somewhat rely on IDE warnings to guide us.
However, the Java code we have is not fully annotated, which causes several consequences:
* IDE warnings can be misleading
* Redundant null checks may increase binary size, hurt performance, and increase cognitive load for readers
* Missing null checks may cause NullPointerException
* Bridging with Kotlin code is polluted with platform types

While there are competing ways to represent absence (`Optional`, "Null Object" pattern, method overloads),
it is unlikely that we'll be able to remove `null` entirely.

After migrating to Java 8, we can use pluggable type checkers to ensure that our annotations are consistent, 
and there are no missing null checks.

## Decision

Use NullAway in JSpecify mode to check for null-related errors in Java code.
For gradual adoption, we enable checks project-by-project.
In order to avoid back-and-forth when refining the annotations, we only enable checks for a project if all its dependencies have checks enabled.

Improper use of `null` detected by NullAway should be considered a compilation error (not a warning, and not a separate CI job to test).

NullAway errors should not be suppressed without a justification.
It is always better to rearrange the code to avoid the error, unless there is a provable performance penalty or a significant loss of code readability.
A preferred way to suppress is an `assert` statement rather than `@SuppressWarning` annotation.

## Status

PROPOSED

## Consequences

* Classes are forced to have consistent nullability annotations.
* IDE warnings become more accurate. 
* Time has to be allocated to clean up existing projects.
* One has to write NullAway-conformant code even when hacking.
* Some patterns used to satisfy NullAway can be slightly more verbose.
* IDE warnings and NullAway errors may not be perfectly in sync.
