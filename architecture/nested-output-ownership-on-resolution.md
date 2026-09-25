<!--
Copyright 2026 Gradle and contributors.

Licensed under the Creative Commons Attribution-Noncommercial-ShareAlike 4.0 International License.
You may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://creativecommons.org/licenses/by-nc-sa/4.0/

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Nested output ownership on resolution

Implementation on `asodja/nested-output-ownership`, based on master `353418a8fb6`.
This extends output ownership for scalar nested properties; the separate nested-input dependency proposal is not part of this branch.

## Contract

A managed bean supplied by a task's `@Nested Property<Foo>` can identify that task as the producer of its managed output properties. Resolving the property establishes the association before a caller or transformation sees the bean. Assignment does not inspect values, record candidates, or attach owners.

```groovy
abstract class Foo {
    @OutputFile abstract RegularFileProperty getResult()
}
abstract class Generate extends DefaultTask {
    @Nested abstract Property<Foo> getFoo()
}

def configuredFoo = objects.newInstance(Foo)
configuredFoo.result.set(layout.buildDirectory.file("result.txt"))
def generate = tasks.register("generate", Generate) { foo.set(configuredFoo) }

consumer.inputFile.set(generate.flatMap { it.foo.flatMap { it.result } })
// Also supported:
consumer.inputFile.set(generate.get().foo.get().result)
Property<Foo> foo = generate.get().foo
consumer.inputFile.set(foo.flatMap { it.result })
```

Two or more nested levels use the same mechanism. Direct managed getters and nested properties may be mixed. The final output keeps its bean as producer; the owner chain connects that bean to its task. Global `flatMap` dependency semantics do not change.

## Lifecycle

| Operation | Behavior |
| --- | --- |
| Create/access an eligible nested property | Attach declaration context; do not resolve its value. |
| `set`, `convention`, `unset`, or constructing a provider chain | Ordinary property behavior; no ownership inspection. |
| Resolve the property | Associate the returned managed bean with this declaration. Ordinary reads leave the property replaceable. |
| Query an output's producer or inspect its output declaration | Retain the resolved bean and finalize each containing nested property. Output locations retain their normal lazy lifecycle. |
| Resolve an output's task owner | Ignore replaced declarations; require a unique task among effective associations. |
| Store/reuse the configuration cache | Store bean values and declaration names, not live parent associations; reconstruct associations through restored declarations. |

Finalization happens when Gradle relies on an output declaration, rather than on the first ordinary read. It prevents graph discovery and execution from referring to different beans, including suppliers that construct a fresh bean on each query. Replacing a property after that point uses the existing final-property error. Replacing it before that point invalidates its old association without walking the old bean.

## Sharing and limits

Input-only beans remain shareable. Associations are retained without checking whether a type has outputs; actual output lookups require a unique owner. This handles runtime-selected nested types without static guesses or an extra bean walk. Several declarations on the same task are allowed.

Dependency inference from an output shared by two known tasks fails with both task names. Output inspection alone reports a deprecation warning, becoming an error in Gradle 10. An unvisited declaration is not discovered by this mechanism.

| Form | Support |
| --- | --- |
| Managed/decorated `@Nested Property<Foo>` containing a managed bean | Ownership and inference, including eligible final property getters. |
| Configuration-computed managed bean | Supported; the effective bean is retained when its outputs are used. |
| Managed bean with lazy input leaves or output filenames | Supported; leaf producer metadata is preserved. |
| Input-only shared bean | Supported; does not acquire output-finalization restrictions. |
| Raw bean reference bypassing its container | Does not establish ownership; works only if ownership was already established. Prefer the container provider chain. |
| Plain bean, standalone property, collection element, argument-provider list | Existing output tracking continues; no new ownership inference. |
| Structure requiring generated content | Unsupported. Existing premature-read checks still apply; content-producing supplier metadata cannot be used to claim a nested output. |

This does not promise diagnostics for hidden file reads or plain properties without output-role metadata. Artifact transforms and command-line argument provider collections receive no new ownership policy.

## Implementation

- The class generator attaches nested declaration context to eligible scalar properties. Existing managed output roles remain attached to their bean.
- `DefaultProperty` delegates nested ownership to a specialized `NestedPropertyState`, installed through its existing lifecycle-state reference when the declaration is attached. Ordinary properties and their state classes have no additional fields or allocations. The specialized state wraps the original state, preserving conventions and finalization flags without replacing the property or breaking aliases, even when attachment occurs after finalization.
- The specialized state attaches resolved values during ordinary and execution-time value calculation. A declaration binding tracks supplier and bean identity; producer lookup claims the current binding using normal property finalization. The wrapper retains the ownership context when its delegate transitions to the shared finalized state.
- `NestedObjectOwner` retains parent associations. Producer lookup follows these chains and detects conflicting tasks. Repeated associations use identity lookup; no task scans or assignment-time traversal are introduced.
- `PropertyCodec` preserves the nested property's calculation hook instead of serializing its backing supplier directly. Parent associations are transient, allowing shared input beans to be serialized independently of other tasks. Restored declarations reattach when resolved.
- The property walker checks ownership only at actual output declarations. Unowned outputs remain valid for tracking. Known conflicts receive the migration warning.
- Combined producer metadata preserves the distinction between task-state and task-content producers.

Behavioral coverage is in `NestedOutputPropertyIntegrationTest`; provider, generator, property-walker, and ownership unit tests cover the supporting mechanisms. Tests exercise dependency inference, multiple levels, replacement, sharing, unsupported structure, and configuration-cache storage and reuse.

## Validation

The state specialization passed 181 integration cases and 601 provider, generator, walker, and ownership unit tests, plus Java/Groovy style checks. Coverage includes the full scalar-property contract with nested state installed, six attachment-lifecycle cases, and a property finalized before attachment, including aliases and configuration-cache reuse. Documentation links were checked with the original implementation. This is targeted validation, not the full Gradle CI matrix.

Java instrumentation on the local JDK 25 measured an ordinary property at 40 bytes and its non-finalized state at 24 bytes, matching master. The first implementation measured 48 bytes for the property. The specialization adds a state wrapper only to actual nested declarations; these are shallow object sizes and depend on JVM layout settings.

A local stress comparison against the same master base used 1,000 tasks sharing a two-level input bean, with 100 explicit reads per task, two warmups, and five alternating samples. With the state specialization, median configuration time was 28.4 ms on master and 30.6 ms with the change; median elapsed build time was 0.491 s and 0.472 s. Both distributions also reused the configuration cache successfully for that scenario. These smoke measurements are not a replacement for controlled performance CI.
