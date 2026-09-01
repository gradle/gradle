# ADR-0013 - Requirements for public API classes

## Status

- PROPOSED on 2026-08-18

## Context

Gradle's public API is a contract.
Build authors and plugin authors — writing in Java, Kotlin, Groovy, and other JVM languages — depend on it being stable, consistent, usable, and well documented.
Once a type or member is published as public API, it is expensive to change, so these classes should be held to a high standard.

There is no single document a contributor or reviewer can consult to answer "what does a class have to do to be part of the public API?".
This ADR consolidates those requirements into one checklist.
It does not propose new rules; it collects the existing ones and records the expectations that all public API classes should meet.

Throughout this ADR, "public API" means a type that resides in one of the packages designated as public API — see `PublicApi.kt` and `PublicKotlinDslApi.kt` in `build-logic-commons/basics/src/main/kotlin/gradlebuild/basics/` — together with its `public` and `protected` members.

The keywords MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as defined in [RFC 2119](https://datatracker.ietf.org/doc/html/rfc2119).

## Decision

A public API class MUST satisfy all of the following requirements.
They fall into two groups: requirements enforced automatically by architecture tests (a violation fails the build), and requirements enforced during API review (a violation blocks the PR).

### Structural requirements

1. **Signatures reference only approved types.**
   Public API methods MUST NOT reference Gradle internal types as parameter or return types.
   The permitted types are other Gradle public API types, primitives, and a curated allow-list of JDK, Kotlin, and slf4j types.
   The authoritative allow-list is defined in `testing/architecture-test/src/test/java/org/gradle/architecture/test/PermittedPublicApiTypes.java`.
   Notably, `java.util.function` types are intentionally excluded — Gradle exposes its own functional types such as `org.gradle.api.Action`, `org.gradle.api.specs.Spec`, and `org.gradle.api.Transformer` instead.

2. **No Groovy types; Closure-taking methods have a Gradle-type equivalent.**
   Per [ADR-0003](0003-avoid-introducing-Groovy-types-to-public-api.md), new public API MUST NOT introduce Groovy types.
   Where a method accepts a `groovy.lang.Closure` (for Groovy DSL ergonomics), there MUST be an equivalent overload that takes a Gradle type — `Action<T>`, `Spec<T>`, or `Transformer<OUT, IN>`.

3. **Public tasks and plugins are abstract.**
   A public API class that is assignable to `Task` or `Plugin` MUST be declared `abstract`, so that Gradle can decorate and instantiate it via managed types and the `ObjectFactory`.

4. **No internal or Groovy supertypes.**
   A public API class MUST NOT have a direct superclass or interface that is a Gradle internal type or a Groovy type.
   Leaking an internal supertype leaks internal API onto the public surface.

5. **`NamedDomainObjectCollection` implementations override `named(Spec)`.**
   A public type implementing `NamedDomainObjectCollection` MUST override `named(Spec)` so the collection contract is honored.

### Explicit nullability

Per [ADR-0008](0008-use-nullaway.md), nullability MUST be explicit and expressed with JSpecify:

6. **JSpecify `@Nullable`, and nothing else.**
   Nullable return types and parameters MUST be annotated with `org.jspecify.annotations.Nullable`.
   A method that is annotated with some *other* `Nullable` annotation (e.g. JetBrains, JSR-305) rather than the JSpecify annotation is a violation.

7. **`@NullMarked` packages.**
   Public API classes MUST be null-marked, preferably by annotating the package via `package-info.java` rather than the class.
   Public API methods MUST NOT be `@NullUnmarked`, and classes MUST NOT be redundantly `@NullMarked` when their package already is.

8. **No `@Contract`.**
   The JetBrains `@Contract` annotation MUST NOT be used on public API code units.
   Per [ADR-0008](0008-use-nullaway.md), `@Contract` is reserved for annotating existing internal code, and the handling of polymorphic-null (`PolyNull`) public APIs is decided on a case-by-case basis.

### Lazy configuration and observability (enforced during API review)

These requirements are not backed by an architecture test; they are enforced during API review.

9. New configuration on tasks, extensions, and domain objects SHOULD use the Provider API (`Property<T>` / `Provider<T>`) per [ADR-0006](0006-use-of-provider-apis-in-gradle.md), and every public setter MUST have a corresponding observer per [ADR-0011](0011-api-observability.md).

### Documentation requirements (enforced during API review)

Reviewers MUST treat a documentation gap as a blocking issue.

10. **Javadoc on every public element.**
    Every public API type and every public (or `protected`) member MUST have a Javadoc.
    The Javadoc MUST describe the element's purpose and behavior — not merely restate its name — and MUST document parameters (`@param`), return values (`@return`), and thrown exceptions (`@throws`) where they exist.
    Nullability and other contract details that affect callers MUST be stated.

11. **Samples where appropriate.**
    Public API Javadoc SHOULD include usage samples or code snippets where an example materially helps a build or plugin author understand how to use the API correctly.
    This applies especially to entry-point types, DSL-configurable types, and any API whose correct use is not obvious from its signature alone.

12. **`@since` on every element.**
    Every public API type and member MUST carry an explicit `@since` tag recording the Gradle version in which it was introduced, per [ADR-0012](0012-since-on-public-api.md).

13. **American English.**
    All documentation and identifiers MUST use American English spelling, per [ADR-0009](0009-use-american-english.md).

### Release notes (enforced during API review)

14. **Reflect public API changes in the release notes.**
    Any change to the public API — a new type or member, a deprecation, or a behavioral change — SHOULD be reflected in the release notes for the corresponding release.
    New capabilities and deprecations in particular MUST be called out, since these notes are the primary way users learn what changed in the API they depend on.

## Consequences

- **A single checklist for authors and reviewers.**
  Contributors adding public API, including AI agents, and reviewers approving it, have one place that enumerates the requirements, on the same footing as the individual ADRs it references.

- **Automated rules catch structural and nullability violations early.**
  The requirements in the first two groups are enforced by architectural tests; a violation fails the build rather than waiting for review.
  Many of these rules are frozen, so pre-existing violations are tolerated but no new ones may be introduced — the public API surface can only get more conformant over time.

- **Documentation, samples, and release notes remain a human responsibility.**
  Javadoc quality, the presence of a helpful sample, and release-note updates cannot be fully verified by a test.
  They depend on API review to enforce, which is why they are stated here explicitly: a missing or unhelpful Javadoc, or a public API change with no release-note entry, SHOULD block the PR.

- **This ADR consolidates rather than supersedes.**
  It does not replace [ADR-0003](0003-avoid-introducing-Groovy-types-to-public-api.md), [ADR-0006](0006-use-of-provider-apis-in-gradle.md), [ADR-0008](0008-use-nullaway.md), [ADR-0009](0009-use-american-english.md), [ADR-0011](0011-api-observability.md), or [ADR-0012](0012-since-on-public-api.md); those remain the authoritative and detailed record of each decision.
