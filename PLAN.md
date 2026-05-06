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

CommonMark, not GitHub Flavored Markdown, is chosen because it is the
strict subset every renderer agrees on; GFM extensions like tables are
not portable across consumers.

There is no v1 syntax for cross references between schema definitions
inside the doc (e.g. linking from `Foo.bar`'s doc to `Bar.baz`).
Generators may use any plain text convention; resolving those references
is a concern for the consumer.

### Definitions that carry documentation

Public schema interfaces in
`platforms/core-configuration/declarative-dsl-tooling-models/src/main/kotlin/org/gradle/declarative/dsl/schema/`:

- `DataClass`
- `DataProperty`
- `SchemaMemberFunction` (covers `DataMemberFunction` and `DataBuilderFunction`)
- `DataTopLevelFunction`
- `DataConstructor`
- `DataParameter`
- `EnumClass` (both the type itself and per entry documentation)

Parameter docs are the most valuable shape for IDE completion popups,
and enum entry docs commonly explain the semantics of each value, so
both ship in v1.

`EnumClass` currently exposes entries as `entryNames: List<String>`.
Rather than promoting entries to a richer type, add a parallel
`entryDocumentation: Map<String, String>` field. Keys are entry names,
values are docs. Entries without docs are simply absent from the map.

**JavaBean accessors and Kotlin properties become a single `DataProperty`.**
The schema's `PropertyExtractor` walks JavaBean getters with the `get`
prefix and Kotlin properties, returning one `DataProperty` per
accessor pair: name is the bean stripped or Kotlin name (`x`); a
matching setter (`setX(T)`) is consumed and folds into the property's
`ReadWrite` mode rather than appearing as a separate member function.
The catalog mirrors this:

- A `properties[<name>]` doc covers both read and write. There is no
  separate setter doc.
- A `functions[setX(T)]` entry would be an orphan (the schema has no
  such function) and would surface as an orphan key warning.

**Only the `get` prefix is bean folded.** `isJavaBeanGetter`
([ClassMembersForSchema.kt:434](platforms/core-configuration/declarative-dsl-core/src/main/kotlin/org/gradle/internal/declarativedsl/schemaBuilder/ClassMembersForSchema.kt#L434))
matches `getXxx` only. A `boolean isReady()` getter does **not** become
a property `ready`; it appears as a member function `isReady()` in the
schema. The catalog must document it under `functions["isReady()"]`,
not under `properties.ready`. (Generators that follow Java's bean
convention naively will produce orphan property entries here.)

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
(`properties[testReports]`); the grafter mirrors the property's doc
onto the synthesized configuring function, recognising it via the
`ConfigureFromGetterOrigin` metadata. Mirror is "fill if null", so an
explicit `functions[testReports()]` entry, if present, still wins.
Generators should not emit such an explicit entry: it would duplicate
the property doc and risk diverging strings.

**Inherited members get their own `DataProperty` / `DataMemberFunction`
instances.** The schema runs `PropertyExtractor` and `FunctionExtractor`
per type; for a subtype, inherited members are included in the
extraction (sourced via `mergeMembersBySignature` from the supertype's
declared members). So `CheckstyleSourceSetDefinition` ends up with its
own `DataProperty("ignoreFailures", ...)` instance distinct from the
one on `CheckstyleDefinition`. Catalogs document each member at its
**definition site** (the supertype that declares it). The grafter
mirrors documentation **down the supertype chain**: for each subtype
property or member function whose documentation is `null`, it walks
the supertype chain (`DataClass.supertypes`, transitively) and copies
the documentation from a same named documented member on a supertype.
Mirror is "fill if null", so an explicit subtype catalog entry still
wins. Generators should emit each member's doc once, on the type that
declares it.

**Not documented (intentionally):** `ParameterizedTypeSignature` and
`ParameterizedTypeInstance` (e.g. `Property<T>`, `List<T>`,
`ListProperty<T>`). DCL users never type these names; they write the
*value* a property holds, not the wrapper type. Given a property
declared as `scope: Property<String>`, a DCL file says `scope = "..."`,
and the schema describes it as "a property named `scope` that can have
`String` values". The `Property` wrapper is implementation detail. The
property itself (`DataProperty.documentation`) is the right place for
user facing doc; the type wrapping its value is not. Same reasoning for
`List<String>` etc. The property doc covers the user visible meaning.

Also intentionally not documented:

- **`ExternalObjectProviderKey`**. An external object's user visible
  meaning is its type, which is documented via the `DataClass` it
  references. The key itself is just a named pointer.
- **`AssignmentAugmentation`**. The `+=` style syntax; its underlying
  `function: DataTopLevelFunction` is already documentable through the
  function field, which is sufficient.
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

Keys are written in **JVM binary form** throughout. Definition types
may be authored in any JVM language, and the JVM form is the language
neutral canonical name available on the schema
(`ClassDataType.javaTypeName`, `DataTopLevelFunction.ownerJvmTypeName`).

- **Type keys**: JVM binary FQN of the class. Top level:
  `org.example.Bar`. Nested: `org.example.Outer$Inner` (dollar
  separated, not dotted).
- **Property keys**: simple property name.
- **Function keys**: `simpleName(parameter list)`. Parameter list is
  comma separated, no spaces. Parameterless: `simpleName()`.
- **Constructor keys**: `(parameter list)` only, no name. Default
  constructor: `()`.
- **Top level function keys**:
  `<ownerJvmTypeName>.<simpleName>(parameter list)`. For Kotlin top
  level functions, `ownerJvmTypeName` is the synthetic file class
  (e.g. `org.example.HelperKt`). For Java static methods, the declaring
  class.

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

Inside each function/constructor/topLevelFunction entry, parameter docs
are keyed by **simple parameter name**:

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
warns rather than fails. Three classes of issue are detected:

- **Malformed JSON.** The resource is unparseable in part or whole.
  Generator bug; user cannot fix it.
- **Orphan keys.** A documented key (type, member, parameter, etc.)
  doesn't match anything in the schema. Typical cause: the contributing
  plugin renamed or removed a definition without updating its catalog.
- **Conflicts where this resource lost.** The same key was contributed
  by another resource that took precedence under last wins. Useful to
  diagnose "why aren't my docs showing?".

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

When the resource parses but has other issues, each category with any
issue appears on its own indented line, in this order:

```
Documentation issues in <jar path>:
  orphan keys (<n>): <k1>, <k2>, …, <k10> (+<n−10> more)
  keys overridden by other catalogs (<m>): <k1>, <k2>, …, <k10> (+<m−10> more)
```

Categories with zero issues are omitted entirely.

Each category truncates its key list at **10** keys; the count in
parentheses is the full count, and `(+N more)` signals the remainder.
Individual keys are listed verbatim, e.g.
`Bar.compute(java.lang.String,int)` for member functions,
`(java.lang.String)` for constructors,
`org.example.HelperKt.helper()` for top level functions.

Worked example (a JAR with two orphan keys and one overridden key):

```
Documentation issues in /Users/alice/.gradle/caches/.../my-plugin-1.0.jar:
  orphan keys (2): org.example.Foo.removed(), org.example.Bar
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
- Top level object with optional keys: `types`, `enums`,
  `topLevelFunctions`. Any key may be absent if the JAR contributes
  nothing in that key space.

All `documentation` values are **CommonMark** strings (or omitted /
`null` when absent).

#### `types[<jvm fqn>]`

Key: JVM binary FQN of a `DataClass`. Top level: `org.example.Foo`.
Nested: `org.example.Outer$Inner` (dollar separated).

Value: object with optional members:

- `documentation`: CommonMark string for the type itself.
- `properties`: map of property simple name to CommonMark string.
- `functions`: map of function key to function doc object (see below).
- `constructors`: map of constructor key to function doc object.

#### `enums[<jvm fqn>]`

Key: JVM binary FQN of an `EnumClass`.

Value: object with optional members:

- `documentation`: CommonMark string for the enum type.
- `entries`: map of enum entry simple name (matching
  `EnumClass.entryNames`) to CommonMark string.

#### `topLevelFunctions[<owner>.<name>(args)]`

Key: `<ownerJvmTypeName>.<simpleName>(<jvm form args>)`. For Kotlin top
level functions, `ownerJvmTypeName` is the synthetic file class (e.g.
`org.example.HelperKt`). For Java statics, the declaring class.

Value: function doc object.

#### Function doc object

Used as the value type in `functions`, `constructors`, and
`topLevelFunctions`. Optional members:

- `documentation`: CommonMark string for the function.
- `parameters`: map of parameter simple name to CommonMark string.
  Nameless parameters cannot be documented in v1.

#### Function and constructor key syntax

- Member function: `<simpleName>(<jvm form args>)`, e.g.
  `compute(java.lang.String,int)`.
- Constructor: `(<jvm form args>)`, no name. Default: `()`.
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

#### Schema visibility rules to apply

Definition types use annotations to control what reaches the schema.
The generator must honour them and skip the same members/types the
schema skips. Otherwise the emitted entries would be orphans and warn
at load time.

- `@HiddenInDefinition` on a member excludes that member.
- `@HiddenInDefinition` on a type makes the type itself hidden and
  drops its supertypes from declarative use.
- `@VisibleInDefinition` is the counter annotation, used in a subtype
  to re expose an inherited hidden member or type.

The annotations live in
`org.gradle.declarative.dsl.model.annotations` (declarative-dsl-api).
The reference for the exact rules is the schema's own
`ClassMembersForSchema`, which is what the loader will validate
against.

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
- Orphan keys (keys that do not address an actual schema definition)
  are warned and dropped.
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
reading `schema.topLevelReceiverType.documentation` would see `null`
even though the same type's entry in the map is populated.

The grafter must rebuild the schema in a single pass that updates every
reference consistently, typically by rebuilding the map first and then
resolving each direct reference (top level receiver, generic
instantiations, etc.) from the rebuilt map. A test asserts that
`schema.topLevelReceiverType.documentation` and
`schema.dataClassTypesByFqName[fqn].documentation` agree.

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

**Cycle 1.1: Field reads back as null/empty by default.**
Red: build a small schema (using existing schema builder test helpers)
and assert that every `Default*`'s `documentation` is `null` and
`EnumClass.entryDocumentation` is empty. Compile fails on the access.
Green: add `documentation: String?` to the seven public interfaces
listed in *Definitions that carry documentation*; add
`entryDocumentation: Map<String, String>` to `EnumClass`; update each
`Default*` impl with a default constructor argument
(`null` / `emptyMap()`); update the kotlinx serialization shapes in
`SchemaSerialization.kt` with matching defaults.

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
`schema.dataClassTypesByFqName[fq].documentation == "Configuration for ..."`.
Original schema unchanged (immutability).
Green: implement the type level pass of `graftDocumentation`.

**Cycle 3.2: Property docs.**
Red: extend the previous test; assert `ignoreFailures` and `configFile`
properties have docs.
Green: extend grafter for properties.

**Cycle 3.3: Member function docs (with parameter docs).**
Red: graft **Sample C** onto a schema containing `LibraryDependencies`;
assert `copyTo(...)` carries function doc and that its `target`
parameter carries the parameter doc.
Green: extend grafter for functions and parameters in one cycle (they
co locate in the catalog under each function's `parameters` block).

**Cycle 3.4: Constructor docs.**
Red: graft **Sample I** (constructors and top level functions) onto a
schema with constructors; assert.
Green: extend grafter for constructors.

**Cycle 3.5: Top level function docs.**
Red: same Sample I; assert top level function and its parameter doc.
Green: extend grafter for `externalFunctionsByFqName` /
`infixFunctionsByFqName`.

**Cycle 3.6: Enum type docs and entry docs.**
Red: graft **Sample J** onto a schema with an enum; assert both enum
type doc and per entry docs.
Green: extend grafter for `EnumClass.documentation` and
`entryDocumentation`.

**Cycle 3.7: Top level receiver aliasing.**
Red: graft a sample documenting the FQN of the top level receiver type;
assert `schema.topLevelReceiverType.documentation` and
`schema.dataClassTypesByFqName[fq].documentation` are both populated
and identical (same string instance is fine).
Green: rebuild direct `DataClass` references held by `AnalysisSchema`
(top level receiver, generic instantiation values) from the rebuilt FQN
map so all aliases agree.

**Cycle 3.8: Mirror property docs onto getter synthesized configure
functions.**
Red: graft **Sample M** onto a schema containing `JavaLibraryModel`
(which has a `getTestReports()` getter that the schema turns into a
configure block). The catalog documents only the `testReports`
property. Assert that both the property and the synthesized
`testReports()` `DataMemberFunction` carry the same documentation
string. An explicit `functions["testReports()"]` entry in the catalog,
if present, still takes precedence over the mirrored value.
Green: extend the grafter with a pass that walks each
`DataMemberFunction` whose `metadata` contains
`ConfigureFromGetterOrigin` and whose `documentation` is `null`, and
fills it from the same named property in the same enclosing class.

**Cycle 3.9: Mirror inherited member docs down the supertype chain.**
Red: graft **Sample N** onto a schema containing
`CheckstyleSourceSetDefinition` (which extends `CheckstyleDefinition`).
The catalog documents only the parent's
`properties[ignoreFailures]`. Assert that
`CheckstyleSourceSetDefinition`'s inherited
`DataProperty("ignoreFailures", ...)` carries the same documentation
string, even though the catalog never names the child type. An
explicit subtype entry, if present, still takes precedence over the
mirrored value.
Green: extend the grafter with a pass that walks each `DataClass`,
inspects each property and member function, and for any with `null`
documentation searches the supertype chain (transitively via
`DataClass.supertypes` and `dataClassTypesByFqName`) for a documented
same named member, copying the doc from the first match. The two
mirror passes (configure functions in 3.8 and inheritance in 3.9) are
independent; the grafter runs the inheritance mirror first so that an
inherited property's doc can subsequently flow into a configure
function synthesized on the subtype.

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

**Cycle 4.3: Filters orphans, warns once.**
Red: classloader serves **Sample E**; loader returns a catalog that
omits the orphan keys, and exactly one `Logger.warn` is captured with
the per resource summary text from the *Diagnostics* template.
Green: implement orphan detection (using the schema parameter) and the
per resource warning emission.

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
Red: classloader serves **Sample L** (15 orphan keys); the captured
warning contains exactly 10 keys and the literal `(+5 more)`.
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
Red: integration test runs with a fixture plugin shipping **Sample E**
(orphan keys); assert the build output contains the expected warning
text.
Green: should pass; this is regression coverage for the wiring.

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

(Asserted: `CheckstyleSourceSetDefinition.documentation` is populated;
`CheckstyleDefinition.documentation` is still null.)

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

### Sample E: orphan keys

Covers: orphan filtering and warning. The `removedProperty` and
`org.example.NotInSchema` entries do not exist in the schema.

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

### Sample I: constructors and top level functions

A small synthetic top level helper and a constructor on a synthetic
type. (The demos do not exercise these directly; the test schema
includes a hand built receiver type with a constructor and a helper.)

```json
{
  "types": {
    "org.example.demo.Coords": {
      "constructors": {
        "(java.lang.String,int)": {
          "documentation": "Build coordinates from group and depth.",
          "parameters": {
            "group": "The group name.",
            "depth": "Numeric depth."
          }
        }
      }
    }
  },
  "topLevelFunctions": {
    "org.example.demo.HelpersKt.coords(java.lang.String,int)": {
      "documentation": "Convenience builder for Coords."
    }
  }
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

### Sample L: high volume orphans (truncation)

A catalog with 15 orphan property entries on a real type, to verify
the truncation cap of 10 in the warning text.

```json
{
  "types": {
    "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
      "properties": {
        "orphan1": "...",
        "orphan2": "...",
        "...": "...",
        "orphan15": "..."
      }
    }
  }
}
```

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

(Asserted: `properties[testReports].documentation` is populated **and**
the synthesized `testReports()` `DataMemberFunction` carries the same
documentation string. No `functions["testReports()"]` entry in the
catalog.)

### Sample N: inherited member doc (supertype mirror)

Covers the grafter's inheritance mirror pass. The catalog documents
`ignoreFailures` only on the parent type `CheckstyleDefinition`. After
grafting, the child type `CheckstyleSourceSetDefinition` (which extends
`CheckstyleDefinition`) carries the same documentation on its own
distinct `DataProperty("ignoreFailures", ...)` instance, even though
the catalog never names the child.

```json
{
  "types": {
    "org.gradle.api.plugins.checkstyle.CheckstyleDefinition": {
      "properties": {
        "ignoreFailures": "Whether Checkstyle violations should fail the build."
      }
    }
  }
}
```

(Asserted: both
`CheckstyleDefinition.properties[ignoreFailures].documentation` **and**
`CheckstyleSourceSetDefinition.properties[ignoreFailures].documentation`
carry the same string.)

## Open questions for later

- Owner only conflict policy (tightening from last wins).
