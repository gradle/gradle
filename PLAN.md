# Documentation in the Declarative Schema

## Goal

Make the declarative schema self-describing by carrying user-facing
documentation for each definition. Documentation is contributed as a
classpath resource — by plugin JARs for the types those plugins declare,
and by Gradle's own JARs for Gradle API types that appear in declarative
schemas — and grafted onto the schema at build time, so any schema
consumer (IDE tooling, schema viewers, generators) gets docs without an
extra channel.

## Non-goals

- Generating the documentation resources. They are produced at build time
  by a separate component (not part of this plan) that extracts
  documentation from source (KDoc/Javadoc/etc.) and writes the catalog
  JSON into each contributing JAR. This plan only defines the resource
  *shape* such generators must target, and the loader that reads it.
- Changing how schemas are built, serialized, or transported beyond adding
  a new optional field.
- Localization. The resource carries one language; multi-language is future
  work.

## Design decisions

### Documentation shape

A single optional `String` field on each definition type. No structured
summary/description split, no source links. Markdown is allowed by
convention; consumers may render or display as plain text.

```kotlin
val documentation: String?
```

`null` means "no documentation available" and is the default for any
definition not covered by a contributed resource.

### Definitions that carry documentation

Public schema interfaces in
`platforms/core-configuration/declarative-dsl-tooling-models/src/main/kotlin/org/gradle/declarative/dsl/schema/`:

- `DataClass`
- `DataProperty`
- `SchemaMemberFunction` (covers `DataMemberFunction` and `DataBuilderFunction`)
- `DataTopLevelFunction`
- `DataConstructor`
- `DataParameter`
- `EnumClass` — both the type itself and per-entry documentation

Parameter docs are the most valuable shape for IDE completion popups, and
enum-entry docs commonly explain the semantics of each value, so both
ship in v1.

`EnumClass` currently exposes entries as `entryNames: List<String>`. Rather
than promoting entries to a richer type, add a parallel
`entryDocumentation: Map<String, String>` field — keys are entry names,
values are docs. Entries without docs are simply absent from the map.

### Classpath resource

Fixed location, one per contributing JAR:

```
META-INF/declarative-dsl/documentation.json
```

Contributors are *any* JAR that declares schema-visible definitions —
plugin JARs for the types those plugins introduce, and Gradle's own
JARs for Gradle API types that appear in declarative schemas (e.g.
`Property<T>`, `Directory`, project feature interfaces declared by Gradle
itself).

The catalog file is *generated*, not hand-authored. For both plugin JARs
and Gradle JARs, a build-time generator (out of scope here) produces the
JSON and the build packages it into the final JAR at the fixed path
above. There is no `src/main/resources/META-INF/...` checked-in copy.

Discovered via `ClassLoader.getResources(...)` on the plugin classloader.
That call walks the parent chain, so resources in Gradle's JARs (sitting
above the plugin classloader) are picked up automatically without any
extra discovery code. Standard convention, same shape as
`META-INF/services/*`.

### Resource format

Kotlinx-serialization JSON whose tree mirrors `AnalysisSchema` so loading
is "deserialize and graft":

```json
{
  "types": {
    "org.example.Foo": {
      "documentation": "...",
      "properties": {
        "name": "..."
      },
      "functions": {
        "doX(java.lang.String)": {
          "documentation": "...",
          "parameters": { "x": "..." }
        }
      },
      "constructors": {
        "(java.lang.String)": {
          "documentation": "...",
          "parameters": { "name": "..." }
        }
      }
    }
  },
  "topLevelFunctions": {
    "org.example.helper(java.lang.String)": {
      "documentation": "...",
      "parameters": { "x": "..." }
    }
  },
  "enums": {
    "org.example.Color": {
      "documentation": "...",
      "entries": { "RED": "...", "BLUE": "..." }
    }
  }
}
```

Keys:

- Type keys: fully qualified Java type name (matches `FqName.qualifiedName`).
- Property keys: simple property name.
- Function keys: simple name + JVM-style parameter list using each
  parameter's `type.javaTypeName`, e.g. `doX(java.lang.String,int)`.
  Parameterless functions: `doX()`.
- Constructor keys: parameter list only, e.g. `(java.lang.String)`.
- Top-level function keys: fully qualified function name + parameter list.

The format evolves additively — new optional fields are tolerated by
kotlinx-serialization with `ignoreUnknownKeys`. If a future change ever
needs to be incompatible, it ships under a new resource path
(`documentation.v2.json`) rather than via an in-file version field.

### Conflict policy

When two JARs contribute documentation for the same key (including a
plugin JAR documenting a type defined by a Gradle JAR):

- Default: **last-wins**, with a single warning logged per conflicting key.
- Tightening to "owner-only" (a JAR can only document FQNs whose classes
  it defines) is a possible future change once usage patterns are clear.
  Under owner-only, a plugin would not be able to override Gradle's docs
  for a Gradle-defined type; under last-wins it can, which may be useful
  during transition periods.

### Loading integration

Build-time graft. After the analysis schema is built (in
`platforms/core-configuration/declarative-dsl-core/.../schemaBuilder/`),
a new step:

1. Reads all `META-INF/declarative-dsl/documentation.json` resources from
   the plugin classloader (`ClassLoaderScope.localClassLoader`).
2. Parses each into the catalog model.
3. Returns a new `AnalysisSchema` whose `Default*` instances carry the
   `documentation` field populated.

The schema remains an immutable value; nothing is resolved lazily.

The integration point is the schema-building path used by
`GradleProcessInterpretationSchemaBuilder` and the per-evaluation-step
schema construction in `evaluationSchema/`. The plugin classloader is
already in scope there.

#### Reference aliasing in the schema graph

`AnalysisSchema` holds some definitions in more than one place. The
top-level receiver appears both as `topLevelReceiverType: DataClass` and
(when it has an FQN) as an entry in `dataClassTypesByFqName`. A naive
grafter that only rebuilds the FQN map would leave `topLevelReceiverType`
pointing at the *old* `DataClass`, so consumers reading
`schema.topLevelReceiverType.documentation` would see `null` even though
the same type's entry in the map is populated.

The grafter must rebuild the schema in a single pass that updates every
reference consistently — typically by rebuilding the map first and then
resolving each direct reference (top-level receiver, generic
instantiations, etc.) from the rebuilt map. A test asserts that
`schema.topLevelReceiverType.documentation` and
`schema.dataClassTypesByFqName[fqn].documentation` agree.

## Implementation steps

Ordered so each step is reviewable on its own.

1. **Add `documentation: String?` to public schema interfaces.**
   Files in `declarative-dsl-tooling-models/.../schema/`. Add to:
   `DataClass`, `DataProperty`, `SchemaMemberFunction`, `DataTopLevelFunction`,
   `DataConstructor`, `DataParameter`, `EnumClass`. Also add
   `entryDocumentation: Map<String, String>` to `EnumClass`.

2. **Plumb through `Default*` implementations.**
   Files in `declarative-dsl-core/.../analysis/Default*`. Add field with
   `null` default. Update constructors and `copy`-style usages.

3. **Update kotlinx serialization.**
   `declarative-dsl-core/.../serialization/SchemaSerialization.kt`. Add
   the new field to each `@Serializable` shape, with a default so older
   payloads still deserialize.

4. **Define the catalog data model.**
   New `@Serializable` types in a dedicated package (likely
   `declarative-dsl-core/.../documentation/`) representing the JSON
   structure above.

5. **Implement the loader.**
   Service that takes a `ClassLoader` and returns a parsed catalog (merged
   across all resources, with conflict warnings). Pure function; no Gradle
   APIs needed beyond the classloader.

6. **Implement the grafter.**
   Function `(AnalysisSchema, DocumentationCatalog) -> AnalysisSchema`
   that returns a new schema with `documentation` filled on each matching
   definition. Walks the schema tree, looks up each key, copies the
   `Default*` value with the `documentation` set. Rebuild the FQN map
   first, then resolve every direct `DataClass` reference held by
   `AnalysisSchema` (top-level receiver, generic instantiations, etc.)
   from the rebuilt map so all aliases agree.

7. **Wire into schema building.**
   Call loader + grafter after schema construction in the project and
   settings interpretation paths. The plugin classloader is the input.

8. **Tests.**
   - Unit: catalog parsing, function-signature key building, grafter on a
     hand-built schema.
   - Aliasing: documenting the top-level receiver's FQN populates
     `documentation` on both `schema.topLevelReceiverType` and the same
     type's entry in `dataClassTypesByFqName`.
   - Integration: a fixture plugin shipping a `documentation.json` resource;
     assert the resulting schema carries the expected docs end-to-end.
   - Parent-classloader discovery: a fixture catalog placed in a JAR on
     the parent classloader (simulating a Gradle JAR) is found by the
     plugin-classloader scan and applied.
   - Conflict: two fixture JARs documenting the same key; assert
     last-wins and that a warning is emitted.

## Open questions for later

- Owner-only conflict policy (tightening from last-wins).
