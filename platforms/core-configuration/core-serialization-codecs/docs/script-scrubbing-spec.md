# Keeping script objects in the configuration cache

Author(s): Sergey Igushkin
Github issue: https://github.com/gradle/gradle/issues/22879, https://github.com/gradle/gradle/issues/20126

## Abstract

A task action defined in a script might capture the script in its closure — a Groovy `doLast` closure keeps its owning script as `owner`,
a Kotlin `doLast` lambda captures the script instance if a function or a property defined at the top level is used.
So the script receiver object gets reachable from the task state.

The Configuration Cache used to reject Kotlin DSL script receiver objects if they were captured by the task state.
Groovy script receiver objects used to be dropped in encoding and then decoded to "broken script" objects which would throw on any access.

We now keep the script instance and sever only its links to the build model in the process called "scrubbing".

Branch: `sigushkin/tide/script-closures-in-cc`.

## Background

Both Groovy and Kotlin scripts are compiled to a class that derives from either `KotlinScript` or `BasicScript` (Groovy).

It holds references to:
* the mutable build model, `Project`/`Settings`/`Gradle`, being the script target,
* a `ServiceRegistry`
* the Project-independent services behind `file()` and `logger`,
* the user-defined top-level `val`/`var`/`def` in the script.

Kotlin lambdas and Groovy closures defined in the script, as well as arbitrary objects of custom classes created by the script,
can hold references to the script. The lambdas do that as a means to access the outer scope's variables and methods.

Such lambdas (and objects in general) can be referenced from the task state (including task actions).

The existing behavior was:
* `unsupported<GradleScript>` codec stub that would bail when a script reference appeared in custom objects and Kotlin lambdas.
* `groovyCodecs` for stubbing Groovy script objects as "broken scripts" when decoded and replacing the closure owners with such "broken script" objects.
    * The broken script objects would throw when accessed at execution time, but stay low if unused.

## Why

The captured-script pattern is ordinary build logic, not an edge case.
A `doLast` that calls a `def` helper, or reads a top-level `val`, captures the script.

Build Engineers and Software Developers writing task actions inline hit it constantly.
Rejecting it means the build is CC-incompatible until someone hoists the state out of the script — an awkward rewrite
for something that reads fine and has no inherent problems.

## Functional design

Both codecs make the same moves:

1. Keep the compiled script instance rather than rejecting it.
1. Sever the build model — replace known references to from the script object it with a "broken object" stand-in, similar to the "broken script" object.
   These broken objects are proxies that report a clear CC problem and throw on any method invocation
1. Keep the script's own state and ambient services working (`val`/`var`, `file()`, `logger`). The services cannot be accessed from the broken objects,
   so references to them are stored in the cache, not delegated to the model.
1. Preserve identity (`encodePreservingIdentityOf`, register before reading fields), so the many closures sharing one script decode to one instance.

## Kotlin-specific implementation

The `ScrubbableScriptCodec` handles `ScrubbableScript` subtypes. Only `DefaultKotlinScript` implements this marker interface.

The codec "scrubs" the script object by filtering the fields (obtained by `relevantStateOf`).
For Kotlin scripts, the field filtering predicate is:
* Drop the fields declared with a build model type (a subtype of `Project`, `Settings`, or `Gradle`).

* Drop the fields declared with a subtype of marker `ScrubbableScript.ScrubbedOut`

    * This is needed to mark the `KotlinScriptHost` holding model and some script machinery data & services but not needed for lambdas.

* Drop the two synthetic fields the KTS compiler generates: `$$implicitReceiver_*` (the receivers) and `$$result` (the script value, for non-Unit scripts). Regular property fields and their `$delegate` backing fields are kept.

The base classes delegate `PluginAware` (`PluginAware by ...`), which the compiler backs with a `$$delegate_N` field. We don't match it by name: for a project script it's typed `Project`, so it's already a build-model reference; for settings/init it's a `PluginAwareScript`, which we mark `ScrubbableScript.ScrubbedOut` so it's dropped by type.

In encoding, such fields are omitted.

In decoding, the instance is allocated without running its constructor, via `beanStateReaderFor(type).newBeanWithId(id)` — the Kotlin script constructor needs a live host, which we don't have.
Fields we keep are set reflectively.
If an omitted field has an interface type, it gets a reference to a "broken object" JDK `Proxy` that reports throws a CC problem on any method usage
(including those of `Any`). For non-interface types, `null` stays in the field, which is fine for the current script classes content but might need care.

## Groovy-specific implementation

`GroovyScriptCodec` handles `BasicScript` subtypes — the base class of build, settings and init Groovy scripts.

The codec "scrubs" the script object by filtering the fields (obtained by `relevantStateOf`).
The field filtering predicate is:
* Drop the field holding the build model — recognized by its runtime value being a subtype of `Project`, `Settings` or `Gradle`.
    * The target field (`BasicScript.target`) is declared as `Object`, so unlike Kotlin the declared type tells us nothing; we inspect the value.
* Drop the Groovy dynamic-dispatch scaffolding — `DynamicObject`, `Binding` and `MetaClass` fields.
    * These are rebuilt when the constructor runs on decode, so there's nothing to carry.
* Drop the `ClassLoader`-typed field (there is just `contextClassLoader`).
* Drop `ServiceRegistry` fields (there is normally just one).
    * There's no serialized form; they are re-resolved from the isolate owner on decode.

In encoding, such fields are omitted. The codec also writes the target kind (`Project`/`Settings`/`Init`), since the target field itself is dropped and the kind
is needed on decode to build the matching broken stand-in.

In decoding, the instance is created by running its no-arg constructor (`scriptType.getDeclaredConstructor().newInstance()`).
Unlike the Kotlin path, running the constructor is what rebuilds the `Binding`, `ScriptDynamicObject` and `MetaClass` — which is why those are dropped rather than carried.
Retained fields are set reflectively by name; `ServiceRegistry` fields are set to the isolate owner's registry.
The context class loader is only needed to run the script, and we do not run it on CC reuse.

The build model is severed by replacing the script's target with a single "broken object".
It's a JDK `Proxy` implementing the internal model interface (`ProjectInternal`/`SettingsInternal`/`GradleInternal`, picked from the stored target kind) plus `DynamicObjectAware`, and it has to
satisfy two consumers at once:
* `ProjectScript.getScriptTarget()` casts the target to `ProjectInternal`, so the proxy must be assignable to it — hence proxying the internal type.
* The Groovy MOP reaches the model through the script's `ScriptDynamicObject`, so the proxy's `getAsDynamicObject` returns a `BrokenModelDynamicObject`.

Every other method on the proxy reports a CC problem and throws. So `buildscript`, `apply` and direct model calls from a task action fail with a clear message instead of a raw `ClassCastException`.

Two other codecs registered by `groovyCodecs` complete the picture; Kotlin has no direct counterpart to them.

`ClosureCodec` handles the closures that actually capture the script. On encode it keeps the owning script — found by walking the closure's `owner` chain
(`findOwningScript`, which follows `Closure.owner` and `ConfigureDelegate` links up to a `Script`) — keeps the owning script
of the closure's `thisObject` and dehydrates the closure body. This is the instance handled by the codec above.
The `delegate` is dropped, because the caller re-sets it (a task action rebinds it).
On decode it rehydrates with a null delegate and those two owners. When an owner doesn't resolve to a script, a `BrokenObject` — an empty `GroovyObjectSupport` — stands in,
so a lookup that would've reached a non-script owner falls through to the delegate as before.
This is what keeps a closure's calls to script-defined methods working after reuse, while its access to the build model still routes through the scrubbed script's broken target.

### Where they diverge, side by side

| Axis | Kotlin (`ScrubbableScriptCodec`) | Groovy (`GroovyScriptCodec`) |
|---|---|---|
| Recognized by | marker `ScrubbableScript` | base class `BasicScript` |
| Reconstruction | constructor bypass (`newBeanWithId`) | runs the no-arg constructor |
| Model classified by | declared field type | runtime value |
| Broken model | per-field proxies over dropped interface fields | one proxy over the internal model interface + `DynamicObjectAware` |
| Services kept working by | `unsafeLazy` `writeReplace` forcing at store | eager fields in `DefaultScript` + materialized fields in `ProjectScript` |
| Closures | ordinary captured field on the lambda | `ClosureCodec` owner-chain rehydration |

### Considered alternatives in the implementation

#### Cross-task shared script identity via the shared-object channel

Each task node serializes in its own isolate (`TaskNodeCodec.kt:309`, `withIsolate(OwnerTask)`), so a script captured by two tasks decodes to a copy per task. We could instead route the script through `writeSharedObject`, the build-wide channel that `BuildServiceParameterCodec` and `ValueSourceProviderCodec` already use (`ProviderCodecs.kt`), and get one shared instance back — closer to non-CC, where both actions share one live script.

We can cross this out due to many downsides:

* If tasks execute in parallel, the script code should be thread-safe, and most scripts are not.

* Other regular mutable state (except for build services and value sources) is also isolated per-task.

* The shared-object reader (`DefaultSharedObjectCodec`) decodes eagerly on a background thread in the `OwnerGradle` isolate, and it throws on reentrancy — a shared value whose graph references another
  not-yet-read shared value hits `"Recursive shared-object decode detected"`.

* A script's arbitrary field graph can easily hold a `ValueSourceProvider` or a build-service `Provider`, both shared objects, so this would turn working builds into order-dependent load failures.

* Decoding in `OwnerGradle` rather than the task isolate moves the `PropertyTrace` and `ProblemsListener` to build scope, so execution-time problems lose their per-task location.

* The parity it buys — one task's action mutating state another observes — is fragile and order-dependent anyway.

#### Groovy: drop the ProjectScript service overrides instead of materializing

Rather than materialize `logging`/`logger` into fields, `ProjectScript` could just stop overriding them and fall back to `DefaultScript`'s field-based versions.

Simpler, but the script's own logging manager (a fresh `createLoggingManager()` in the script services) is a different instance from `project.getLogging()`, and dropping the overrides changes the
live logger category and the manager that output capture runs against. That's a live behavior change outside the cache, so we materialize instead and leave the live path alone.

#### Instead of filtering the state, try to serialize it and rollback if it fails

It would be a potentially working alternative to attempt serializing all the fields of the script object, rolling back the field on failure and storing a failure
marker. It might be worth exploring this alternative once the machinery for CC encoding rollback is in place. The downside is that it blurs the line between the captured and the dropped state, not
reporting the dropped state until there is an attempt to use it. So some new state that fails to serialize might not appear in the tests early enough.

## Considered alternative approaches

* Treat the Kotlin script body as a method rather than script top-level scope, so top-level `val`s and `fun`s become ordinary locals with nothing to capture on a script object.
  This would not work since method bodies are more restricted than script scope — a method can only be called after it's declared, for one — so recompiling the body as a method changes what compiles
  at all, as noted on #22879.

* Staticalize the Project-independent members with a Kotlin compiler plugin — rewrite the methods and variables that don't
  touch `Project` so they no longer need the script instance. It's a heavier mechanism, it only helps Kotlin and it doesn't touch Groovy,
  so it's a bigger hammer than a codec-level scrubbing we can share across both DSLs. Deferred rather than ruled out.

* Make top-level `val`s true local variables under CC.
  Today a top-level `val` is an implicit property on the script object, not a local — the user guide's local-variables section arguably misdescribes this (https://docs.gradle.org/current/userguide/writing_build_scripts.html#sec:local_variables). If they were real locals, a lambda would capture the value directly and there'd be no script reference to scrub. This is probably the most principled fix, but it's a language-semantics change with compatibility fallout — `this.dest`-style property access would stop compiling, cross-block visibility changes — so it's out of scope here. Possible future direction (?).

## Won't do

No attempt to sanitize model references captured by user code (rather than the scripting machinery) – that intentionally stays CC-incompatible.

## Security implications

Not relevant.

## Build Scans

Not relevant.
