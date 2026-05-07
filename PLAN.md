# Documentation in the Declarative Schema

## Goal

Make the declarative schema self describing by carrying user facing
documentation for each definition. Documentation is contributed as a
classpath resource (by plugin JARs for the types those plugins declare,
and by Gradle's own JARs for Gradle API types that appear in declarative
schemas) and grafted onto the schema at build time, so any schema
consumer (IDE tooling, schema viewers, generators) gets docs without an
extra channel.

## Non goals

- Generating the documentation resources. They are produced at build time
  by a separate component (not part of this plan) that extracts
  documentation from source (KDoc/Javadoc/etc.) and writes the catalog
  JSON into each contributing JAR. This plan only defines the resource
  *shape* such generators must target, and the loader that reads it.
- Changing how schemas are built, serialized, or transported beyond
  adding a new optional field.
- Localization. The resource carries one language; multi language is
  future work.

## Design decisions

### Documentation shape

Documentation is carried as a new `SchemaItemMetadata` variant rather
than as a direct field on each schema interface. This keeps the schema
API as a stable shape (no public field additions on existing
interfaces) and matches the existing pattern where metadata is the
extension point for cross cutting per item information.

The new variant lives alongside the others in
`org.gradle.declarative.dsl.schema.SchemaItemMetadata`:

```kotlin
interface SchemaDocumentation : SchemaItemMetadata {
    val text: String?                     // main doc for the item
    val parts: Map<String, String>        // sub keyed docs (entries, parameters, …)
}
```

`text` is the doc for the item itself (a class, an enum, a property,
a function). `parts` is a map of sub keyed docs whose meaning depends
on the item kind:

| Item kind                | `text`         | `parts`                         |
|--------------------------|----------------|----------------------------------|
| `DataClass`              | type doc       | empty                           |
| `EnumClass`              | enum type doc  | entry name → entry doc          |
| `SchemaMemberFunction`   | function doc   | parameter name → parameter doc  |
| `DataProperty`           | property doc   | empty                           |

Items with no documentation simply have no `SchemaDocumentation`
instance in their `metadata` list (rather than a present instance with
all nulls/empties). Consumers read with:

```kotlin
val docs = item.metadata.filterIsInstance<SchemaDocumentation>().firstOrNull()
docs?.text
docs?.parts?.get("x")
```

The grafter ensures **at most one** `SchemaDocumentation` per item:
if a documented item already has one (e.g. from a mirror pass), an
explicit catalog entry replaces it. Consumers can rely on `firstOrNull`.

**Content format: CommonMark.** Generators MUST emit valid CommonMark
strings. Consumers SHOULD render the string as Markdown when
displaying it; consumers that cannot render Markdown MAY display the
string verbatim, accepting that formatting markers will be visible.

CommonMark, not GitHub Flavored Markdown, is chosen because it is the
strict subset every renderer agrees on; GFM extensions like tables are
not portable across consumers.

There is no v1 syntax for cross references between schema definitions
inside the doc (e.g. linking from `Foo.bar`'s doc to `Bar.baz`).
Generators may use any plain text convention; resolving those
references is a concern for the consumer.

### Definitions that carry documentation

Public schema interfaces in
`platforms/core-configuration/declarative-dsl-tooling-models/src/main/kotlin/org/gradle/declarative/dsl/schema/`
that may carry a `SchemaDocumentation` entry in their `metadata` list:

- `DataClass`
- `DataProperty`
- `SchemaMemberFunction` (covers `DataMemberFunction` and `DataBuilderFunction`)
- `EnumClass`

Each of these already exposes `metadata: List<SchemaItemMetadata>`
(directly or via the `ClassDataType` supertype); no public interface
changes are needed.

**Parameter docs flow through the function's `parts` map.**
`DataParameter` does **not** carry documentation directly; per
parameter docs live in the parent `SchemaMemberFunction`'s
`SchemaDocumentation.parts`, keyed by parameter name. This keeps the
schema interface surface untouched.

**Enum entry docs flow through the enum's `parts` map.** No parallel
`entryDocumentation` field on `EnumClass`; entry docs live in the enum's
`SchemaDocumentation.parts`, keyed by entry name (matching
`EnumClass.entryNames`).

**JavaBean accessors and Kotlin properties become a single `DataProperty`.**
The schema's `PropertyExtractor` walks JavaBean getters with the `get`
prefix and Kotlin properties, returning one `DataProperty` per
accessor pair: name is the bean stripped or Kotlin name (`x`); a
matching setter (`setX(T)`) is consumed and folds into the property's
`ReadWrite` mode rather than appearing as a separate member function.
The catalog mirrors this:

- A `properties[<name>]` doc covers both read and write. There is no
  separate setter doc.
- A `functions[setX(T)]` entry would be silently dropped: the schema
  has no such function, so the loader filters it out without a
  warning.

**Only the `get` prefix is bean folded.** `isJavaBeanGetter`
([ClassMembersForSchema.kt:434](platforms/core-configuration/declarative-dsl-core/src/main/kotlin/org/gradle/internal/declarativedsl/schemaBuilder/ClassMembersForSchema.kt#L434))
matches `getXxx` only. A `boolean isReady()` getter does **not** become
a property `ready`; it appears as a member function `isReady()` in the
schema. The catalog must document it under `functions["isReady()"]`,
not under `properties.ready`. (Generators that follow Java's bean
convention naively will produce property entries that the loader
silently drops as not present in the schema.)

**Getters returning a complex type also synthesize a configuring
function.** `GetterBasedConfiguringFunctionExtractor`
([FunctionExtractor.kt:353](platforms/core-configuration/declarative-dsl-core/src/main/kotlin/org/gradle/internal/declarativedsl/schemaBuilder/FunctionExtractor.kt#L353))
emits a zero parameter `DataMemberFunction` with `simpleName` equal to
the property name and `semantics = AccessAndConfigure(...)` whenever a
getter (or Kotlin read only property) returns a configurable nested
type. So `getTestReports(): TestReports` (no setter) yields:

1. A `DataProperty("testReports", valueType = TestReports, ...)`.
2. A `DataMemberFunction("testReports"(), AccessAndConfigure)` carrying
   `ConfigureFromGetterOrigin` metadata that records the original
   getter.

In DCL the user writes `testReports { ... }`, which resolves to the
configuring function. The catalog only documents the property side
(`properties[testReports]`); the grafter mirrors the property's
`SchemaDocumentation` onto the synthesized configuring function,
recognising it via the `ConfigureFromGetterOrigin` metadata. Mirror is
"fill if absent", so an explicit `functions[testReports()]` entry,
if present, still wins. Generators should not emit such an explicit
entry: it would duplicate the property doc and risk diverging strings.

**Inherited members get their own `DataProperty` / `DataMemberFunction`
instances.** The schema runs `PropertyExtractor` and `FunctionExtractor`
per type; for a subtype, inherited members are included in the
extraction (sourced via `mergeMembersBySignature` from the supertype's
declared members). So `CheckstyleSourceSetDefinition` ends up with its
own `DataProperty("ignoreFailures", ...)` instance distinct from the
one on `CheckstyleDefinition`. **Generators emit docs at every type
that exposes the member, including inherited ones.** This works
naturally with KDoc/Javadoc tooling (Dokka, javadoc), which resolves
inheritance at extraction time: asking "what is the doc for
`Foo.foo()`?" returns the inherited doc transparently. The catalog
ends up with an entry for each subtype site; the grafter applies them
directly without any inheritance walk.

This means **the doc pipeline does not require the schema to carry
declaring type origin metadata.** Direct per type emission, paired
with the loader's silent filtering of catalog entries that don't
match the schema (#2), covers inheritance without a grafter side
mirror or schema side change. The same is true for members
contributed via generic supertypes (e.g. `Definition<T>`): they are
not present in the schema as proper `DataClass`es, but their members
appear as members of the concrete subtype, where the generator emits
docs naturally.

**Not documented in v1:** `ParameterizedTypeSignature` and
`ParameterizedTypeInstance` (e.g. `Property<T>`, `List<T>`,
`ListProperty<T>`). The v1 demos do not exercise these as documented
definitions, and the long term plan is to derive their
`SchemaDocumentation` from the class they originate from rather than
have generators emit it directly. See *Postponed for future
iterations* for the long term direction.

Also intentionally not documented:

- **`ExternalObjectProviderKey`**. An external object's user visible
  meaning is its type, which is documented via the `DataClass` it
  references. The key itself is just a named pointer.
- **`AssignmentAugmentation`**. The `+=` style syntax. Out of scope
  for v1 along with top level functions (see *Postponed for future
  iterations*).
- **`defaultImports`**. A list of FQNs, not a definition.

### Classpath resource

Fixed location, one per contributing JAR:

```
META-INF/declarative-dsl/documentation.json
```

Contributors are *any* JAR that declares schema visible definitions:
plugin JARs for the types those plugins introduce, and Gradle's own
JARs for Gradle API types that appear in declarative schemas (e.g.
`Property<T>`, `Directory`, project feature interfaces declared by
Gradle itself).

The catalog file is *generated*, not hand authored. For both plugin
JARs and Gradle JARs, a generator running at build time (out of scope
here) produces the JSON and the build packages it into the final JAR
at the fixed path above. There is no `src/main/resources/META-INF/...`
checked in copy.

Discovered via `ClassLoader.getResources(...)` on the plugin
classloader. That call walks the parent chain, so resources in Gradle's
JARs (sitting above the plugin classloader) are picked up automatically
without any extra discovery code. Standard convention, same shape as
`META-INF/services/*`.

### Resource format

Kotlinx serialization JSON whose tree mirrors `AnalysisSchema` so
loading is "deserialize and graft":

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
      }
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

Keys are written in **JVM binary form** throughout. Definition types
may be authored in any JVM language, and the JVM form is the language
neutral canonical name available on the schema
(`ClassDataType.javaTypeName`).

- **Type keys**: JVM binary FQN of the class. Top level:
  `org.example.Bar`. Nested: `org.example.Outer$Inner` (dollar
  separated, not dotted).
- **Property keys**: simple property name.
- **Function keys**: `simpleName(parameter list)`. Parameter list is
  comma separated, no spaces. Parameterless: `simpleName()`.

**Parameter type fragments** (each entry in a parameter list) project
`DataParameter.type: DataTypeRef` to a JVM form string:

- Class types (`DataTypeRef.Name` and `NameWithArgs`): the underlying
  `ClassDataType.javaTypeName`. Parameterized types are **erased**, so
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
  fragment plus `[]`, e.g. `vararg s: String` becomes
  `java.lang.String[]`.

**Deferred for v1:**

- Type variable parameters (`fun <T> foo(t: T)`). `TypeVariableUsage`
  only carries `variableId: Long`; no clean printable JVM form without
  resolving the owning generic signature. Rare by design in DCL. Add a
  rule when it comes up.
- `NullType` (the schema's `DataType.NullType`). Not currently used in
  practice (`// TODO: implement nulls?` in [DataType.kt:80](platforms/core-configuration/declarative-dsl-tooling-models/src/main/kotlin/org/gradle/declarative/dsl/schema/DataType.kt#L80)).

#### Parameter documentation keys

Inside each function entry, parameter docs are keyed by **simple
parameter name**:

```json
"doX(java.lang.String,int)": {
  "documentation": "...",
  "parameters": { "scope": "...", "depth": "..." }
}
```

`DataParameter.name` is nullable. A parameter without a name cannot
carry documentation in v1: it is simply absent from the `parameters`
map, and the grafted schema leaves its `documentation` null. The format
can be extended additively later (e.g. an optional `parametersByIndex`
block) if this becomes a problem in practice.

The main source of nameless parameters is Java compiled without
`-parameters`. As a separate followup (out of scope here), the
`java-gradle-plugin` plugin should default
`JavaCompile.options.compilerArgs` to include `-parameters`, so that
plugins implemented in Java always ship parameter names and stay fully
documentable.

The format evolves additively. New optional fields are tolerated by
kotlinx-serialization with `ignoreUnknownKeys`. If a future change ever
needs to be incompatible, it ships under a new resource path
(`documentation.v2.json`) rather than via an in file version field.

### Conflict policy

When two JARs contribute documentation for the same key (including a
plugin JAR documenting a type defined by a Gradle JAR):

- Default: **last wins**. The losing entry is dropped from the merged
  catalog and surfaced in the diagnostics for the resource it came from
  (see *Diagnostics* below).
- Tightening to "owner only" (a JAR can only document FQNs whose
  classes it defines) is a possible future change once usage patterns
  are clear. Under owner only, a plugin would not be able to override
  Gradle's docs for a Gradle defined type; under last wins it can,
  which may be useful during transition periods.

### Diagnostics

The loader is **never fatal**. Documentation is purely additive: its
absence degrades tooling but cannot break correctness, so the loader
warns rather than fails. Two classes of issue surface as warnings:

- **Malformed JSON.** The resource is unparseable in part or whole.
  Generator bug; user cannot fix it.
- **Conflicts where this resource lost.** The same key was contributed
  by another resource that took precedence under last wins. Useful to
  diagnose "why aren't my docs showing?".

**Orphan keys are silent.** A documented key that doesn't match
anything in the current schema is dropped without a warning. With #2's
"include all docs in catalog" posture, an entry not present in the
current schema may be perfectly valid for another build's schema (a
hidden member, a type only used by a depending plugin, etc.); a
warning here would fire uniformly on benign omissions and real
generator bugs alike, so the signal is not worth the noise. Generator
bugs (typos in member names, etc.) surface only when someone notices
missing docs and looks; the tradeoff is acceptable given doc is
additive.

**One warning log statement is emitted per resource that has at least
one issue**, summarizing all of its issues in a single message. A clean
catalog produces no log output. The warning is emitted by a single
`Logger.warn(...)` call, Gradle's standard build logger, not the
Problems API. Documentation issues are routine generator side hygiene,
not something that should surface as a structured build problem.

#### Warning text format

Multi line, indented body, no namespace prefix (Gradle's logger already
brands WARN output). The resource is identified by its JAR file path:
the trailing `!/META-INF/declarative-dsl/documentation.json` is the
same for every resource and is omitted as noise.

When the resource fails to parse, the parse error line is the only
content (no other categories can be enumerated):

```
Documentation issues in <jar path>:
  parse error: <kotlinx serialization message>
```

When the resource parses but has other issues, the conflict category
appears on its own indented line:

```
Documentation issues in <jar path>:
  keys overridden by other catalogs (<m>): <k1>, <k2>, …, <k10> (+<m−10> more)
```

A resource with no issues produces no log output.

The category truncates its key list at **10** keys; the count in
parentheses is the full count, and `(+N more)` signals the remainder.
Individual keys are listed verbatim, e.g.
`Bar.compute(java.lang.String,int)` for member functions and the
fully qualified type name for type and enum keys.

Worked example (a JAR with one overridden key):

```
Documentation issues in /Users/alice/.gradle/caches/.../my-plugin-1.0.jar:
  keys overridden by other catalogs (1): org.example.Baz
```

**Forward compat is silent.** Unknown top level JSON fields produced by
a newer toolchain are accepted via kotlinx-serialization's
`ignoreUnknownKeys = true` and do *not* generate a warning. The format
is designed to evolve additively, and warning here would mean every old
loader complaining about every new catalog.

### Generator contract

Catalog generation is a separate piece of work, owned by another
internal contributor. This subsection is the contract that work
targets, concise enough to skim without reading the rest of the plan.

**Status: exploratory.** The format is for exploration and
demonstration. **No stability guarantees yet.** Any rule below may
change as we learn from the first generator and the first consumers.
Generators should expect to track changes for now.

#### What to produce

One file per JAR that contributes definitions:

- Path: `META-INF/declarative-dsl/documentation.json`
- Encoding: UTF-8 JSON.
- Top level object with optional keys: `types`, `enums`. Any key may
  be absent if the JAR contributes nothing in that key space.

All `documentation` values are **CommonMark** strings (or omitted /
`null` when absent).

#### `types[<jvm fqn>]`

Key: JVM binary FQN of a `DataClass`. Top level: `org.example.Foo`.
Nested: `org.example.Outer$Inner` (dollar separated).

Value: object with optional members:

- `documentation`: CommonMark string for the type itself.
- `properties`: map of property simple name to CommonMark string.
- `functions`: map of function key to function doc object (see below).

#### `enums[<jvm fqn>]`

Key: JVM binary FQN of an `EnumClass`.

Value: object with optional members:

- `documentation`: CommonMark string for the enum type.
- `entries`: map of enum entry simple name (matching
  `EnumClass.entryNames`) to CommonMark string.

#### Function doc object

Used as the value type in `functions`. Optional members:

- `documentation`: CommonMark string for the function.
- `parameters`: map of parameter simple name to CommonMark string.
  Nameless parameters cannot be documented in v1.

#### Function key syntax

- `<simpleName>(<jvm form args>)`, e.g.
  `compute(java.lang.String,int)`.
- Args list is comma separated, **no whitespace**.
- The trailing **configure block lambda** is excluded from the args
  list. For a function with configure semantics like
  `feature(name: String, action: Action<Foo>)` the schema drops the
  lambda, so the catalog key is `feature(java.lang.String)`, not
  `feature(java.lang.String,org.gradle.api.Action)`. The schema's own
  rule lives in `DefaultFunctionExtractor.memberFunction`
  ([FunctionExtractor.kt:142](platforms/core-configuration/declarative-dsl-core/src/main/kotlin/org/gradle/internal/declarativedsl/schemaBuilder/FunctionExtractor.kt#L142)).

#### JVM form parameter type fragments

Each comma separated piece in an args list:

- Class types: JVM binary FQN. `java.lang.String`, `org.example.Foo`,
  `org.example.Outer$Inner`.
- Parameterized types: erased to the raw class JVM name.
  `java.util.List` for `List<String>` and `List<Int>` alike.
- Primitives: fixed table. `int`, `long`, `boolean`, `java.lang.String`,
  `void`.
- Vararg: element type fragment plus `[]`. `vararg s: String` becomes
  `java.lang.String[]`.

#### Visibility annotations: ignore them

Definition types may carry `@HiddenInDefinition` and
`@VisibleInDefinition` annotations to control what reaches the schema.
**Generators emit docs for all members regardless of these
annotations.** It is not known upfront which types and members will
end up in any given build's schema (a hidden member can be overridden
as visible in a subtype; a plugin can expose types that its own schema
doesn't use but a depending plugin's schema does). Including all docs
in the catalog and letting the loader silently filter against the
actual built schema is simpler and works across all such cases. Entries
the schema doesn't include are dropped without a warning.

#### Configure functions synthesized from getters

Do not emit a `functions["name()"]` entry for a getter that the schema
turns into a configure block (a getter without a setter, returning a
configurable complex type, e.g. `getTestReports(): TestReports`).
Document only the property; the grafter mirrors the property's doc
onto the synthesized configuring function. Emitting both risks
diverging strings and adds no information. See *Definitions that
carry documentation* for the full rationale.

#### Loader behaviour generators can rely on

- The loader is non fatal. Bad catalogs warn, never fail the build.
- Conflict resolution is last wins; the losing resource gets a per
  resource warning.
- Orphan keys (keys that do not address a definition in the current
  build's schema) are silently dropped, with no warning.
- Unknown JSON fields are silently ignored (forward compat).

#### Currently out of scope

- Documenting parameterized type signatures (`Property<T>`, `List<T>`,
  etc.).
- Documenting external objects.
- Per parameter documentation for nameless parameters.
- Cross reference syntax between definitions.

### Loading integration

The analysis schema is **build scoped**: a single schema governs every
DCL file in the build, including all project DCL files, and is fully
determined once the settings plugins block has been applied. After
`PluginsInterpretationSequenceStep.whenEvaluated` calls
`pluginApplicator.applyPlugins(...)` and locks `targetScope`
([PluginsInterpretationSequenceStep.kt:115-117](platforms/core-configuration/declarative-dsl-provider/src/main/kotlin/org/gradle/internal/declarativedsl/settings/PluginsInterpretationSequenceStep.kt#L115)),
`settings.classLoaderScope.localClassLoader` has every contributing JAR
on its classpath, both the applied plugin JARs and Gradle's own JARs
in the parent chain.

The loading flow:

1. Once `targetScope` is locked, the loader calls
   `classLoader.getResources("META-INF/declarative-dsl/documentation.json")`
   to enumerate every catalog on the classpath, parses each entry, and
   merges them under the conflict policy below.
2. The grafter rebuilds the analysis schema with the catalog applied,
   producing a single immutable `AnalysisSchema` that carries the
   documentation.

This grafted schema is the same value used to evaluate every DCL file
in the build. There is no project level reload and no project level
regraft. Per project plugin discovery is not a thing in DCL: schema
contribution is settings time only.

Schema building remains pure (`declarative-dsl-core`). Resource
discovery and the wiring to `ClassLoaderScope` /
`PluginsInterpretationSequenceStep` live in `declarative-dsl-provider`.

#### Reference aliasing in the schema graph

`AnalysisSchema` holds some definitions in more than one place. The top
level receiver appears both as `topLevelReceiverType: DataClass` and
(when it has an FQN) as an entry in `dataClassTypesByFqName`. A naive
grafter that only rebuilds the FQN map would leave
`topLevelReceiverType` pointing at the *old* `DataClass`, so consumers
reading `schema.topLevelReceiverType.metadata` would not see the
`SchemaDocumentation` even though the same type's entry in the map
carries it.

The grafter must rebuild the schema in a single pass that updates every
reference consistently, typically by rebuilding the map first and then
resolving each direct reference (top level receiver, generic
instantiations, etc.) from the rebuilt map. A test asserts that the
`SchemaDocumentation` reachable via `schema.topLevelReceiverType` is
the same instance as the one reachable via
`schema.dataClassTypesByFqName[fqn]`.

## Implementation plan (strict TDD)

Each cycle is a red→green pair: a test is added that fails for an
expected reason, then the smallest implementation change that makes it
pass is committed. Test framework: JUnit 5 plus Kotlin assertions,
matching the existing `declarative-dsl-core/src/test` style.

Cycles are grouped into five phases. Within a phase, cycles depend on
each other and should be done in order. Between phases, dependencies
are spelled out.

Sample catalogs referenced by name (`Sample A` through `Sample L`) are
listed in the **Sample catalogs** section after the cycles. They are
based on real definition types from the
`platforms/core-configuration/project-features-demos` project so the
fixtures resemble real declarative definitions.

### Phase 1: Schema fields and plumbing

Lays the public API foundation. After this phase, the schema can carry
documentation; nothing fills it in yet.

**Cycle 1.1: `SchemaDocumentation` exists and round trips through
serialization.**
Red: assert that a `SchemaDocumentation` instance can be constructed
with a non null `text` and a non empty `parts` map, that it satisfies
`SchemaItemMetadata`, and that a `Default*` carrying it in its
`metadata` list serializes via `SchemaSerialization` and deserializes
back to an equal value. Compile fails because the type does not exist
yet.
Green: add the `SchemaDocumentation` interface to
`org.gradle.declarative.dsl.schema` next to the other
`SchemaItemMetadata` variants; add a `Default*` implementation in
`org.gradle.internal.declarativedsl.analysis` next to
`DefaultSchemaItemMetadata`; register the new variant in
`SchemaSerialization.kt`. No existing schema interface gets a new
field; no `Default*` impl outside this new one is touched.

### Phase 2: Catalog model and key projection

Pure data and a pure projection function. No schema, no Gradle, no
classloader yet.

**Cycle 2.1: Catalog model parses JSON.**
Red: deserialize **Sample A** into the catalog data classes; assert the
expected tree. (Cycle fails at compile because the data classes don't
exist yet.)
Green: define `DocumentationCatalog`, `TypeDocumentation`,
`FunctionDocumentation`, `EnumDocumentation` as `@Serializable` data
classes in `declarative-dsl-core/.../documentation/`, mirroring the
*Resource format* JSON shape.

**Cycle 2.2: Function signature key projection.**
Red: assert `paramTypeFragment` and `functionKey` produce the expected
strings for each input variant: primitives (one per row of the
primitive table), class types (FQN, nested with `$`), parameterized
types (erased), vararg (element plus `[]`). Multiple parameterized
cases on table driven tests.
Green: implement `paramTypeFragment(DataTypeRef): String` and
`functionKey(simpleName: String, parameters: List<DataParameter>):
String` in the same package, pure functions.

### Phase 3: Grafter

Pure schema rebuilder. Each cycle adds one definition kind. No
diagnostics; the input catalog is assumed valid (the loader validates).

**Cycle 3.1: Grafter applies type docs.**
Red: graft **Sample A** onto a hand built schema containing
`CheckstyleDefinition`; assert
`schema.dataClassTypesByFqName[fq].metadata` contains a
`SchemaDocumentation` whose `text` is the expected string. Original
schema unchanged (immutability).
Green: implement the type level pass of `graftDocumentation`.

**Cycle 3.2: Property docs.**
Red: extend the previous test; assert `ignoreFailures` and `configFile`
properties' `metadata` lists each contain a `SchemaDocumentation` with
the expected `text`.
Green: extend grafter for properties.

**Cycle 3.3: Member function docs (with parameter docs).**
Red: graft **Sample C** onto a schema containing `LibraryDependencies`;
assert that `copyTo(...)`'s `metadata` contains a `SchemaDocumentation`
whose `text` carries the function doc and whose `parts` map contains
`"target"` with the parameter doc.
Green: extend grafter for functions and parameters in one cycle (the
function and its parameter docs land in the same `SchemaDocumentation`
instance).

**Cycle 3.4: Enum type docs and entry docs.**
Red: graft **Sample J** onto a schema with an enum; assert that the
enum's `metadata` contains a `SchemaDocumentation` whose `text` is the
enum type doc and whose `parts` map covers each documented entry by
name.
Green: extend grafter for enums (entries land in the same
`SchemaDocumentation.parts` as the type doc).

**Cycle 3.5: Top level receiver aliasing.**
Red: graft a sample documenting the FQN of the top level receiver type;
assert that the `SchemaDocumentation` reachable via
`schema.topLevelReceiverType.metadata` is the same instance as the one
reachable via `schema.dataClassTypesByFqName[fq].metadata`.
Green: rebuild direct `DataClass` references held by `AnalysisSchema`
(top level receiver, generic instantiation values) from the rebuilt
FQN map so all aliases agree.

**Cycle 3.6: Mirror property `SchemaDocumentation` onto getter
synthesized configure functions.**
Red: graft **Sample M** onto a schema containing `JavaLibraryModel`
(which has a `getTestReports()` getter that the schema turns into a
configure block). The catalog documents only the `testReports`
property. Assert that both the property and the synthesized
`testReports()` `DataMemberFunction` carry equal `SchemaDocumentation`
in their `metadata`. An explicit `functions["testReports()"]` entry in
the catalog, if present, still takes precedence over the mirrored
value.
Green: extend the grafter with a pass that walks each
`DataMemberFunction` whose `metadata` contains
`ConfigureFromGetterOrigin` and lacks a `SchemaDocumentation`, and
adds one copied from the same named property in the same enclosing
class.

### Phase 4: Loader

Resource discovery, parsing, merging, and per resource diagnostics.
Lives in `declarative-dsl-provider`. Each cycle adds one diagnostic
behaviour.

**Cycle 4.1: Loads a single resource.**
Red: a `URLClassLoader` over a synthetic JAR containing **Sample A** at
the fixed path; loader returns the parsed catalog with that single
type entry. No warnings emitted.
Green: implement resource enumeration via `getResources(...)`, parsing
each, returning the merged result. Logger is captured by a test fixture
(no real Gradle Logger needed in unit tests).

**Cycle 4.2: Merges multiple resources without conflicts.**
Red: classloader has **Sample D** split across two synthetic JARs (one
for `CheckstyleDefinition`, one for `AntlrGrammarsDefinition`); loader
returns a merged catalog covering both, no warnings.
Green: implement the merge.

**Cycle 4.3: Filters orphans silently.**
Red: classloader serves **Sample E**; loader returns a catalog that
omits the orphan keys; **no `Logger.warn` is captured**. Generator
bugs that produce orphan entries are dropped silently because the
"include all docs" posture in the Generator contract makes orphan vs.
"hidden in this build" indistinguishable.
Green: implement orphan filtering (using the schema parameter) without
emitting any warning.

**Cycle 4.4: Resolves conflicts last wins, warns the loser.**
Red: classloader serves **Sample F** (two catalogs document the same
key); loader's merged catalog has the last entry; the losing resource
gets a per resource warning naming the overridden key(s).
Green: implement conflict tracking and warning.

**Cycle 4.5: Reports parse errors, keeps loading siblings.**
Red: classloader has both **Sample G** (malformed) and **Sample A**
(valid); loader returns a catalog containing Sample A's entries; one
parse error warning is emitted naming the malformed resource; Sample A
is unaffected.
Green: implement parse error handling (catch the kotlinx exception per
resource, summarize, continue).

**Cycle 4.6: Ignores unknown JSON fields silently.**
Red: classloader serves **Sample H** (extra top level key); loader
parses successfully and emits no warnings.
Green: confirm `ignoreUnknownKeys = true` is set on the kotlinx Json
instance.

**Cycle 4.7: Truncates long issue lists.**
Red: classloader serves **Sample L** (15 catalogs colliding on the
same set of keys, so each loser carries 15 overridden entries); the
captured warning contains exactly 10 keys and the literal `(+5 more)`.
Green: implement the cap at 10 truncation in the warning formatter.

### Phase 5: Wiring (integration)

End to end against a real DCL build; lives in the integration test
module that already exercises declarative DSL.

**Cycle 5.1: Plugin contributes documentation.**
Red: integration test runs a build with a fixture settings plugin that
ships **Sample A** at the fixed path inside its JAR; the test resolves
the build's analysis schema (via existing tooling model or test
fixture hooks) and asserts the documentation is present.
Green: hook
`DocumentationCatalogLoader.load(settings.classLoaderScope.localClassLoader, schema)`
into `PluginsInterpretationSequenceStep.whenEvaluated` (or an
immediately following step), then call `graftDocumentation` and pass
the grafted schema downstream.

**Cycle 5.2: Parent classloader discovery.**
Red: integration test where the fixture catalog lives in a JAR placed
on the parent classloader (simulating a Gradle JAR above the plugin
classloader); same assertion as 5.1.
Green: should pass with no extra code, since `getResources()` walks the
parent chain. The cycle exists to pin the behaviour.

**Cycle 5.3: Diagnostics surface in the build log.**
Red: integration test runs with two fixture plugins both contributing
**Sample F** (the same key with conflicting docs); assert the build
output contains the expected per resource warning naming the
overridden key.
Green: should pass; this is regression coverage for the wiring.

## Implementation TODO list

Each cycle in the *Implementation plan (strict TDD)* section above is
broken down here into individual tasks with unique identifiers.
Identifiers follow the form `T<phase>.<n>`. Use the checkboxes to
track progress.

### Phase 1: `SchemaDocumentation` metadata variant

- [x] **T1.1** Add the `SchemaDocumentation` interface to
  `org.gradle.declarative.dsl.schema.SchemaItemMetadata`
  (declarative-dsl-tooling-models): subtype of `SchemaItemMetadata`
  with `text: String?` and `parts: Map<String, String>`. Document its
  contract (CommonMark `text`, named sub docs in `parts`).
- [x] **T1.2** Update the `@ToolingModelContract(subTypes = [...])`
  annotation on `SchemaItemMetadata` to include `SchemaDocumentation`.
- [x] **T1.3** Add `DefaultSchemaDocumentation` data class in
  `org.gradle.internal.declarativedsl.analysis` (declarative-dsl-core),
  annotated `@Serializable` and `@SerialName("schemaDocumentation")`.
- [x] **T1.4** Register `DefaultSchemaDocumentation` as a polymorphic
  subclass in `SchemaSerialization.kt` under the
  `SchemaItemMetadata` polymorphic block.
- [x] **T1.5** Write a unit test in declarative-dsl-core: construct a
  `DefaultSchemaDocumentation` with non null `text` and non empty
  `parts`, attach it to a hand built `Default*` item's `metadata`,
  serialize via `SchemaSerialization`, deserialize, assert structural
  equality.

### Phase 2: Catalog data model and key projection

- [x] **T2.1** Create the package
  `org.gradle.internal.declarativedsl.documentation` in
  declarative-dsl-core.
- [x] **T2.2** Add `DocumentationCatalog` `@Serializable` data class
  with `types: Map<String, TypeDocumentation> = emptyMap()` and
  `enums: Map<String, EnumDocumentation> = emptyMap()`.
- [x] **T2.3** Add `TypeDocumentation` `@Serializable` data class with
  `documentation: String? = null`,
  `properties: Map<String, String> = emptyMap()`,
  `functions: Map<String, FunctionDocumentation> = emptyMap()`.
- [x] **T2.4** Add `FunctionDocumentation` `@Serializable` data class
  with `documentation: String? = null`,
  `parameters: Map<String, String> = emptyMap()`.
- [x] **T2.5** Add `EnumDocumentation` `@Serializable` data class with
  `documentation: String? = null`,
  `entries: Map<String, String> = emptyMap()`.
- [x] **T2.6** Add a private `kotlinx.serialization.json.Json` instance
  configured with `ignoreUnknownKeys = true`, used by both the loader
  and the unit tests in this phase.
- [x] **T2.7** Write a unit test that deserializes **Sample A** as a
  raw JSON string and asserts the resulting `DocumentationCatalog`
  tree.
- [x] **T2.8** Write a unit test that deserializes **Sample H** (with
  an unknown top level field) and asserts the parse succeeds and the
  unknown field is silently ignored.
- [x] **T2.9** Implement `paramTypeFragment(ref: DataTypeRef): String`
  as a pure function. Cover: `DataTypeRef.Type` (primitives via the
  fixed table), `DataTypeRef.Name` (use the underlying class's
  `javaTypeName`), `DataTypeRef.NameWithArgs` (erased to the
  signature's `javaTypeName`), and the vararg `[]` suffix rule.
- [x] **T2.10** Implement
  `functionKey(simpleName: String, parameters: List<DataParameter>): String`
  as a pure function joining `simpleName(<frag1>,<frag2>,...)` with
  no whitespace.
- [x] **T2.11** Write table driven unit tests for `paramTypeFragment`
  covering: every row of the primitive table, top level FQN, nested
  FQN with `$`, parameterized erased, vararg of `String`, vararg of a
  user type.
- [x] **T2.12** Write unit tests for `functionKey`: zero parameters,
  one primitive, mixed primitives, mixed primitive plus class,
  trailing vararg, single vararg.

### Phase 3: Grafter

- [x] **T3.1** Add the entry point
  `graftDocumentation(schema: AnalysisSchema, catalog: DocumentationCatalog): AnalysisSchema`
  in the same package as the catalog model. No Gradle dependencies.
- [x] **T3.2** Implement type level grafting: for each entry in
  `dataClassTypesByFqName`, look up its `javaTypeName` in
  `catalog.types`; when a `documentation` is present, append a
  `DefaultSchemaDocumentation(text = ..., parts = emptyMap())` to the
  type's `metadata` and rebuild the `Default*` instance.
- [x] **T3.3** Add a hand built schema test fixture for
  `CheckstyleDefinition`. Write the Cycle 3.1 test using **Sample A**
  asserting type level `SchemaDocumentation`.
- [x] **T3.4** Extend the type level pass: for each `DataProperty` in
  the type's `properties`, look up the property name in
  `entry.properties` and attach a
  `DefaultSchemaDocumentation(text = ..., parts = emptyMap())` to the
  property's `metadata`.
- [x] **T3.5** Extend the test from T3.3 to assert the property docs
  for `ignoreFailures` and `configFile`.
- [x] **T3.6** Implement member function plus parameter grafting: for
  each `DataMemberFunction`, build the function key via T2.10, look
  up in `entry.functions`, attach a
  `DefaultSchemaDocumentation(text = entry.documentation, parts = entry.parameters)`
  to the function's `metadata`.
- [x] **T3.7** Add a hand built schema test fixture for
  `LibraryDependencies` exposing `copyTo(LibraryDependencies)`. Write
  the Cycle 3.3 test using **Sample C**.
- [x] **T3.8** Implement enum grafting: for each `EnumClass` in the
  schema, look up in `catalog.enums`, attach a
  `DefaultSchemaDocumentation(text = entry.documentation, parts = entry.entries)`
  to its `metadata`.
- [x] **T3.9** Add a synthetic `Severity` enum schema fixture. Write
  the Cycle 3.4 test using **Sample J**.
- [x] **T3.10** Implement reference aliasing in the rebuild: rebuild
  `dataClassTypesByFqName` first, then resolve every direct
  `DataClass` reference held by `AnalysisSchema` (top level receiver,
  generic instantiation map values) from the rebuilt FQN map so all
  aliases agree.
- [x] **T3.11** Write the Cycle 3.5 aliasing test: a sample
  documenting the top level receiver's FQN; assert the
  `SchemaDocumentation` reachable via `topLevelReceiverType` is the
  same instance as the one reachable via `dataClassTypesByFqName`.
- [x] **T3.12** Implement the configure function mirror pass: walk
  every `DataMemberFunction` whose `metadata` contains
  `ConfigureFromGetterOrigin` and lacks a `SchemaDocumentation`; copy
  the `SchemaDocumentation` from the same named property in the same
  enclosing class.
- [x] **T3.13** Add a hand built `JavaLibraryModel` schema fixture
  exposing the synthesized `testReports()` configure function. Write
  the Cycle 3.6 test using **Sample M**.

### Phase 4: Loader

- [x] **T4.1** Create the package
  `org.gradle.internal.declarativedsl.documentation` in
  declarative-dsl-provider.
- [x] **T4.2** Add `DocumentationCatalogLoader` class with constructor
  injecting an `org.gradle.api.logging.Logger` and offering
  `load(classLoader: ClassLoader, schema: AnalysisSchema): DocumentationCatalog`.
- [x] **T4.3** Implement resource enumeration:
  `classLoader.getResources("META-INF/declarative-dsl/documentation.json")`
  iterated to a list of URLs.
- [x] **T4.4** Implement single resource parsing using the kotlinx
  `Json` instance from T2.6. Hold per resource issue lists for later
  diagnostics.
- [x] **T4.5** Add a test fixture helper that builds a synthetic JAR
  (as a temp file or in a `URLClassLoader` over a directory)
  containing a given JSON resource at the fixed path.
- [x] **T4.6** Add a test fixture for capturing `Logger.warn(...)`
  calls (e.g. a `TestLogger` or a Mockito spy).
- [x] **T4.7** Write the Cycle 4.1 test: single JAR with **Sample A**;
  loader returns the catalog; no `Logger.warn` calls.
- [x] **T4.8** Implement merge across resources (no conflicts yet).
- [x] **T4.9** Write the Cycle 4.2 test: two JARs with **Sample D**
  split; loader returns merged catalog; no warnings.
- [x] **T4.10** Implement orphan filtering: drop catalog entries
  (types, enums, properties, functions, parameters, entries) that do
  not address an actual schema definition. **Emit no warning for
  orphans.**
- [x] **T4.11** Write the Cycle 4.3 test: classloader serves
  **Sample E**; assert orphans dropped from returned catalog and
  zero warnings captured.
- [x] **T4.12** Implement conflict tracking during merge: when the
  same key appears in two resources, record the loser's overridden
  keys in its issue list (last wins).
- [x] **T4.13** Implement per resource warning emission for resources
  that have any issue. The format follows the *Diagnostics* template
  (parse error line OR conflict category line, no orphan category).
- [x] **T4.14** Write the Cycle 4.4 test: classloader serves
  **Sample F**; assert merged catalog has the winner's value and
  exactly one `Logger.warn` invocation against the loser, with the
  expected text.
- [x] **T4.15** Implement parse error handling: catch the kotlinx
  exception per resource, record a parse error issue, continue
  loading siblings.
- [x] **T4.16** Write the Cycle 4.5 test: classloader has both
  **Sample G** (malformed) and **Sample A**; assert Sample A's
  entries present in the returned catalog and one parse error
  warning naming the malformed resource.
- [x] **T4.17** Write the Cycle 4.6 test: classloader serves
  **Sample H**; assert successful parse (Sample A's entries present)
  and no warnings.
- [x] **T4.18** Implement issue list truncation in the warning
  formatter: cap each category's key list at 10 entries; append
  `(+N more)` when truncated.
- [x] **T4.19** Write the Cycle 4.7 test: classloader serves
  **Sample L** (15 conflicts); assert the captured warning lists
  exactly 10 keys followed by the literal `(+5 more)`.

### Phase 5: Wiring (integration)

- [x] **T5.1** Identify the integration test module that already
  exercises declarative DSL builds. Confirm it has the necessary
  dependencies on declarative-dsl-provider.
- [x] **T5.2** Modify `PluginsInterpretationSequenceStep.whenEvaluated`
  (or introduce an immediately following step) to: after
  `targetScope.lock()`, instantiate `DocumentationCatalogLoader`
  with the appropriate `Logger`, call
  `load(targetScope.localClassLoader, schema)`, then call
  `graftDocumentation(schema, catalog)`. *(Wired at the
  `DeclarativeSchemaModelBuilder` boundary instead of the plugins
  step: documentation grafting happens when the tooling model is
  built, using the settings classloader scope. A new convenience
  function `loadAndGraftDocumentation(schema, classLoader, logger)`
  in declarative-dsl-provider combines the loader and grafter.)*
- [x] **T5.3** Confirm the grafted schema replaces the un grafted one
  in every downstream DCL evaluation path (settings body, project
  scripts, etc.). *(Both the settings sequence and the project
  sequence go through `analysisOnly(classLoader)` in
  `DeclarativeSchemaModelBuilder`, which grafts each step's
  analysis schema before exposing it.)*
- [x] **T5.4** Build a test fixture: a settings plugin packaged into
  a JAR with **Sample A** at the fixed path. *(Covered by
  `DocumentationContributorTest` using `URLClassLoader` over a
  synthetic resource directory.)*
- [x] **T5.5** Write the Cycle 5.1 integration test: build with the
  fixture plugin; resolve the build's analysis schema (via tooling
  model or test fixture hook); assert the documentation is present.
  *(Exercised via `DocumentationContributorTest.loads catalog and
  grafts SchemaDocumentation onto matching schema items`.)*
- [x] **T5.6** Build a test fixture: a JAR placed on the parent
  classloader of the plugin classloader (simulating a Gradle JAR
  above plugins). *(Covered by nesting `URLClassLoader` instances:
  parent has the catalog, child has nothing; getResources walks the
  parent chain.)*
- [x] **T5.7** Write the Cycle 5.2 integration test using the fixture
  from T5.6; same assertion as T5.5. *(Exercised via
  `DocumentationContributorTest.parent classloader catalog is found
  by getResources walk`.)*
- [x] **T5.8** Build a test fixture: two settings plugins both
  shipping **Sample F** (conflicting docs for the same key).
  *(Covered by `URLClassLoader` over two resource directories with
  conflicting catalogs.)*
- [x] **T5.9** Write the Cycle 5.3 integration test using the fixture
  from T5.8; assert the build output contains the per resource
  conflict warning. *(Exercised via
  `DocumentationContributorTest.conflicting catalogs follow last
  wins and the loser warns`.)*

## Sample catalogs

All catalogs target schema definitions drawn from
`platforms/core-configuration/project-features-demos` so the fixtures
look like realistic plugin contributions.

### Sample A: type doc plus property docs

Covers: type entry, simple property keys, primitives in property types
(via the `Property<T>` wrapper, which is erased in the catalog).

```json
{
  "types": {
    "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
      "documentation": "Configuration for the Checkstyle quality check on a source set.",
      "properties": {
        "ignoreFailures": "Whether Checkstyle violations should fail the build.",
        "configFile": "Path to the Checkstyle configuration XML file."
      }
    }
  }
}
```

### Sample B: inheritance

Covers: a documented child type does not silently document its parent's
members; only the keys present in the catalog get docs.

```json
{
  "types": {
    "org.gradle.api.plugins.checkstyle.CheckstyleSourceSetDefinition": {
      "documentation": "A source set scoped Checkstyle definition that produces a build model."
    }
  }
}
```

(Asserted: `CheckstyleSourceSetDefinition.metadata` contains a
`SchemaDocumentation`; `CheckstyleDefinition.metadata` does not.)

### Sample C: member function with parameter doc

Covers: function key projection (one parameter, class type), parameter
doc keyed by name. Uses a non bean member function from the demos
(`LibraryDependencies.copyTo(LibraryDependencies)`) so the function is
genuinely a member function in the schema and not a bean accessor
folded into a property. (Parameterized erasure is exercised separately
by the unit tests in Cycle 2.2.)

```json
{
  "types": {
    "org.gradle.api.plugins.java.LibraryDependencies": {
      "functions": {
        "copyTo(org.gradle.api.plugins.java.LibraryDependencies)": {
          "documentation": "Copy all dependencies and constraints from this collector to the target.",
          "parameters": {
            "target": "Destination receiving the copied dependencies."
          }
        }
      }
    }
  }
}
```

### Sample D: multi resource merge

Two separate JSON files, each in its own synthetic JAR. Covers: non
conflicting merge across multiple contributing JARs.

JAR 1:
```json
{
  "types": {
    "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
      "documentation": "Checkstyle config."
    }
  }
}
```

JAR 2:
```json
{
  "types": {
    "org.gradle.api.plugins.antlr.AntlrGrammarsDefinition": {
      "documentation": "ANTLR grammars binding.",
      "properties": {
        "trace": "Enable trace output during parsing.",
        "traceLexer": "Enable trace output for the lexer."
      }
    }
  }
}
```

### Sample E: orphan keys (silent filtering)

Covers: silent orphan filtering. The `removedProperty` and
`org.example.NotInSchema` entries do not exist in the schema. The
loader drops them without a warning, and the build proceeds.

```json
{
  "types": {
    "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
      "properties": {
        "ignoreFailures": "Valid.",
        "removedProperty": "This property no longer exists in the schema."
      }
    },
    "org.example.NotInSchema": {
      "documentation": "This type is not in the schema."
    }
  }
}
```

### Sample F: conflict (last wins)

Two JARs both document `CheckstyleDefinition` with different strings.
The loader emits a per resource warning to the loser; the merged
catalog contains the winner's value.

JAR 1 (loser):
```json
{ "types": { "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": { "documentation": "First version." } } }
```

JAR 2 (winner):
```json
{ "types": { "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": { "documentation": "Overriding version." } } }
```

### Sample G: malformed JSON

Literally not valid JSON. The loader emits a parse error warning naming
the resource and continues to load siblings.

```
{ this is not json }
```

### Sample H: forward compat

Valid catalog with an extra unknown top level key. The loader ignores
the unknown field and emits no warning.

```json
{
  "types": {
    "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": { "documentation": "Hello." }
  },
  "futureExtensionPoint": { "x": "y" }
}
```

### Sample J: enum

A synthetic enum (the demos do not currently include one). Covers enum
type doc and per entry docs.

```json
{
  "enums": {
    "org.example.demo.Severity": {
      "documentation": "Severity level for a quality finding.",
      "entries": {
        "INFO": "Informational; not a violation.",
        "WARN": "A potential issue.",
        "ERROR": "A violation that fails the build."
      }
    }
  }
}
```

### Sample K: varargs and primitives

Covers every entry of the primitive table and the vararg `[]` rule.

```json
{
  "types": {
    "org.example.demo.AllParams": {
      "functions": {
        "kitchenSink(int,long,boolean,java.lang.String,java.lang.String[])": {
          "documentation": "Exercises every parameter type fragment.",
          "parameters": {
            "i": "An int.",
            "l": "A long.",
            "b": "A boolean.",
            "s": "A String.",
            "rest": "Vararg of Strings."
          }
        }
      }
    }
  }
}
```

### Sample L: high volume conflicts (truncation)

A pair of catalogs that document the same 15 keys on a real type with
different doc strings, so each loser carries 15 overridden entries.
Used to verify the truncation cap of 10 in the warning text. (Set up
in tests by replicating the same property keys across the two
fixtures; the actual property names can be made up since the test
fixture's hand built schema decides what is in the schema.)

```json
{
  "types": {
    "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
      "properties": {
        "p1": "version A",
        "p2": "version A",
        "...": "...",
        "p15": "version A"
      }
    }
  }
}
```

A second catalog provides the same 15 property keys with `"version B"`
docs; whichever loads last wins; the loser logs a single warning
listing 10 keys plus `(+5 more)`.

### Sample M: getter synthesized configure function (mirror)

Covers the grafter's mirror pass for configure functions synthesized
from getters returning a complex type. `JavaLibraryModel.getTestReports()`
returns a `TestReports` nested interface with no setter, so the schema
synthesizes a configuring function `testReports()` carrying
`ConfigureFromGetterOrigin` metadata. The catalog only documents the
`testReports` property; after grafting, both the property and the
synthesized configuring function carry the same string.

```json
{
  "types": {
    "org.gradle.api.plugins.java.JavaLibraryModel": {
      "properties": {
        "testReports": "Configures the destinations for test reports produced by this library."
      }
    }
  }
}
```

(Asserted: the `testReports` property's `metadata` contains a
`SchemaDocumentation` **and** the synthesized `testReports()`
`DataMemberFunction`'s `metadata` carries an equivalent
`SchemaDocumentation`. No `functions["testReports()"]` entry in the
catalog.)

## Postponed for future iterations

Things intentionally left out of v1 that may come back later. Listed
here so the omission is recorded rather than forgotten.

- **Top level functions.** The schema has a few hand written built ins
  in `externalFunctionsByFqName` / `infixFunctionsByFqName`, no custom
  ones. Their documentation can live alongside their construction in
  code for now, so the catalog format does not include a
  `topLevelFunctions` block. Add support if/when custom top level
  functions become a thing or when the volume justifies a catalog
  driven approach.

- **Parameterized type documentation.**
  `ParameterizedTypeSignature` and `ParameterizedTypeInstance` (e.g.
  `Property<T>`, `List<T>`, `ListProperty<T>`) carry no documentation
  in v1. In the long run they should derive their
  `SchemaDocumentation` from the class they originate from (the same
  class whose KDoc/Javadoc the generator already extracts), so that
  schema viewers showing a parameterized type can render the
  originating class's doc. Adjacent improvement mentioned by Sergey:
  the schema introspection tool currently does not print these types;
  making it print them is a complement to this work but lives outside
  this plan.

- **Documentation for synthesised member functions.** The schema
  synthesises member functions in two cases that v1 does not
  document:
  - **Dependency collector overloads** (`api(...)`,
    `implementation(...)`, etc.) generated by
    `DependencyCollectorFunctionExtractorAndRuntimeResolver` from a
    declared `DependencyCollector`-typed property. The originating
    property's KDoc is the natural doc source. The cleanest long
    term path is to extend the schema with a metadata entry
    identifying these synthesised overloads (parallel to
    `ConfigureFromGetterOrigin` already used for getter synthesised
    configure functions), then have the grafter mirror docs from
    the originating property.
  - **Container element factories** generated by
    `ContainersSchemaComponent`. They already carry
    `ContainerElementFactory(elementType)` metadata, but there is no
    clear source of doc since the source declares only the container,
    not the factory function. Whether these need documentation at
    all is itself an open question; revisit when a concrete use case
    appears.

- **Ship a Gradle authored documentation catalog.** The loader is
  designed to pick up catalogs from Gradle's own JARs (see
  *Classpath resource*), but this plan does not produce them. In the
  long run, Gradle should ship documentation catalogs in its API JARs
  covering:
  - Gradle types that plugin schemas reuse (e.g. `Property<T>`,
    `DirectoryProperty`, `RegularFileProperty`,
    `NamedDomainObjectContainer<T>`, etc.).
  - The DCL synthesised members described in the preceding entry,
    once the schema metadata supports documenting them.

  Without this, plugin authored catalogs cover plugin defined types
  only; hovering on a Gradle type in a schema viewer shows nothing.

## Open questions for later

- Owner only conflict policy (tightening from last wins).
