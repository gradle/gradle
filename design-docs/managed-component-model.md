
Currently much of the software component model is not managed, and so is not visible to rules and cannot take any real
advantage of the features of that rule based configuration offers.

This spec outlines several steps toward a fully managed software component model.

# Feature 1: Key objects in the software model are visible to rules

The goal for this feature is to expose something like the following structure to rules:

    project (aka 'model')
    +-- components
    |   +-- <component-name>
    |       +-- sources
    |       |   +-- <source-set-name>
    |       +-- binaries
    |           +-- <binary-name>
    |               +-- tasks
    |                   +-- <task-name>
    +-- testSuites
        +-- <test-suite-name>
            +-- <same-structure-as-component-above>

For this feature, it is a non-goal to expose any public mechanism for implementing this structure, but this isn't necessarily ruled out either.
The implementation may be fully internal and not extensible (other than adding elements to the various collections above).

At the completion of this feature, it should be possible to:

- Run the `model` report to see something like the above structure.
- Use the rule DSL to configure any of the objects above selected using hard-coded path.

## Implementation

One possible approach is to add each relationship to the graph incrementally, one at a time, fixing the inevitable breakages before moving to the
next.

An implementation could use the internal `MutableModelNode` type to expose each relationship as an edge between two nodes with unmanaged values.
Rules hardcoded in the component base plugins can attach these node and edges as the appropriate model elements become known.

The collections in the graph above are currently represented using `DomainObjectSet`. One possible implementation is to listen for changes to these collections
and attach node and edges as elements are added, bridging the legacy collections into the model graph.
Possibly a better implementation would be to change `CollectionBuilder` into a 'managed map' and use a managed implementation
(not the existing bridged implementation) for these collections instead. This approach would mean some interleaving of this feature and the next.

## Candidate stories

1. Make the `sources` property of each component visible to rules
1. Make the `binaries` property of each component visible to rules

The rest TBD

# Feature 2: Configuration of key parts of the software model is deferred until required

This feature changes the software model to introduce 'managed map' types instead of `DomainObjectSet`.

- The property `ComponentSpec.sources`, a collection of `LanguageSourceSet`, should allow any `LanguageSourceSet` type registered using a `@LanguageType` rule to be added.
- The property `ComponentSpec.binaries`, a collection of `BinarySpec`, should allow any `BinarySpec` type registered using a `@BinaryType` rule to be added.
- The property `BinarySpec.tasks`, a collection of `Task`, should allow any `Task` implementation to be added.

At the completion of this feature, it should be possible to write 'before-each', 'after-each', 'all with type' etc rules for the source sets and binaries of a component,
and the tasks of a binary. These rules will be executed as required.

## Implementation

Again, a possible approach is to change each collection, one at a time, and fix the breakages before moving on to the next collection. Breakages are expected
as configuration for the elements of these collections will be deferred, whereas it is currently performed eagerly.

This feature requires a managed map implementation whose values are unmanaged, which means some kind of internal factory will be required for this implementation
(such as `NamedEntityInstantiator`).

Currently the DSL supports nested, eager, configuration of these elements:

    model {
        component {
            someThing {
                sources {
                    cpp { ... }
                }
                binaries {
                    all { ... }
                }
            }
        }
    }

Some replacement for this nesting should be offered, and the configuration deferred.

# Feature 3: Plugin author uses managed types to extend the software model

This feature allows a plugin author to extend certain key types using a managed type:

- `ComponentSpec`
- `LanguageSourceSet`
- `BinarySpec`

It is a non-goal of this feature to add any further capabilities to managed types, or to migrate any of the existing subtypes to managed types.

## Implementation

This feature will require some state for a given object to be unmanaged, possibly attached as node private data, and some state to be managed, backed by
individual nodes.

# Feature 4: Build logic defines tasks for generated source sets and intermediate outputs

This feature generalizes the infrastructure through which build logic defines the tasks that build a binary, and reuses it for generated source sets
and intermediate outputs.

The goals for this feature:

- Introduce an abstraction that represents a physical thing, where a binary, a source set and intermediate outputs are all physical things.
- Allow the inputs to a physical thing to be declared. These inputs are also physical things.
- Allow a rule to define the tasks that build the physical thing.
- Allow navigation from the model for the physical thing to the tasks that are responsible for building it.
- Expose native object files, jvm class files, and generated source for play applications as intermediate outputs.

It is a non-goal of this feature to provide a public way for a plugin author to define the intermediate outputs for a binary.

## Implementation

Many of these pieces are already present, and the implementation should formalize these concepts.

Currently:

- `BuildableModelElement` represents a physical thing.
- `BinarySpec.tasks` and properties on `BuildableModelElement` represent the tasks that build the thing.
- `BinarySpec.sources` represents the inputs to a binary.
- A `@BinaryTasks` rule defines the tasks that build the binary.
- Various types, such as `JvmClasses` and `PublicAssets` represent intermediate outputs.

The implementation would be responsible for coordinating the rules when assembling the task graph, so that when a physical thing is to be built, the
tasks that build its inputs should be configured, then added as dependencies of the tasks the build the target.

The `components` report should show details of the intermediate outputs of a binary, the relationships between physical things and the entry point
task to build the thing.

# Feature 5: Build logic defines tasks to run executable things

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

# Feature 6: References between key objects in the software model are visible to rules

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
