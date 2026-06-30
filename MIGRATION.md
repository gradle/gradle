# Migrating off Deprecated Resolution APIs

This guide covers the most common migration patterns for resolution APIs deprecated in Gradle 9 and scheduled for removal in Gradle 10. It is intentionally task-oriented: every section pairs a deprecated pattern with the recommended replacement and explains the cases where the replacement behaves differently.

The deprecated APIs in scope:

- `ResolutionStrategy.force(...)`, `setForcedModules(...)`, `getForcedModules()`
- `ResolutionStrategy.eachDependency(Action<DependencyResolveDetails>)`
- `Configuration.getResolvedConfiguration()` / `getLenientConfiguration()` (the legacy resolution result surface)
- The `with` alias and space-syntax in `DependencySubstitutions` (e.g. `substitute module(x) using module(y)`, `substitute module(x) with project(y)`)

---

## 1. Migrating `eachDependency`

`eachDependency` is a per-dependency rule that runs against every requested dependency. Internally it is now a thin wrapper over `dependencySubstitution { all { ... } }` — the new API is strictly more capable.

### Pattern A: Concrete source module → concrete target module

This is the most common shape. Use the **declarative form**:

```groovy
// Before
configurations.all {
    resolutionStrategy.eachDependency { details ->
        if (details.requested.group == 'com.example' && details.requested.name == 'old-library') {
            details.useTarget('com.example:new-library:1.0.0')
            details.because('Our license only allows use of version 1')
        }
    }
}

// After
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module('com.example:old-library'))
            .because('Our license only allows use of version 1')
            .using(module('com.example:new-library:1.0.0'))
    }
}
```

Important conventions in the new API:

- **Wrap the selector in parentheses** — `substitute(module(...))`, not `substitute module(...)`.
- **`.using(...)` must always be last** — the chain is `substitute(...).because(reason).using(target)`. `because` (and `withClassifier`, `withoutClassifier`, `withoutArtifactSelectors`, etc.) must precede `using`.
- **`using(...)`, not `with(...)`** — the `with` alias is deprecated.

### Pattern B: Match by version only, or computed target

When the source isn't a single concrete `group:name`, or the target is computed from the requested coordinates, fall back to the `all { ... }` block. This is the direct equivalent of `eachDependency`:

```groovy
// Before — custom versioning scheme
resolutionStrategy.eachDependency { details ->
    if (details.requested.version == 'default') {
        def v = catalog.lookup(details.requested.group, details.requested.name)
        details.useVersion(v.version)
        details.because(v.reason)
    }
}

// After
resolutionStrategy.dependencySubstitution {
    all { dep ->
        if (dep.requested.version == 'default') {
            def v = catalog.lookup(dep.requested.group, dep.requested.module)
            dep.useTarget("${dep.requested.group}:${dep.requested.module}:${v.version}", v.reason)
        }
    }
}
```

Notes:

- `DependencyResolveDetails.requested.name` is `.module` on `ModuleComponentSelector`. Other fields (`group`, `version`) are the same.
- `useVersion('x')` becomes `useTarget("${group}:${module}:x")` — the substitution API doesn't have a version-only setter.
- The 2-arg `useTarget(notation, reason)` is the equivalent of calling `useTarget(...)` and `because(...)` together.
- In Groovy, dynamic dispatch lets you read `dep.requested.module` directly. In Kotlin, you need to cast: `val req = requested as? ModuleComponentSelector ?: return@all`.

### Pattern C: Substituting a project dependency

The substitution API can do something `eachDependency` could not — substitute a project dependency. If you previously relied on `eachDependency` seeing project dependencies as their module coordinates, prefer the explicit project selector:

```groovy
resolutionStrategy.dependencySubstitution {
    substitute(project(':a')).using(module('org.gradle.test:a:1.3'))
    // or the reverse
    substitute(module('org.gradle:api')).using(project(':api'))
}
```

---

## 2. Migrating `force(...)`, `forcedModules`, `setForcedModules`

`ResolutionStrategy.force(...)` and its `forcedModules` getter/setter were limited to forcing a specific version of a module. Two clean replacements exist; pick based on intent.

### Replacement A: A substitution of the same `group:name` (recommended for most cases)

Gradle recognizes a substitution like `substitute(module('g:n')).using(module('g:n:v'))` as a force internally. The selected node in the resolution graph still carries the `forced` selection reason, so assertions like `forced()` in `resolve.expectGraph` continue to work.

```groovy
// Before
configurations.compile.resolutionStrategy.force 'asm:asm-all:3.3.1'

// After
configurations.compile.resolutionStrategy.dependencySubstitution {
    substitute(module('asm:asm-all')).using(module('asm:asm-all:3.3.1'))
}
```

For multiple modules, write one `substitute(...).using(...)` line per module.

### Replacement B: A strict version constraint

If the intent is closer to "every consumer must use exactly this version, and the choice should be reflected in published metadata", use a strict version constraint instead. This is the form CodeNarc, AGP, and other plugin authors have adopted:

```groovy
// Before
configurations.codenarc.resolutionStrategy.force 'org.apache.groovy:groovy:4.0.21'

// After
dependencies {
    constraints {
        codenarc('org.apache.groovy:groovy') {
            version {
                strictly('4.0.21')
            }
        }
    }
}
```

Strict constraints differ from forces:

- They participate in normal conflict resolution and produce a *failure* when another participant requires an incompatible version, rather than silently overriding.
- They are published in module metadata (if you publish the configuration), so downstream consumers see the requirement.
- They are tied to a configuration through dependency declaration, not to the resolution strategy.

When in doubt: if you want loud failure on conflict, use `strictly`. If you want silent override, use the substitution form.

### Removing `forcedModules` (getter/setter)

There is no direct replacement. Migrate each entry as a substitution rule or constraint, then delete the getter/setter call.

---

## 3. Migrating `ResolvedConfiguration` and `LenientConfiguration`

The legacy `Configuration.getResolvedConfiguration()` and `getLenientConfiguration()` APIs predate Gradle's variant-aware engine and don't compose well with the configuration cache. They are being deprecated. Replace them with one of:

- `Configuration.getIncoming().getResolutionResult()` — for inspecting the resolved dependency graph
- `Configuration.getIncoming().getArtifacts()` — for the resolved artifacts as files/IDs
- `Configuration.getIncoming().artifactView { ... }` — when you need filtering, lenient resolution, or variant reselection

### Pattern A: Walking first-level / transitive dependencies

```groovy
// Before
configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.each {
    it.children.each { transitive ->
        println "${transitive.moduleGroup}:${transitive.moduleName}:${transitive.moduleVersion}"
    }
}

// After
def rootComponent = configurations.compile.incoming.resolutionResult.rootComponent
def rootVariant = configurations.compile.incoming.resolutionResult.rootVariant
doLast {
    rootComponent.get().getDependenciesForVariant(rootVariant.get()).each { transitive ->
        println "${transitive.owner.group}:${transitive.owner.module}:${transitive.owner.version}"
    }
}
```

Key differences:

- `rootComponent` and `rootVariant` are `Provider<...>` — declare them as task inputs (or call `.get()` inside `doLast`). This makes the code configuration-cache friendly.
- The graph is variant-aware. A dependency may resolve to a specific variant of a component; `getDependenciesForVariant(...)` is the entry point that mirrors the old "first-level dependencies of this configuration" walk.

### Pattern B: Inspecting resolved artifacts

```groovy
// Before
configurations.compile.resolvedConfiguration.resolvedArtifacts.each {
    println "${it.name}:${it.extension}:${it.classifier}"
}

// After
def resolvedArtifacts = configurations.compile.incoming.artifacts.resolvedArtifacts
dependsOn(resolvedArtifacts)
doLast {
    resolvedArtifacts.get().each {
        println "${it.id.componentIdentifier.displayName} -> ${it.file.name}"
    }
}
```

Notes:

- `incoming.artifacts.resolvedArtifacts` is a `Provider<Set<ResolvedArtifactResult>>`. Always declare it (or `incoming.artifacts` itself) as a task input so the artifacts are wired into the task graph.
- The new `ResolvedArtifactResult` exposes the artifact `id`, the component `variant`, the `file`, and the `type` directly. The classifier/extension/name fields previously read off `ResolvedArtifact` are accessible via `id.componentIdentifier` or the variant attributes.
- For the file set only, `incoming.artifacts.artifactFiles` returns a `FileCollection` that's straightforward to consume.

### Pattern C: Lenient resolution

```groovy
// Before
def files = configurations.compile.resolvedConfiguration.lenientConfiguration.files

// After
def files = configurations.compile.incoming.artifactView {
    lenient(true)
}.files
```

`artifactView { lenient(true) }` is the modern lenient API. It also accepts a `componentFilter`, `withVariantReselection()`, and attribute overrides — capabilities the old `LenientConfiguration` didn't have.

---

## 4. DSL conventions in `dependencySubstitution { ... }`

Three small style points that the deprecation cycle is enforcing:

### Use parentheses and dots

The space-syntax form is no longer recommended:

```groovy
// Old
substitute module('org:foo:1.0') using module('org:foo:1.1')

// New
substitute(module('org:foo:1.0')).using(module('org:foo:1.1'))
```

### `using`, not `with`

```groovy
// Old
substitute module('org:foo') with project(':foo')

// New
substitute(module('org:foo')).using(project(':foo'))
```

### `using(...)` is always the last call

If you need to attach a reason, a classifier, or no-artifact-selector, place those calls *before* `using`:

```groovy
// Wrong — using() before because()
substitute(module('org:foo')).using(module('org:foo:1.1')).because('CVE fix')

// Right — because() before using()
substitute(module('org:foo')).because('CVE fix').using(module('org:foo:1.1'))

// Same applies to withClassifier / withoutClassifier
substitute(module('org:lib:1.0'))
    .withClassifier('tests')
    .using(module('org:lib:1.0'))
```

This applies to the `Substitution` builder uniformly — any modifier of the source selector comes before `using`, which finalizes the substitution.

---

## 5. Subtle behavioral differences to be aware of

A few cases where the new APIs are not exact 1:1 substitutes:

### Strict-version selectors don't match `module('g:n:v')`

A dependency declared with `strictly '1.0'` produces a component selector that is *not* equal to `module('g:n:1.0')` (which is non-strict). The declarative `substitute(module('g:n:1.0'))` form will not match a strict-version request. Use the `all { ... }` form when you need to substitute strict-version edges:

```groovy
resolutionStrategy.dependencySubstitution {
    all { dep ->
        if (dep.requested.module == 'foo' && dep.requested.version == '1.0') {
            dep.useTarget('org:new:1.0')
        }
    }
}
```

### Adding `all { }` enables graph-time task-dependency resolution

`eachDependency`'s internal wrapper did not flip the "rules may add project dependency" flag. The substitution `all { }` form does. The practical effect is that adding an `all { }` rule (even an empty one) causes Gradle to fully resolve the configuration during task-graph construction, rather than lazily. For 99% of resolution use cases this is invisible; if you observe unexpected ordering of configuration vs. execution phases after migrating, this is the cause.

### `useVersion(...)` doesn't exist on `DependencySubstitution`

`DependencyResolveDetails.useVersion('x')` kept the existing group/name and only changed the version. On `DependencySubstitution`, build a full target selector:

```groovy
dep.useTarget("${dep.requested.group}:${dep.requested.module}:x")
```

### The `forced()` selection reason still appears

When a substitution maps `group:name` → `group:name:v` (same module identity, version-only change), Gradle treats this internally as a force and the resolved node carries both `selectedByRule` and `forced` selection reasons. Existing graph assertions that expect `forced()` continue to pass after migration.

---

## Quick reference

| Deprecated API | Replacement | Notes |
| --- | --- | --- |
| `eachDependency { details -> ... }` (concrete g:n → concrete target) | `substitute(module('g:n')).using(module('g:n:v'))` | Use `.because(reason).using(target)` for reasons |
| `eachDependency { details -> ... }` (conditional, computed target, or version-only match) | `dependencySubstitution { all { dep -> ... } }` | Use `dep.useTarget(notation, reason)` for reasons |
| `force 'g:n:v'` | `substitute(module('g:n')).using(module('g:n:v'))` | Still produces `forced` selection reason |
| `force 'g:n:v'` (with publish-visible intent) | `dependencies { constraints { conf('g:n') { version { strictly('v') } } } }` | Conflicts now fail loudly |
| `forcedModules = [...]` / `setForcedModules(...)` / `getForcedModules()` | Migrate each entry as a substitution or strict constraint; remove the getter/setter | No direct replacement |
| `configuration.resolvedConfiguration.firstLevelModuleDependencies` | `configuration.incoming.resolutionResult.rootComponent.get().getDependenciesForVariant(...)` | Variant-aware |
| `configuration.resolvedConfiguration.resolvedArtifacts` | `configuration.incoming.artifacts.resolvedArtifacts` | Provider; declare as task input |
| `configuration.resolvedConfiguration.lenientConfiguration` | `configuration.incoming.artifactView { lenient(true) }` | Richer filtering options |
| `substitute X using Y` (space syntax) | `substitute(X).using(Y)` | Paren+dot form |
| `substitute X with Y` | `substitute(X).using(Y)` | `with` is deprecated |
| `substitute(X).using(Y).because(r)` | `substitute(X).because(r).using(Y)` | `using` must be last |
