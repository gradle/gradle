# Property Self-Assignment: Optimizations Implementation Plan

## Context

The spike on branch `asodja/property-circular-resolve` implements self-assignment detection via `containsProviderInChain()` / `substituteProvider()` on `ProviderInternal`. Every `set(Provider)` call walks the provider chain doing identity comparisons — O(n) where n is chain depth (typically 1-3). The spec estimates < 1% overhead, but identifies three optimizations to reduce it further. This plan also addresses correctness gaps found during review.

## Pre-requisite: Fix Missing Overrides (Correctness)

Three composite providers wrap other providers but don't override `containsProviderInChain` / `substituteProvider`, so self-references through them go undetected:

| Provider | Wraps | Risk |
|----------|-------|------|
| `WithSideEffectProvider` | single `provider` field | Medium — reachable via `property.map{}.withSideEffect{}` |
| `MergeProvider` | `List<Provider<R>> items` | Low — internal use, unlikely in self-assignment |
| `DelegatingProviderWithValue` | single `delegate` field | Low — internal presence-enforcement wrapper |

**Root cause:** The current `containsProviderInChain` and `substituteProvider` have default implementations on `ProviderInternal`. New composite providers silently inherit the leaf behavior (`this == target`) instead of getting a compile error. This is addressed in Optimization 1 by removing all defaults.

**Files:**
- `platforms/core-configuration/model-core/src/main/java/org/gradle/api/internal/provider/WithSideEffectProvider.java`
- `platforms/core-configuration/model-core/src/main/java/org/gradle/api/internal/provider/MergeProvider.java`
- `platforms/core-configuration/model-core/src/main/java/org/gradle/api/internal/provider/DelegatingProviderWithValue.java`

---

## Optimization 1: Single-Pass Substitution with Leaf Fast-Path (Recommended)

**Impact: High** — Combines three improvements: (1) skip the walk entirely for leaf providers, (2) merge detect + substitute into a single pass for composite providers, halving virtual dispatches, and (3) remove default methods to prevent silent bugs in future composite providers.

### Design

#### Part A: No default methods — compile-time enforcement

The spike's `containsProviderInChain` and `substituteProvider` use default methods on `ProviderInternal`, which is why `WithSideEffectProvider`, `MergeProvider`, and `DelegatingProviderWithValue` were silently missed. To prevent this class of bug, **all three new methods (`isCompositeProvider`, `substituteProvider` with lazy factory) have no defaults on `ProviderInternal`**:

```java
// In ProviderInternal — no defaults, every provider must implement:

/**
 * Returns true if this provider composes over other providers,
 * meaning it may contain other providers in its chain.
 * Leaf providers return false; composite providers return true.
 */
boolean isCompositeProvider();

/**
 * Returns a provider equivalent to this one, but with all occurrences of {@code target}
 * in the upstream chain replaced by the result of {@code replacementFactory}.
 * The factory is called lazily — only when a match is found.
 * Returns {@code this} unchanged if {@code target} does not appear in the chain.
 *
 * <p>Leaf providers: return {@code replacementFactory.get()} if {@code this == target},
 * otherwise return {@code this}.
 *
 * <p>Composite providers: must recurse into all child providers.
 */
<S> ProviderInternal<S> substituteProvider(
    ProviderInternal<?> target, Supplier<ProviderInternal<?>> replacementFactory);
```

This means:
- **Every provider class** must explicitly implement both methods
- A new composite provider that forgets to walk its children **won't compile** until it provides implementations
- Leaf providers get ~4 lines of boilerplate each (trivial, one-time cost for ~10-15 classes)

Leaf provider implementation (e.g., `FixedValueProvider`, `DefaultProperty`, etc.):

```java
@Override
public boolean isCompositeProvider() {
    return false;
}

@SuppressWarnings("unchecked")
@Override
public <S> ProviderInternal<S> substituteProvider(
        ProviderInternal<?> target, Supplier<ProviderInternal<?>> replacementFactory) {
    if (this == target) {
        return (ProviderInternal<S>) replacementFactory.get();
    }
    return (ProviderInternal<S>) this;
}
```

#### Part B: Leaf fast-path with `isCompositeProvider()`

Composite providers override `isCompositeProvider()` to return `true`:
- `TransformBackedProvider` (covers `MappingProvider` via inheritance)
- `FlatMapProvider`
- `BiProvider`
- `FilteringProvider`
- `OrElseProvider`
- `OrElseFixedValueProvider`
- `WithSideEffectProvider`
- `MergeProvider`
- `DelegatingProviderWithValue`

#### Part C: Merge detect + substitute into single pass

Replace `containsProviderInChain` + `substituteProvider` with a single `substituteProvider` call that uses a lazy `Supplier` to avoid allocating `shallowCopy()` when no self-reference exists.

Each composite provider overrides to walk once:

```java
// TransformBackedProvider:
@Override
public boolean isCompositeProvider() { return true; }

@Override
public <S> ProviderInternal<S> substituteProvider(
        ProviderInternal<?> target, Supplier<ProviderInternal<?>> replacementFactory) {
    if (this == target) {
        return (ProviderInternal<S>) replacementFactory.get();
    }
    ProviderInternal<? extends IN> newProvider = provider.substituteProvider(target, replacementFactory);
    if (newProvider == provider) {
        return (ProviderInternal<S>) this;
    }
    return (ProviderInternal<S>) new TransformBackedProvider<>(type, newProvider, transformer);
}

// BiProvider:
@Override
public boolean isCompositeProvider() { return true; }

@Override
public <S> ProviderInternal<S> substituteProvider(
        ProviderInternal<?> target, Supplier<ProviderInternal<?>> replacementFactory) {
    if (this == target) {
        return (ProviderInternal<S>) replacementFactory.get();
    }
    ProviderInternal<A> newLeft = left.substituteProvider(target, replacementFactory);
    ProviderInternal<B> newRight = right.substituteProvider(target, replacementFactory);
    if (newLeft == left && newRight == right) {
        return (ProviderInternal<S>) this;
    }
    return (ProviderInternal<S>) new BiProvider<>(type, newLeft, newRight, combiner);
}
```

#### Part C: Updated call sites

Update `set()` in `DefaultProperty`, `AbstractCollectionProperty`, `DefaultMapProperty`:

```java
// Before (two passes):
if (p.containsProviderInChain(this)) {
    p = p.substituteProvider(this, shallowCopy());
}

// After (single pass with leaf fast-path):
if (p == this) {
    p = shallowCopy();
} else if (p.isCompositeProvider()) {
    ProviderInternal<? extends T> substituted = p.substituteProvider(this, this::shallowCopy);
    if (substituted != p) {
        p = substituted;
    }
}
```

This approach:
- **Leaf providers that aren't `this`**: skip entirely — single `==` check + `isCompositeProvider()` (both `false`)
- **Direct self-assignment** (`property.set(property)`): handled in O(1) without walking
- **Composite providers**: single walk, `shallowCopy()` allocated lazily only when a match is found
- **No match in composite**: walk returns identity (`substituted == p`), no allocation

#### Cost comparison

| Approach | Walks per `set()` | `shallowCopy()` allocations | Virtual dispatches |
|---|---|---|---|
| Current (detect + substitute) | 2 when found, 1 when not | Only when needed | 2n (found) or n (not found) |
| **Single-pass + leaf fast-path** | **0 for leaf, 1 for composite** | **Only when needed** | **0 (leaf) or n (composite)** |

#### Removing `containsProviderInChain` and old `substituteProvider`

With single-pass substitution, both `containsProviderInChain` and the old `substituteProvider(target, replacement)` (eager, non-lazy) become redundant — detection is implicit in the new `substituteProvider` returning a different instance. Both methods and all their overrides can be removed.

**Files:**
- `ProviderInternal.java` — remove `containsProviderInChain` + old `substituteProvider`; add `isCompositeProvider()` (no default) + lazy `substituteProvider` (no default)
- All leaf providers (~10-15 classes) — add `isCompositeProvider()` returning `false` + leaf `substituteProvider`
- All composite providers (9 classes) — add `isCompositeProvider()` returning `true` + chain-walking `substituteProvider`
- `DefaultProperty.java`, `AbstractCollectionProperty.java`, `DefaultMapProperty.java` — update `set()` call sites

---

## Optimization 2: Non-Self-Referencing Provider Marking (Deferred)

**Impact: Medium** — Helps for `prop.set(otherProp.map { ... })` where the chain is composite but built entirely from external sources.

### Design (for future consideration)

Track whether a provider chain contains any "assignable property" (i.e., `DefaultProperty`, `AbstractCollectionProperty`, `DefaultMapProperty`) using a boolean flag computed during construction:

```java
default boolean mayContainProperty() {
    return this instanceof HasMutableValue; // or similar marker
}
```

Composite providers compute this eagerly: `this.mayContainProperty = child.mayContainProperty()`.

If `!p.mayContainProperty()`, skip the walk — the chain is built entirely from non-property sources (literals, external providers).

### Why defer

- With Optimization 1 already in place, this only helps composite chains built from non-property sources (e.g., `Providers.of("x").map{}.filter{}`). These are rarer than leaf provider `set()` calls.
- Adds a boolean field to every composite provider.
- Requires careful handling — a property wrapped in `map()` becomes a composite, and its `mayContainProperty` must propagate correctly.
- Marginal benefit for typical chain depths of 1-3.

Can be revisited if profiling shows the single-pass substitution is still a hotspot after Optimization 1.

---

## ~~Optimization 3: Depth-Limited Walk~~ (Dropped)

A depth limit would create a behavioral cliff: chains below the limit work silently, chains above get a hard `CircularEvaluationException` with no obvious workaround. Since each walk step is a single identity comparison, even chains of hundreds of levels cost microseconds. The walk is naturally bounded by chain depth, which is 1-3 in practice. Stack overflow from deep recursion is not a realistic concern at any practical chain length. **Not worth the complexity or the user-facing cliff.**

---

## Implementation Order

1. **Optimization 1** (single-pass + leaf fast-path + no defaults) — removes `containsProviderInChain` and old `substituteProvider` entirely, replaces with `isCompositeProvider()` + lazy `substituteProvider`, fixes the missing overrides as a side effect (all providers must now implement both methods to compile)
2. Optimization 2 deferred to future work

The missing overrides fix (pre-requisite) is subsumed by Optimization 1 — removing defaults forces `WithSideEffectProvider`, `MergeProvider`, and `DelegatingProviderWithValue` to implement the new methods or fail to compile.

## Verification

1. Run existing spike tests to ensure no regressions:
   ```
   ./gradlew :model-core:test --tests "*PropertyCircular*"
   ```
2. Add test cases for the gaps:
   - Self-assignment through `WithSideEffectProvider`: `prop.set(prop.map{}.withSideEffect{})`
   - Self-assignment through `MergeProvider` (if reachable from user API)
3. Verify the fast-path by debugging or adding a test that passes a `FixedValueProvider` to `set()` — should not invoke `substituteProvider`
4. Run the full model-core test suite:
   ```
   ./gradlew :model-core:test
   ```
