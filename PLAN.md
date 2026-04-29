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
summary/description split, no source links.

```kotlin
val documentation: String?
```

`null` means "no documentation available" and is the default for any
definition not covered by a contributed resource.

**Content format: CommonMark.** Generators MUST emit valid CommonMark
strings (or `null` when there is no documentation). Consumers SHOULD
render the string as Markdown when displaying it; consumers that cannot
render Markdown MAY display the string verbatim, accepting that
formatting markers will be visible.

CommonMark — not GitHub-Flavored Markdown — is chosen because it is the
strict subset every renderer agrees on; GFM extensions like tables are
not portable across consumers.

There is no v1 syntax for cross-references between schema definitions
inside the doc (e.g. linking from `Foo.bar`'s doc to `Bar.baz`).
Generators may use any plain-text convention; resolving those references
is a consumer-side concern.

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

**Not documented (intentionally):** `ParameterizedTypeSignature` and
`ParameterizedTypeInstance` (e.g. `Property<T>`, `List<T>`,
`ListProperty<T>`). DCL users never type these names; they write the
*value* a property holds, not the wrapper type. Given a property
declared as `scope: Property<String>`, a DCL file says `scope = "..."`,
and the schema describes it as "a property named `scope` that can have
`String` values" — the `Property` wrapper is implementation detail. The
property itself (`DataProperty.documentation`) is the right place for
user-facing doc; the type wrapping its value is not. Same reasoning for
`List<String>` etc. — the property doc covers the user-visible meaning.

Also intentionally not documented:

- **`ExternalObjectProviderKey`** — an external object's user-visible
  meaning is its type, which is documented via the `DataClass` it
  references. The key itself is just a named pointer.
- **`AssignmentAugmentation`** — the `+=`-style syntax; its underlying
  `function: DataTopLevelFunction` is already documentable through the
  function field, which is sufficient.
- **`defaultImports`** — a list of FQNs, not a definition.

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
    "org.example.HelperKt.helper(java.lang.String)": {
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

Keys are written in **JVM binary form** throughout — definition types may
be authored in any JVM language, and the JVM form is the language-neutral
canonical name available on the schema (`ClassDataType.javaTypeName`,
`DataTopLevelFunction.ownerJvmTypeName`).

- **Type keys**: JVM binary FQN of the class. Top-level: `org.example.Bar`.
  Nested: `org.example.Outer$Inner` (dollar-separated, not dotted).
- **Property keys**: simple property name.
- **Function keys**: `simpleName(parameter-list)`. Parameter list is
  comma-separated, no spaces. Parameterless: `simpleName()`.
- **Constructor keys**: `(parameter-list)` only — no name. Default
  constructor: `()`.
- **Top-level function keys**: `<ownerJvmTypeName>.<simpleName>(parameter-list)`.
  For Kotlin top-level functions, `ownerJvmTypeName` is the synthetic
  file class (e.g. `org.example.HelperKt`). For Java static methods, the
  declaring class.

**Parameter type fragments** (each entry in a parameter list) project
`DataParameter.type: DataTypeRef` to a JVM-form string:

- Class types (`DataTypeRef.Name` and `NameWithArgs`): the underlying
  `ClassDataType.javaTypeName`. Parameterized types are **erased** —
  `List<String>` and `List<Int>` both project to `java.util.List`.
  Erasure is inherent to the JVM and matches what the schema already
  exposes via `ParameterizedTypeInstance.javaTypeName`.
- Primitives (`DataTypeRef.Type`): a fixed table.

  | Primitive type   | Fragment           |
  |------------------|--------------------|
  | `IntDataType`    | `int`              |
  | `LongDataType`   | `long`             |
  | `BooleanDataType`| `boolean`          |
  | `StringDataType` | `java.lang.String` |
  | `UnitType`       | `void`             |
- **Varargs**: a vararg parameter is represented in the schema as
  `NameWithArgs` over `VarargSignature`. Project as the element type's
  fragment plus `[]`, e.g. `vararg s: String` → `java.lang.String[]`.

**Deferred for v1:**

- Type-variable parameters (`fun <T> foo(t: T)`). `TypeVariableUsage` only
  carries `variableId: Long`; no clean printable JVM form without resolving
  the owning generic signature. Rare by design in DCL — add a rule when
  it comes up.
- `NullType` (the schema's `DataType.NullType`). Not currently used in
  practice (`// TODO: implement nulls?` in [DataType.kt:80](platforms/core-configuration/declarative-dsl-tooling-models/src/main/kotlin/org/gradle/declarative/dsl/schema/DataType.kt#L80)).

#### Parameter documentation keys

Inside each function/constructor/top-level-function entry, parameter docs
are keyed by **simple parameter name**:

```json
"doX(java.lang.String,int)": {
  "documentation": "...",
  "parameters": { "scope": "...", "depth": "..." }
}
```

`DataParameter.name` is nullable. A parameter without a name cannot carry
documentation in v1: it is simply absent from the `parameters` map, and
the grafted schema leaves its `documentation` null. The format can be
extended additively later (e.g. an optional `parametersByIndex` block) if
this becomes a problem in practice.

The main source of nameless parameters is Java compiled without
`-parameters`. As a separate follow-up (out of scope here), the
`java-gradle-plugin` plugin should default `JavaCompile.options.compilerArgs`
to include `-parameters`, so that plugins implemented in Java always
ship parameter names and stay fully documentable.

The format evolves additively — new optional fields are tolerated by
kotlinx-serialization with `ignoreUnknownKeys`. If a future change ever
needs to be incompatible, it ships under a new resource path
(`documentation.v2.json`) rather than via an in-file version field.

### Conflict policy

When two JARs contribute documentation for the same key (including a
plugin JAR documenting a type defined by a Gradle JAR):

- Default: **last-wins**. The losing entry is dropped from the merged
  catalog and surfaced in the diagnostics for the resource it came from
  (see *Diagnostics* below).
- Tightening to "owner-only" (a JAR can only document FQNs whose classes
  it defines) is a possible future change once usage patterns are clear.
  Under owner-only, a plugin would not be able to override Gradle's docs
  for a Gradle-defined type; under last-wins it can, which may be useful
  during transition periods.

### Diagnostics

The loader is **never fatal**. Documentation is purely additive — its
absence degrades tooling but cannot break correctness — so the loader
warns rather than fails. Three classes of issue are detected:

- **Malformed JSON.** The resource is unparseable in part or whole.
  Generator bug; user cannot fix it.
- **Orphan keys.** A documented key (type, member, parameter, etc.)
  doesn't match anything in the schema. Typical cause: the contributing
  plugin renamed or removed a definition without updating its catalog.
- **Conflicts where this resource lost.** The same key was contributed
  by another resource that took precedence under last-wins. Useful to
  diagnose "why aren't my docs showing?".

**One warning log statement is emitted per resource that has at least
one issue**, summarizing all of its issues in a single message. A clean
catalog produces no log output. The warning is emitted by a single
`Logger.warn(...)` call — Gradle's standard build logger, not the
Problems API. Documentation issues are routine generator-side hygiene,
not something that should surface as a structured build problem.

#### Warning text format

Multi-line, indented body, no namespace prefix (Gradle's logger already
brands WARN output). The resource is identified by its JAR file path —
the trailing `!/META-INF/declarative-dsl/documentation.json` is the
same for every resource and is omitted as noise.

When the resource fails to parse, the parse-error line is the only
content (no other categories can be enumerated):

```
Documentation issues in <jar-path>:
  parse error: <kotlinx-serialization message>
```

When the resource parses but has other issues, each category with any
issue appears on its own indented line, in this order:

```
Documentation issues in <jar-path>:
  orphan keys (<n>): <k1>, <k2>, …, <k10> (+<n-10> more)
  keys overridden by other catalogs (<m>): <k1>, <k2>, …, <k10> (+<m-10> more)
```

Categories with zero issues are omitted entirely.

Each category truncates its key list at **10** keys; the count in
parentheses is the full count, and `(+N more)` signals the remainder.
Individual keys are listed verbatim — `Bar.compute(java.lang.String,int)`
for member functions, `(java.lang.String)` for constructors,
`org.example.HelperKt.helper()` for top-level functions.

Worked example (a JAR with two orphan keys and one overridden key):

```
Documentation issues in /Users/alice/.gradle/caches/.../my-plugin-1.0.jar:
  orphan keys (2): org.example.Foo.removed(), org.example.Bar
  keys overridden by other catalogs (1): org.example.Baz
```

**Forward-compat is silent.** Unknown top-level JSON fields produced by
a newer toolchain are accepted via kotlinx-serialization's
`ignoreUnknownKeys = true` and do *not* generate a warning — the format
is designed to evolve additively, and warning here would mean every old
loader complaining about every new catalog.

### Generator contract

Catalog generation is a separate piece of work, owned by another internal
contributor. This subsection is the contract that work targets — concise
enough to skim without reading the rest of the plan.

**Status: exploratory.** The format is for exploration and demonstration.
**No stability guarantees yet** — any rule below may change as we learn
from the first generator and the first consumers. Generators should
expect to track changes for now.

#### What to produce

One file per JAR that contributes definitions:

- Path: `META-INF/declarative-dsl/documentation.json`
- Encoding: UTF-8 JSON.
- Top-level object with optional keys: `types`, `enums`,
  `topLevelFunctions`. Any key may be absent if the JAR contributes
  nothing in that key space.

All `documentation` values are **CommonMark** strings (or omitted /
`null` when absent).

#### `types[<jvm-fqn>]`

Key: JVM binary FQN of a `DataClass`. Top-level: `org.example.Foo`.
Nested: `org.example.Outer$Inner` (dollar-separated).

Value: object with optional members:

- `documentation` — CommonMark string for the type itself.
- `properties` — map of property simple-name → CommonMark string.
- `functions` — map of function key → function-doc object (see below).
- `constructors` — map of constructor key → function-doc object.

#### `enums[<jvm-fqn>]`

Key: JVM binary FQN of an `EnumClass`.

Value: object with optional members:

- `documentation` — CommonMark string for the enum type.
- `entries` — map of enum entry simple-name (matching `EnumClass.entryNames`)
  → CommonMark string.

#### `topLevelFunctions[<owner>.<name>(args)]`

Key: `<ownerJvmTypeName>.<simpleName>(<jvm-form-args>)`. For Kotlin
top-level functions, `ownerJvmTypeName` is the synthetic file class
(e.g. `org.example.HelperKt`). For Java statics, the declaring class.

Value: function-doc object.

#### Function-doc object

Used as the value type in `functions`, `constructors`, and
`topLevelFunctions`. Optional members:

- `documentation` — CommonMark string for the function.
- `parameters` — map of parameter simple-name → CommonMark string.
  Nameless parameters cannot be documented in v1.

#### Function and constructor key syntax

- Member function: `<simpleName>(<jvm-form-args>)`, e.g. `compute(java.lang.String,int)`.
- Constructor: `(<jvm-form-args>)` — no name. Default: `()`.
- Args list is comma-separated, **no whitespace**.

#### JVM-form parameter type fragments

Each comma-separated piece in an args list:

- Class types — JVM binary FQN: `java.lang.String`, `org.example.Foo`,
  `org.example.Outer$Inner`.
- Parameterized types — erased to the raw class JVM name:
  `java.util.List` for `List<String>` and `List<Int>` alike.
- Primitives — fixed table: `int`, `long`, `boolean`, `java.lang.String`,
  `void`.
- Vararg — element-type fragment + `[]`: `vararg s: String` →
  `java.lang.String[]`.

#### Loader behaviour generators can rely on

- The loader is non-fatal. Bad catalogs warn, never fail the build.
- Conflict resolution is last-wins; the losing resource gets a per-resource
  warning.
- Orphan keys (keys that do not address an actual schema definition) are
  warned and dropped.
- Unknown JSON fields are silently ignored (forward-compat).

#### Currently out of scope

- Documenting parameterized type signatures (`Property<T>`, `List<T>`, etc.).
- Documenting external objects.
- Per-parameter documentation for nameless parameters.
- Cross-reference syntax between definitions.

### Loading integration

The analysis schema is **build-scoped**: a single schema governs every
DCL file in the build, including all project DCL files, and is fully
determined once the settings plugins block has been applied. After
`PluginsInterpretationSequenceStep.whenEvaluated` calls
`pluginApplicator.applyPlugins(...)` and locks `targetScope`
([PluginsInterpretationSequenceStep.kt:115-117](platforms/core-configuration/declarative-dsl-provider/src/main/kotlin/org/gradle/internal/declarativedsl/settings/PluginsInterpretationSequenceStep.kt#L115)),
`settings.classLoaderScope.localClassLoader` has every contributing JAR
on its classpath — both the applied plugin JARs and Gradle's own JARs
in the parent chain.

The loading flow:

1. Once `targetScope` is locked, the loader calls
   `classLoader.getResources("META-INF/declarative-dsl/documentation.json")`
   to enumerate every catalog on the classpath, parses each entry, and
   merges them under the conflict policy below.
2. The grafter rebuilds the analysis schema with the catalog applied,
   producing a single immutable `AnalysisSchema` that carries the
   documentation.

This grafted schema is the same value used to evaluate every DCL file in
the build — there is no project-level re-load and no project-level
re-graft. Per-project plugin discovery is not a thing in DCL: schema
contribution is settings-time only.

Schema-building remains pure (`declarative-dsl-core`). Resource discovery
and the wiring to `ClassLoaderScope` / `PluginsInterpretationSequenceStep`
live in `declarative-dsl-provider`.

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
   the new field to each `@Serializable` shape with the same default
   as on the `Default*` impl (`null` / `emptyMap()`).

4. **Define the catalog data model.**
   New `@Serializable` types in a dedicated package (likely
   `declarative-dsl-core/.../documentation/`) representing the JSON
   structure above.

5. **Implement the loader.**
   `DocumentationCatalogLoader` in `declarative-dsl-provider`. Signature:
   `(ClassLoader, AnalysisSchema) → DocumentationCatalog`. The schema
   parameter is the un-grafted schema, used for orphan-key validation.
   Responsibilities, in order: enumerate
   `META-INF/declarative-dsl/documentation.json` resources via
   `getResources(...)`; parse each (collect parse-error issues per
   resource); merge with the last-wins conflict policy (collect
   override-loser issues per resource); drop any catalog entries whose
   keys don't resolve in the schema (collect orphan issues per
   resource); emit one `Logger.warn(...)` per resource that has any
   issues, with the per-resource summary format from *Diagnostics*.
   Returns a clean catalog: every entry is guaranteed to address an
   actual schema definition.

6. **Implement the grafter.**
   Pure function `graftDocumentation(AnalysisSchema, DocumentationCatalog)
   → AnalysisSchema` in `declarative-dsl-core`. No Gradle dependencies,
   no `Logger`, no diagnostics — the catalog is already validated by the
   loader, so every entry is known to match a schema definition. Returns
   a new schema with `documentation` filled on each matching definition.
   Walks the schema tree, looks up each key, copies the `Default*` value
   with the `documentation` set. Rebuild the FQN map first, then resolve
   every direct `DataClass` reference held by `AnalysisSchema`
   (top-level receiver, generic instantiations, etc.) from the rebuilt
   map so all aliases agree.

7. **Wire into schema building.**
   Hook the loader into `PluginsInterpretationSequenceStep.whenEvaluated`
   (or an immediately-following step) so the catalog is read from
   `settings.classLoaderScope.localClassLoader` right after
   `targetScope.lock()`. The grafter produces the single build-scoped
   `AnalysisSchema` that subsequent DCL evaluation uses for every script
   in the build.

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
