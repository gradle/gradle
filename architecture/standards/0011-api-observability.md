# ADR-0011 - API observability

## Status

- PROPOSED on 2026-05-28

## Context

Gradle's public API exposes a large number of *configuration APIs*: types and methods whose purpose is to let a build author or plugin tell Gradle how to behave.
A recurring shape problem affects many of them: they ship a setter (a `set*` method, a `configure*` builder, or an action-based mutator), but no symmetric way to read the resulting state, and no way to put it back the way it was.

This makes builds:

* **Hard to debug.** Once a value has been configured, neither the build author nor a downstream plugin can ask "what is currently in effect here?".
  The only source of truth is the call site that wrote it.
* **Hard to compose.** Plugins routinely apply conventions on top of each other.
  Without an observer, a plugin cannot inspect what an earlier plugin (or the user) has already configured, and cannot decide intelligently whether to override, merge, or step aside.
* **Order-dependent and lossy.** With several plugins all calling write-only setters, the final state is whatever happened to be applied last.
  There is no way to introspect that final state, and no way to recover an earlier one.

The problem is not new — ADR-0006 ([Use of Provider APIs in Gradle](0006-use-of-provider-apis-in-gradle.md)) already mandates lazy `Property<T>` / `Provider<T>` properties for new configuration on tasks, extensions and domain objects.
When that ADR is followed, observability is essentially free: a `Property<T>` getter is both the setter and the observer, the convention machinery models "default in effect", and `unset()` restores the default.
This ADR is the observability complement to ADR-0006: it generalizes the same expectation to *every* public configuration API, including those where `Property<T>` is not the natural shape (action-based mutators, semantic verbs like `failOnVersionConflict()`, etc.).

### Worked counter-example: `ResolutionStrategy`

The public interface `org.gradle.api.artifacts.ResolutionStrategy` (`subprojects/core-api/src/main/java/org/gradle/api/artifacts/ResolutionStrategy.java`) is the clearest illustration of the cost of *not* having this standard.
It is a configuration API with broad reach — every `Configuration` has one — and almost none of its mutators are observable.

Setters with no observer and no way to restore the default:

* `preferProjectModules()` at `ResolutionStrategy.java:147` flips a boolean-shaped behavior; there is no `getPreferProjectModules()` and no way to unset it.
* `cacheDynamicVersionsFor(int, String)` at `ResolutionStrategy.java:289` and `cacheDynamicVersionsFor(int, TimeUnit)` at `ResolutionStrategy.java:302` set a duration that nothing can query.
  `cacheChangingModulesFor(int, String)` at `ResolutionStrategy.java:314` and `cacheChangingModulesFor(int, TimeUnit)` at `ResolutionStrategy.java:327` are the same pattern.
  Once any of these has been called, neither the build nor another plugin can recover the configured duration, the unit, or the fact that the default is no longer in effect.
* `sortArtifacts(SortOrder)` at `ResolutionStrategy.java:405` writes the sort order; the matching `getSortArtifacts()` / `getArtifactSortOrder()` does not exist.
* Verb-style mutators in the same file follow the same shape: `failOnVersionConflict()`, `failOnDynamicVersions()`, `failOnChangingVersions()`, `failOnNonReproducibleResolution()`, `activateDependencyLocking()`, `deactivateDependencyLocking()`, `enableDependencyVerification()`, `disableDependencyVerification()` — all write-only, all silent about the resulting state.

Action-based mutators where registered behavior is not introspectable:

* `eachDependency(Action<? super DependencyResolveDetails>)` at `ResolutionStrategy.java:277` — each registered action is opaque to every later caller.
* `dependencySubstitution(Action<? super DependencySubstitutions>)` at `ResolutionStrategy.java:375` — the rules added inside the action vanish behind a `DependencySubstitutions` whose registered rules are not enumerable.
* `componentSelection(Action<? super ComponentSelectionRules>)` at `ResolutionStrategy.java:344` and `capabilitiesResolution(Action<? super CapabilitiesResolution>)` at `ResolutionStrategy.java:414` — same shape.

Acknowledging the partial prior art on this same interface:

* `getForcedModules()` at `ResolutionStrategy.java:245` does return the configured set, so `force(...)` is at least observable.
  The shape is inconsistent with everything around it — one observer, surrounded by write-only setters — which is exactly the cost of not having a standard.
* `getUseGlobalDependencySubstitutionRules()` at `ResolutionStrategy.java:389` is a `Property<Boolean>`, and reads exactly the way ADR-0006 prescribes — observable and restorable through the convention machinery.
  The contrast with `preferProjectModules()` on the same interface is striking: both are boolean-shaped, but only one is fit to compose with.

The remainder of this ADR sets a standard that would have prevented this shape, and gives `ResolutionStrategy` an explicit path forward.

## Decision

This ADR applies to every public configuration API in Gradle: a public type, or public method on a public type, whose purpose is to let users configure how some Gradle behavior runs.
It does not apply to internal types, to behavior-free data carriers, or to identity (see ADR-0006 on identity information).

The keywords MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as defined in RFC 2119.

1. **Every public setter MUST have an observer.**
   For every public setter — whether spelled `setX(...)`, `x(...)`, a fluent verb like `failOnVersionConflict()`, or a `configure*` block — the same API MUST expose a way to read the currently configured value.
   The observer MAY take any of these shapes: a `Property<T>` / `Provider<T>` getter (preferred; see ADR-0006), a plain getter, or a documented query method that returns the effective configuration as data.
   The observer MUST also let the caller distinguish "the default is in effect" from "the default value happens to have been configured explicitly".
   With `Property<T>`, this falls out of the convention machinery; with other shapes it MUST be documented.

2. **Public configuration APIs SHOULD let the user restore the default.**
   For every behavior that has a default, the API SHOULD expose a way to put it back: `Property.unset()` on a lazy property, an explicit `resetX()` / `clearX()` method, a nullable setter documented to mean "use the default", or an equivalent.
   Where restoration is fundamentally impossible (the side effect cannot be undone), the API MUST document this explicitly.
   "Restoration is not implemented yet" is not the same thing as "restoration is impossible" and MUST NOT be papered over.

3. **Action-based mutators MUST be introspectable, or MUST be re-modelled as data.**
   When a public API accepts an `Action<...>` / `Closure` that registers behavior (rules, callbacks, configuration blocks), one of the following MUST hold:

   * the API exposes an observer over the *result* of all registered actions (e.g. the set of rules they produced, the count and provenance of registered actions, or an equivalent introspection surface), or
   * the API is re-modelled so that the configuration is data, not opaque behavior — typically a managed domain object container, a `NamedDomainObjectContainer`, or a `Property<T>` over a value type.
     The container MUST let callers enumerate what has been configured (entries, names, identities); it does NOT have to expose recursive observability into the internal state of each entry.
     Whether an entry type is itself observable is governed by this ADR independently — only if and to the extent the entry type is itself a public configuration API.

   The first form preserves the action-based shape; the second eliminates the problem.
   New APIs SHOULD prefer the second.

4. **New public APIs MUST comply with this standard from now on.**
   API review MUST treat a missing observer, or a missing default-restoration story, the same way it treats a missing nullability annotation or a missing `@since` tag: as a blocking issue, not a follow-up.

5. **Existing public APIs SHOULD be migrated.**
   Migration MAY be incremental — adding an observer in one release, restoration in the next, action introspection later — and SHOULD be tracked alongside the normal deprecation pipeline.
   Where adding a getter to a public interface would break binary compatibility for implementors outside Gradle, the migration SHOULD use a default method or a companion query type rather than skipping the observer altogether.

### Terminology

* **Configuration API** — a public type or public method on a public type whose purpose is to let users tell Gradle how to behave.
  Tasks, extensions, domain objects, and types like `ResolutionStrategy` all qualify.
  Pure data carriers and identity getters do not.
* **Observer** — a public read API that returns the currently configured value of some piece of behavior, and that lets the caller distinguish "the default is in effect" from "an explicit value has been configured".
  A `Provider<T>` whose presence reflects whether a convention is in effect counts; a plain getter that returns the convention without disclosing that fact does not.
* **Default-restoration** — a public way to return the configured behavior to the state it would have been in if no setter had ever been called.
  `Property.unset()` is the canonical shape; `resetX()` / `clearX()` / a documented nullable setter are acceptable alternatives.
* **Action introspection** — a public way to observe the effect of action-based mutators after the fact: either the data they produced (rules, substitutions, registered objects) or, at minimum, their count and origin.
  Container membership (the entries, names, and identities a `NamedDomainObjectContainer` or equivalent holds) counts as introspection over the registered actions' result; recursive observability into each entry's own setters is out of scope for this requirement.

## Consequences

* **For new APIs.** Reviewers gain a concrete checklist, on the same footing as ADR-0003 (Groovy types), ADR-0006 (Provider APIs), ADR-0008 (NullAway) and ADR-0009 (American English).
  For each public setter or `configure*` block proposed in a PR, the review asks: is there an observer? does it distinguish default from explicit? is there a way to restore the default? for action-based mutators, what is the introspection story?
  A PR that adds a public setter without an observer SHOULD be sent back for revision.
  In most cases the answer is "use `Property<T>` per ADR-0006", which already satisfies all three requirements; this ADR mostly makes that expectation enforceable beyond the cases ADR-0006 explicitly covers.

* **For existing APIs.** There is a real migration cost, and it spans multiple releases.
  `ResolutionStrategy` alone implies adding observers (and where possible, default-restoration) for `preferProjectModules`, the cache durations, `sortArtifacts`, and the `failOn*` / dependency-locking / dependency-verification verbs, plus an introspection surface for `eachDependency`, `dependencySubstitution`, `componentSelection` and `capabilitiesResolution`.
  A realistic path is to add `Property<T>`-shaped observers alongside the existing setters (e.g. a `Property<Boolean>` for `preferProjectModules`, a `Property<Duration>` for the cache windows, a `Property<SortOrder>` for `sortArtifacts`), deprecate the write-only setters in favour of those properties, and design an introspection surface for the action-based methods over one or more releases.
  Comparable shapes exist elsewhere in the dependency-management, plugin and tooling APIs.
  We accept this cost; the alternative is to keep shipping new write-only setters indefinitely.
  Treat this ADR as the constraint, not the schedule.

* **For binary compatibility.** Adding methods to public interfaces is a binary-compatible change only if the new methods have default implementations or if the interface is documented as not-for-implementation outside Gradle.
  Migration of existing interfaces SHOULD use Java `default` methods or a companion query type when external implementations exist; both are preferable to leaving the API unobservable.

* **For the `Property<T>` / `Provider<T>` idiom.** This ADR makes the lazy-property idiom from ADR-0006 the default answer for new configuration APIs: it satisfies observation, default-restoration (via `unset()`) and convention-handling in one shape, and it is already what reviewers expect.
  APIs that deviate from the lazy-property idiom (verb-style mutators, action-based configuration) are not forbidden, but they MUST carry the extra observer/restoration/introspection work that the lazy-property idiom would have given them for free.

