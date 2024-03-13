# ADR-0003 - Avoid introducing Groovy types to public API

## Date

2024-01-12

## Context

Gradle's public API requires equal access from all JVM-based languages.
Kotlin, Groovy, Java, and other JVM-based languages should be able to use the Gradle API without relying on another language's standard library.

Historically, Gradle has shipped with some Groovy types in very prominent APIs.
This required the Kotlin DSL to add special integration to work with Groovy closures.
This has also forced plugins written in languages other than Groovy to use Groovy types for some APIs.

When the Kotlin DSL was introduced, we made an effort to add non-Groovy equivalents for all APIs.
This has been mostly done, but there remain a few holdouts (fixing these is out of scope).

To keep the Groovy DSL ergonomic, we generate methods as necessary from the non-Groovy equivalents.

Doing this provides the following specific benefits:
- **Reduce the API surface** - We no longer need to maintain two methods.
- **Consistency** - All languages have consistent access to the same APIs and ergonomics in the DSL.
- **Reduce the size of the Gradle distribution** - We no longer need to carry multiple standard libraries for different languages.

## Decision

We do not introduce new public API methods that include Groovy types in their signatures.
Existing Groovy methods will not be removed immediately.

## Status

ACCEPTED

## Consequences

* If we would have used `Closure`, we must instead use `Action<T>`, `Spec<T>`, `Callable<T>`, or `Transformer<OUT, IN>`.
* We need to ensure all APIs that are exposed in the Groovy DSL go through runtime decoration.
Runtime decoration mixes in methods that use Groovy Closures to maintain consistent behavior in Groovy DSL.
Decoration is performed on objects instantiated by `ObjectFactory`.
