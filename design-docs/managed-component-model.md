
Currently much of the software component model is not managed, and so is not visible to rules and cannot take any real
advantage of the features of that rule based configuration offers.

This spec outlines several steps toward a fully managed software component model.

# Feature 4: Plugin author uses managed types to extend the software model

This feature allows a plugin author to extend certain key types using a managed type:

- `ComponentSpec`
- `LanguageSourceSet`
- `BinarySpec`
- `JarBinarySpec`

It is a non-goal of this feature to add any further capabilities to managed types, or to migrate any of the existing subtypes to managed types.

## Implementation

This feature will require some state for a given object to be unmanaged, possibly attached as node private data, and some state to be managed, backed by
individual nodes.

As part of this work, remove empty subclasses of `BaseLanguageSourceSet`, such as `DefaultCoffeeScriptSourceSet`.

## Story: Custom JarBinarySpec type is implemented as a @Managed type

Specific support will be added for specialisations of `JarBinarySpec`.
This is being targeted first as it is needed to continue the dependency management stream.

### Implementation

(unordered)

- Allow plugins to register schema extraction strategies via our plugin service registry mechanism
- Have the platformJvm module register a strategy for `JarBinarySpec` subtypes
- Change `@BinaryType` rule to have some understanding of `@Managed` types so that `defaultImplementation` is not required or allowed for managed binary types.
- Add support for having managed types be somehow backed by a “real object”

> For this story, the simplest approach may be to do this via delegation.
> First we create a `JarBinarySpecInternal` using the existing machinery, then create a managed subtype wrapper that delegates
> all `JarBinarySpecInternal` to the manually created unmanaged instance.
> This will require opening up the type generation mechanics to some extent.

- Open up managed node creation mechanics to allow “custom” strategies

> The schema extraction strategy may be the link here.
> That is, the strategy for creating the _node_ may be part of the schema.

- Implementation of `@BinaryType` rule WRT interaction with `BinarySpecFactory` will need to be “managed aware”
- Move `baseName` property from `JarBinarySpecInternal` to `JarBinarySpec`

### Tests

- Illegal managed subtype registered via `@BinaryType` yields error at rule execution time (i.e. when the binary types are being discovered)
- Attempt to call `binaryTypeBuilder.defaultImplementation` fails eagerly if public type is `@Managed`
- Subtype can have `@Unmanaged` properties
- Subtype can have further subtypes
- Subtype exhibits managed impl behaviour WRT immutability when realised
- Subtype can be cast and used as `BinarySpecInternal`
- Subtype cannot be created via `BinaryContainer` (i.e. top level `binaries` node) - (requires node backing)
- Can successfully create binary represented by `JarBinarySpec` subtype

# Feature 5: Managed Model usability

Some candidates:

- Consistent validation when managed type, ModelMap and ModelSet are used as inputs.
- Consistent validation when managed type, ModelMap and ModelSet are mutated after view is closed.
- Consistent validation when managed type, ModelMap and ModelSet used on subject that is not mutable.
- Consistent error message for ModelMap<T> and ModelSet<T> where T is not managed.
- Consistent usage of ModelMap and ModelSet with reference properties.
- Consistent mutation methods on ModelMap and ModelSet.
- Enforce that `@Defaults` rules cannot be applied to an element created using non-void `@Model` rule.
- Enforce that subject cannot be mutated in a validate rule.
- Rename (via add-deprecate-remove) the mutation methods on ModelMap and ModelSet to make more explicit that
  they are intended to be used to define mutation rules that are invoked later. For example `all { }` or `withType(T) { }` could have better names.
- Add methods (or a view) that allows iteration over collection when it is immutable.
- Rename (via add-deprecate-remove) `@Mutate` to `@Configure`.
- Allow empty managed subtypes of ModelSet and ModelMap. This is currently available internally, eg for `ComponentSpecContainer`.

# Feature 6: Internal views for managed types

- Open question: should we introduce some concept of internal model elements as well? That is, elements whose path (and existence) are an internal implementation detail
  for a plugin. These may or may not have view types which are public or internal.
    - To some degree this could be inferred, such that an element reachable only via properties of internal views could be considered an internal element.
- Model report should not show internal types or internal model elements, at least by default.

# Feature 7: Plugin author attaches source sets to managed type

This feature allows source sets to be added to arbitrary locations in the model. For example, it should be possible to model Android
build types and flavors each with an associated source set.

It is also a goal of this feature to make `ComponentSpec.sources` and `BinarySpec.sources` model backed containers.

## Implementation

- Allow any registered `LanguageSourceSet` subtype to be added as a property of a managed type, or created as a top-level element
    - Instances are linked into top level `sources` container
    - Need some convention for source directory locations. Possibly default to empty set.
- Change `ComponentSpec.source` so that configuration is deferred
    - Need to replace usages of internal `ComponentSpecInternal.sources`
    - Rename `source` to `sources` via add-deprecate-remove
- Change `BinarySpec.sources` so that configuration is deferred
- Change `FunctionalSourceSet` so that it extends `ModelMap`
- Allow `FunctionalSourceSet` to be added as a property of a managed type, or created as a top-level element
    - Instances are linked into top level `sources` container
    - Need some convention for source directory locations. Possibly add a `baseDir` property to `FunctionalSourceSet` and default source directories to `$baseDir/$sourceSet.name`
    - Need some convention for which languages are included. Possibly default to no languages
- Change `BinarySpec.sources` and `ComponentSpec.sources` to have type `FunctionalSourceSet`

# Later features

# Feature: Key component model elements are not realized until required

This feature continues earlier work to make key properties of `BinarySpec` managed.

- `BinarySpec.source`
- `BinarySpec.tasks`

# Feature: Build logic defines tasks for generated source sets and intermediate outputs

This feature generalizes the infrastructure through which build logic defines the tasks that build a binary, and reuses it for generated source sets
and intermediate outputs.

A number of key intermediate outputs will be exposed for their respective binaries:

- Native object files
- JVM class files
- Generated source for play applications

Rules implemented either in a plugin or in the DSL will be able to define the tasks that build a particular binary from its intermediate outputs,
an intermediate output from its input source sets, or a particular source set. Gradle will take care of invoking these rules as required.

Rules will also be able to navigate from the model for a buildable item, such as a binary, intermediate output or source set, to the tasks, for
configuration or reporting.

The `components` report should show details of the intermediate outputs of a binary, the input relationships between the source sets, intermediate outputs and
binaries, plus the task a user would run to build each thing.

It is a non-goal of this feature to provide a public way for a plugin author to define the intermediate outputs for a binary. This is a later feature.

## Implementation

A potential approach:

- Introduce an abstraction that represents a physical thing, where a binary, a source set and intermediate outputs are all physical things.
- Allow the inputs to a physical thing to be modelled. These inputs are also physical things.
- Allow a rule, implemented in a plugin or in the DSL, to define the tasks that build the physical thing.
- Allow navigation from the model for the physical thing to the tasks that are responsible for building it.
- A pre-built physical thing will have no tasks associated with it.

Many of these pieces are already present, and the implementation would formalize these concepts and reuse them.

Currently:

- `BuildableModelElement` represents a physical thing.
- `BinarySpec.tasks` and properties on `BuildableModelElement` represent the tasks that build the thing.
- `BinarySpec.sources` represents the inputs to a binary.
- A `@BinaryTasks` rule defines the tasks that build the binary, as do various methods on `LanguageSourceSet` and `BuildableModelElement`.
- Various types, such as `JvmClasses` and `PublicAssets` represent intermediate outputs.

The implementation would be responsible for invoking the rules when assembling the task graph, so that:

- When a physical thing is to be built, the tasks that build its inputs should be configured.
- When a physical thing is used as input, the tasks that build its inputs, if any, should be determined and attached as dependencies of those tasks
that take the physical thing as input.

# Feature: Plugin author uses managed types to define intermediate outputs

This feature allows a plugin author to declare intermediate outputs for custom binaries, using custom types to represent these outputs.

Allow a plugin author to extend any buildable type with a custom managed type. Allow a custom type to declare the inputs for the buildable type in a strongly typed way.
For example, a JVM library binary might declare that it accepts any JVM classpath component as input to build a jar, where the intermediate classes directory is a
kind of JVM classpath component.

## Implementation

One approach is to use annotations to declare the roles of various strongly typed properties of a buildable thing, and use this to infer the inputs
of a buildable thing.

# Feature: Build logic defines tasks that run executable things

This feature generalizes the infrastructure through which build logic defines the executable things and how they are to be executed.

The goals for this feature:

- Introduce an abstraction that represents an executable thing, where an installed executable or a test suite variant are executable things.
- Allow a rule to define the tasks that run the executable thing.
- Allow navigation from the model for the executable thing to the tasks that run it.
- Expose an installed native executable and a test suite variant as executable things.

This feature should sync with the plan for play application execution.

## Implementation

The implementation would be responsible for building the executable things, and then configuring and running the appropriate tasks.

The `components` report should show details of the executable things, which as the entry point task to run the thing.

# Feature: References between key objects in the software model are visible to rules

The relationships exposed in the first feature represent ownership, where the relationship is one between a parent and child.
This feature exposes other key 'non-ownership' relationships present in the software model.

- A binary has a collection of language source sets that it takes as input. These are not owned by the binary, but form its inputs.
- A test suite component has component under test associated with it.
- The project level binaries collection is a collection of binaries owned by various components.
- The project level sources collection is a collection of language source sets owned by various components.
- The project level task collection is a collection of tasks owned by various binaries, source sets, and other buildable things.

At the completion of this feature, it should be possible to see the above relationships represented in the `model` report.

It is a non-goal of this feature to allow rules to be written to select these objects using their 'non-ownership' paths.

## Implementation

For a binary's input source sets, one option would be to change the behaviour so that a binary receives a copy of its component's source sets. These
copies would then be owned by the binary and can be further customized in the context of the binary.

For a test suite's component under test, one option would be to restructure the relationship, so that test suite(s) become a child of the component under test.
