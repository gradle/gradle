# ADR-0008 - Use NullAway for null checking

## Date

2025-08-06

## Context

Our codebase uses `null` extensively to represent an absence of value or optionality of the argument.
We utilize nullness annotations in Java code and somewhat rely on IDE warnings to guide us.
However, the Java code we have is not fully annotated, which causes several consequences:
* IDE warnings can be misleading
* Redundant null checks may increase binary size, hurt performance, and increase cognitive load for readers
* Missing null checks may cause NullPointerException
* Bridging with Kotlin code is polluted with platform types
* Some public APIs have incorrect nullability annotations (both overly restrictive and overly permissive)

While there are competing ways to represent absence (`Optional`, "Null Object" pattern, method overloads),
it is unlikely that we'll be able to remove `null` entirely.

After migrating to Java 8, we can use pluggable type checkers to ensure that our annotations are consistent, 
and there are no missing null checks.

## Decision

Use "NullAway" in JSpecify mode to check for null-related errors in Java code.

Consider improper uses of `null` detected by NullAway a compilation error (not a warning, and not a separate CI job to test).

Do not suppress NullAway errors without a justification.
Prefer rearranging the code to avoid the error, unless there is a provable performance penalty or a significant loss of code readability.
Use `Objects.requireNonNull` statement or `Preconditions` rather than `assert` or `@SuppressWarning` annotation to suppress if possible.

For gradual adoption, enable checks project-by-project.
Do not rely on `@NullMarked` annotations.
Only enable checks for a project if all its dependencies have checks enabled, in order to avoid back-and-forth when refining the annotations.

Avoid writing the so-called `PolyNull` or `ParametricNull` methods (where nullability of the result depends on the nullability of the type argument).
One particular example is when a [method returns `null` if and only if its argument is `null`](https://github.com/gradle/gradle/blob/674b8430b024f03cae24f1e4dd6dbaa78b557dae/platforms/core-runtime/base-services/src/main/java/org/gradle/util/internal/TextUtil.java#L163).
Prefer either:
  * Provide only non-nullable version and move the `null` check to the call site (if the number of nullable callsites is low).
  * Provide two overloads with a different names (as Java doesn't support overloading on nullability). See 
      [`Cast.cast`](https://github.com/gradle/gradle/blob/674b8430b024f03cae24f1e4dd6dbaa78b557dae/platforms/core-runtime/stdlib-java-extensions/src/main/java/org/gradle/internal/Cast.java#L37)
      and
      [`Cast.castNullable`](https://github.com/gradle/gradle/blob/674b8430b024f03cae24f1e4dd6dbaa78b557dae/platforms/core-runtime/stdlib-java-extensions/src/main/java/org/gradle/internal/Cast.java#L62).
  * When annotating existing internal code, use `org.jetbrains.annotations.Contract`, e.g. `@Contract(null -> null; !null -> !null)`.
      When doing so, still annotate nullable parameters and the return type as `@Nullable`.
  * Do not use `@Contract` for public APIs.
  * For polynull public APIs, the solution has to be decided on a case-by-case basis.

Do not remove `null` checks on public API boundaries, even if the annotations (or rather lack of them) suggest this.
Not all client code is compiled with NullAway.
Do not make a parameter `@Nullable` just to check and throw a `NullPointerException` when it is `null`. 

## Status

ACCEPTED

## Consequences

* Classes are forced to have consistent nullability annotations.
* IDE warnings become more accurate, reducing warnings fatigue.
* Public API nullability annotations become closer to reality.
* Java compilation of Gradle code suffers from small performance penalty (up to 10%).
* Time has to be allocated to clean up existing projects.
* One has to write NullAway-conformant code even when hacking.
* Some patterns used to satisfy NullAway can be slightly more verbose.
* IDE warnings and NullAway errors may not be perfectly in sync.
