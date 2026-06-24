# ADR-0011 - API observability

## Status

- PROPOSED on 2026-05-28

## Context

Gradle's public API exposes a large number of *configuration APIs*: types and methods whose purpose is to let a build author or plugin tell Gradle how to behave.
A recurring shape problem affects many of them: they ship a setter (a `set*` method or a fluent verb-style mutator), but no symmetric way to read the resulting state.

This makes builds:

* **Hard to debug.** Once a value has been configured, neither the build author nor a downstream plugin can ask "what is currently in effect here?".
  The only source of truth is the call site that wrote it.
* **Hard to compose.** Plugins routinely apply conventions on top of each other.
  Without an observer, a plugin cannot inspect what an earlier plugin (or the user) has already configured, and cannot decide intelligently whether to override, merge, or step aside.
* **Order-dependent and lossy.** With several plugins all calling write-only setters, the final state is whatever happened to be applied last.
  There is no way to introspect that final state.

The problem is not new — ADR-0006 ([Use of Provider APIs in Gradle](0006-use-of-provider-apis-in-gradle.md)) already mandates lazy `Property<T>` / `Provider<T>` properties for new configuration on tasks, extensions and domain objects.
When that ADR is followed, observability is essentially free: a `Property<T>` getter is both the setter and the observer.
This ADR is the observability complement to ADR-0006: it generalizes the same expectation to *every* public configuration API, including those where `Property<T>` is not the natural shape (semantic verbs like `failOnVersionConflict()`, etc.).

### Worked counter-example: `ResolutionStrategy`

The public interface `org.gradle.api.artifacts.ResolutionStrategy` (`subprojects/core-api/src/main/java/org/gradle/api/artifacts/ResolutionStrategy.java`) is the clearest illustration of the cost of *not* having this standard.
It is a configuration API with broad reach — every `Configuration` has one — and almost none of its mutators are observable.

Setters with no observer:

* `preferProjectModules()` at `ResolutionStrategy.java:147` flips a boolean-shaped behavior; there is no `getPreferProjectModules()`.
* `cacheDynamicVersionsFor(int, String)` at `ResolutionStrategy.java:289` and `cacheDynamicVersionsFor(int, TimeUnit)` at `ResolutionStrategy.java:302` set a duration that nothing can query.
  `cacheChangingModulesFor(int, String)` at `ResolutionStrategy.java:314` and `cacheChangingModulesFor(int, TimeUnit)` at `ResolutionStrategy.java:327` are the same pattern.
  Once any of these has been called, neither the build nor another plugin can recover the configured duration or the unit.
* `sortArtifacts(SortOrder)` at `ResolutionStrategy.java:405` writes the sort order; the matching `getSortArtifacts()` / `getArtifactSortOrder()` does not exist.
* Verb-style mutators in the same file follow the same shape: `failOnVersionConflict()`, `failOnDynamicVersions()`, `failOnChangingVersions()`, `failOnNonReproducibleResolution()`, `activateDependencyLocking()`, `deactivateDependencyLocking()`, `enableDependencyVerification()`, `disableDependencyVerification()` — all write-only, all silent about the resulting state.

Acknowledging the partial prior art on this same interface:

* `getForcedModules()` at `ResolutionStrategy.java:245` does return the configured set, so `force(...)` is at least observable.
  The shape is inconsistent with everything around it — one observer, surrounded by write-only setters — which is exactly the cost of not having a standard.
* `getUseGlobalDependencySubstitutionRules()` at `ResolutionStrategy.java:389` is a `Property<Boolean>`, and reads exactly the way [ADR-0006](0006-use-of-provider-apis-in-gradle.md) prescribes.
  The contrast with `preferProjectModules()` on the same interface is striking: both are boolean-shaped, but only one is fit to compose with.

The same interface also carries action-based mutators — `eachDependency` at `ResolutionStrategy.java:277`, `dependencySubstitution` at `ResolutionStrategy.java:375`, `componentSelection` at `ResolutionStrategy.java:344`, and `capabilitiesResolution` at `ResolutionStrategy.java:414` — where behavior is either registered to run later or used to configure a container.
Whether (and how) such APIs need to be observable raises a different set of questions and is left to a follow-up ADR; this ADR focuses on plain setters.

The remainder of this ADR sets a standard that would have prevented the setter shape above, and gives `ResolutionStrategy` an explicit path forward.

## Decision

This ADR applies to every public configuration API in Gradle: a public type, or public method on a public type, whose purpose is to let users configure how some Gradle behavior runs.

The keywords MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as defined in [RFC 2119](https://datatracker.ietf.org/doc/html/rfc2119).

1. **Every public setter MUST have an observer.**
   For every public setter — whether spelled `setX(...)`, `x(...)`, or a fluent verb like `failOnVersionConflict()` — the same API MUST expose a way to read the currently configured value.
   The observer MAY take any of these shapes: a `Property<T>` / `Provider<T>` getter (preferred; see ADR-0006), a plain getter, or a documented query method that returns the effective configuration as data.

2. **New public APIs MUST comply with this standard from now on.**
   API review MUST treat a missing observer the same way it treats a missing nullability annotation or a missing `@since` tag: as a blocking issue, not a follow-up.

3. **Existing public APIs SHOULD be migrated.**
   Migration MAY be incremental and SHOULD be tracked alongside the normal deprecation pipeline.
   Where adding a getter to a public interface would break binary compatibility for implementors outside Gradle, the migration SHOULD use a Java `default` method or a companion query type rather than skipping the observer altogether.

This ADR deliberately does not cover action-based mutators — APIs that accept an `Action<...>` / `Closure` registering behavior to run later, or used to configure a container.
That question — whether and how registered callbacks should be observable, and where lifecycle hooks fit — is left to a follow-up ADR.

### Terminology

* **Configuration API** — a public type or public method on a public type whose purpose is to let users tell Gradle how to behave.
  Tasks, extensions, domain objects, and types like `ResolutionStrategy` all qualify.
* **Observer** — a public read API that returns the currently configured value of some piece of behavior.
  A `Property<T>` / `Provider<T>` getter is the canonical shape; a plain getter or a documented query method are acceptable alternatives.

## Consequences

* **For new APIs.** Reviewers gain a concrete checklist, on the same footing as [ADR-0003](0003-avoid-introducing-Groovy-types-to-public-api.md) (Groovy types), [ADR-0006](0006-use-of-provider-apis-in-gradle.md) (Provider APIs), [ADR-0008](0008-use-nullaway.md) (NullAway) and [ADR-0009](0009-use-american-english.md) (American English).
  For each public setter proposed in a PR, the review asks: is there an observer?
  A PR that adds a public setter without an observer SHOULD be sent back for revision.
  In most cases the answer is "use `Property<T>` per ADR-0006", which already satisfies this requirement; this ADR mostly makes that expectation enforceable beyond the cases ADR-0006 explicitly covers.

* **For existing APIs.** There is a real migration cost, and it spans multiple releases.
  `ResolutionStrategy` alone implies adding observers for `preferProjectModules`, the cache durations, `sortArtifacts`, and the `failOn*` / dependency-locking / dependency-verification verbs.
  A realistic path is to add `Property<T>`-shaped observers alongside the existing setters (e.g. a `Property<Boolean>` for `preferProjectModules`, a `Property<Duration>` for the cache windows, a `Property<SortOrder>` for `sortArtifacts`) and deprecate the write-only setters in favour of those properties, over one or more releases.
  Comparable shapes exist elsewhere in the dependency-management, plugin and tooling APIs.
  We accept this cost; the alternative is to keep shipping new write-only setters indefinitely.
  Treat this ADR as the constraint, not the schedule.

* **For binary compatibility.** Adding methods to public interfaces is a binary-compatible change only if the new methods have default implementations or if the interface is documented as not-for-implementation outside Gradle.
  Migration of existing interfaces SHOULD use Java `default` methods or a companion query type when external implementations exist; both are preferable to leaving the API unobservable.

* **For the `Property<T>` / `Provider<T>` idiom.** This ADR makes the lazy-property idiom from ADR-0006 the canonical answer for new configuration APIs: a `Property<T>` getter is both the setter and the observer in one shape, and it is already what reviewers expect.
  APIs that deviate from the lazy-property idiom (verb-style mutators) are not forbidden, but they MUST carry the extra observer work that the lazy-property idiom would have given them for free.
