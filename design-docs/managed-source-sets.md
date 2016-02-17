# Feature: `LanguageSourceSet` is an 'extensible' type

This feature covers converting `LanguageSourceSet` into an 'extensible' type, which will allow users to declare `@Managed` and unmanaged subtypes, as well as attach internal views. Currently `LanguageSourceSet` appears in many places within the software model, with subtly different implementation an semantics in each case. Some of these have a node associated with them (e.g. `ComponentSpec.sources`) and some of which do not: (e.g. `FunctionalSourceSet or `ProjectSourceSet`).

The first stories in this theme are focussed on converting the various collections of `LanguageSourceSet` instances into a true `ModelMap` (or `ModelSet`). To the end user, this will ensure that these collections have consistent behaviour:
- `LanguageSourceSet` elements appear in the model report
- `LanguageSourceSet` elements can be targeted by model rules
- `LanguageSourceSet` elements are configured on demand

Once every `LanguageSourceSet` collection is a `ModelMap`, we will be using a common node initializer to create all `LanguageSourceSet` instances. The final step will be to switch to use the managed-type aware node initializer similar to `ComponentSpec` and `BinarySpec`, making `LanguageSourceSet` a true 'extensible' type, allowing `@Managed` subtypes and internal views to be applied.

Here are the `LanguageSourceSet` collections that need converting:

1. `ComponentSpec.sources` => `ModelMap`
2. `BinarySpec.sources` => `ModelMap`
3. `FunctionalSourceSet` => `ModelMap`
4. `ProjectSourceSet` => `ModelSet`

Where in each case this is a _real_ `ModelMap`, with lazy configuration semantics.
“Real ModelMap” from an implementation perspective means “uses the `LanguageSourceSet` node initializer only to create LSS instances". From a user perspective this means “LSS instances are visible to rules, appear in the model report, and are configured on demand".

This feature will also allow source sets to be added to arbitrary locations in the model. For example, it should be possible to model Android build types and flavors each with an associated source set.

## Story: Allow top level model elements of type `FunctionalSourceSet` to created

- Allow a `FunctionalSourceSet` to be used as a top level model element.
- Empty by default.

For example:

    model {
        sources(FunctionalSourceSet)
    }

Or:

    @Model
    void sources(FunctionalSourceSet sources) {
    }

### Test cases

- Instance can be defined as above, when the `LanguageBasePlugin` plugin has been applied.
- Instance can not be defined when `LanguageBasePlugin` has not been applied. Error message should include details of which types are available.
- Model report shows something reasonable for source set instance.

### Implementation

- Continue to converge on `NodeInitializer` as the strategy for creating all model elements, including the children of a managed type, the elements of a model collection and
top level model elements. For this story, we only need to make this work for top level model elements.
    - All model elements are created using a `NodeInitializer`.
    - Each type has 1 `NodeInitializer` implementation associated with it, that can be reused in any context where that type appears.
- Allow a `NodeInitializer` to be located for `FunctionalSourceSet` from the `NodeInitializerRegistry`.
- Extract validation from `NonTransformedModelDslBacking` and `TransformedModelDslBacking` into some shared location, probably to `NodeInitializerRegistry`. The idea here is to have a single place where something outside the registry can ask for a 'constructable' thing.
    - `NonTransformedModelDslBacking` and `TransformedModelDslBacking` no longer need to use the `ModelSchemaStore`.
    - Error message should include details of which types can be created. Keep in mind that this validation will need to be reused in the next story, for managed type properties and collection elements.
    - Query the `NodeInitializerExtractionStrategy` instances for the list of types they support.
- Change `NodeInitializerRegistry` so that strategies are pushed into it, rather than pulled, and change the `LanguageBasePlugin` to register a strategy.

### Out of scope

- Making the state of `FunctionalSourceSet` managed. This means, for example, the children of the source set will not be visible in the `model` report, and that immutability will not be enforced.
- Adding any children to the source set. This is a later story. A plugin can add children by first attaching a factory using `registerFactory()`.

## Story: Allow a managed type to have a property of type `FunctionalSourceSet`

- Allow a `FunctionalSourceSet` to be used as:
    - A read-only property or a mutable property of a `@Managed` type
    - An element of managed collections `ModelSet` and `ModelMap`

For example:

    @Managed
    interface BuildType {
        FunctionalSourceSet getSources()

        FunctionalSourceSet getInputs()
        void setInputs(FunctionalSourceSet sources)

        ModelMap<FunctionalSourceSet> getComponentSources()
    }

### Implementation

- Continue to converge on `NodeInitializer` as the strategy for creating the children of a managed type, the elements of a model collection and top level elements.
- Change validation for managed type properties and managed collection elements to allow any type for which a creation strategy is available.
    - Share (don't duplicate) the validation from the previous story that decides whether an instance of a given type can be created.
    - Error message should include the types available to be used.
- Update user guide to list `FunctionalSourceSet` as a type that can be used in the model.
- Refactors to clean up implementation:
    - Should share the same mechanism to expose the initializer for `FunctionalSourceSet` and `JarBinarySpec`, to make it easier to later add more types.
      Ideally, this would mean registering some description of the types (eg here's a public type and here's an implementation type for it), rather than registering an initializer strategy implementation.
    - Replace the various `ChildNodeInitializerStrategy` implementations with one that delegates to the schema.

### Test cases
- read-only property of a `@Managed` type.
- a mutable property of a `@Managed` type.
- element of managed collections `ModelSet`.
- element of managed collections `ModelMap`.
- Attempting to define a managed type with a non-supported type on any of the above cases should report an error with the supported types including:
    - FunctionalSourceSet
    - ModelMap<T> for any supported T
    - ModelSet<T> for any supported T
- Cannot define a property or managed element of a type which extends FunctionalSourceSet
- A property or managed element of type `FunctionalSourceSet` cannot be applied when the `LanguageBasePlugin` has not been applied.
- Model report shows something reasonable for a managed property or collections of type FunctionalSourceSet.

## Story: A `LanguageSourceSet` of any registered type can be created in any `FunctionalSourceSet` instance

- All registered `LanguageSourceSet` types are available to be added.
- Need some convention for source directory locations. Possibly add a `baseDir` property to `FunctionalSourceSet` and default source directories to `$baseDir/$sourceSet.name`
- Out-of-scope: Instances are visible in top level `sources` container.
- Currently `FunctionalSourceSet` pushes instances into `sources` container. Should change this to work the same way as binaries, where the owner of the binary has no knowledge of where its elements end up being referenced.

### Implementation

- Currently rules push language registrations into various well known instances. Should change this to work with all instances, ideally by pull rather than push.
    - Define a `LanguageRegistry` as a `@Service`. Anything needing a `LanguageRegistry` uses this instance.
    - Change the constructor of `DefaultFunctionalSourceSet` to take a `LanguageRegistry`.
    - Change`DefaultFunctionalSourceSet` to use that `LanguageRegistry` to create LSS instances (i.e. `DefaultPolymorphicNamedEntityInstantiator.factories` is no longer used to create LanguageSourceSets)
    - Change `ComponentRules.ComponentSourcesRegistrationAction#registerLanguageSourceSetFactory` to no longer push `sourceSetFactory`'s into `FunctionalSourceSet`

- Source directory locations
    - Add a `baseDir` property to `FunctionalSourceSet` and default it to `project.projectDir`
    - ~~Add an `abstract` method to `BaseLanguageSourceSet`, `String getSourceDirConvention()`. For a java LSS this would return `"src/main"`~~

- Currently `FunctionalSourceSet` pushes instances into `sources` container
    - A `@Defaults` rule is used to give `LanguageSourceSet`'s a default source directory instead of the constructor of `DefaultFunctionalSourceSet`

### Test cases
Assuming `JavaSourceSet` is registered as a `LanguageType`

- Creating `LanguageSourceSet`'s via rule sources
```groovy

class Rules extends RuleSource {
    @Model
    void functionalSources(FunctionalSourceSet sources) {
        sources.create("javaB", JavaSourceSet)
    }
}
```

- Creating `LanguageSourceSet`'s via the model DSL
```groovy

model {
    functionalSources(FunctionalSourceSet){
        java(JavaSourceSet)
    }
}
```

- An error message consistent with that of `ComponentSpec` and `BinarySpec` when a LanguageSourceSet is not supported/registered.
- The source set locations of a LSS when `fss.baseDir` has been overridden.

### Out of scope
- LanguageSourceSet instances created within a FunctionalSourceSet are visible in the model report.

## Story: Allow `LanguageSourceSet` instances to be attached to a managed type

- Allow any registered subtype of `LanguageSourceSet` to be used as:
    - A read-only property of a `@Managed` type
    - An element of managed collections `ModelSet` and `ModelMap`
    - A top level element.
- Reporting changes: `LanguageSourceSet` instances should appear as follows:

```
+ lss
      | Type:   	org.gradle.language.cpp.CppSourceSet
      | Value:  	C++ source 'lss:lss'
      | Creator: 	Rules#lss
```

### Implementation

- Add creation strategy for `LanguageSourceSet` backed by type registration (`ConstructibleTypesRegistry`).

### Test cases
- Can not create a top level LSS for an LSS type that has not been registered
- Can create a top level LSS with a rule
- Can create a top level LSS via the model DSL
- Can create a LSS as property of a managed type
- An LSS can be an element of managed collections (`ModelMap` and `ModelSet`)

### Out of scope
- Making `LanguageSourceSet` managed.
- Adding instances to the top level `sources` container.
- Convention for source directory locations. Need the ability to apply rules to every model node of a particular type to do this.

## Story: Elements of ComponentSpec.sources are visible in the model report

For each `LanguageSourceSet` configured in `ComponentSpec`.sources, the model report should display as follows:

```
+ lss
      | Type:   	org.gradle.language.cpp.CppSourceSet
      | Value:  	C++ source 'lss:lss'
      | Creator: 	Rules#lss
```

### Test cases
- Standard and custom source sets for a `JavaLibrarySpec` are visible in the model report
- Standard and custom source sets for a `NativeExecutableSpec` are visible in the model report
- Source sets for a custom `ComponentSpec` subtype are visible in the model report

## Story: Elements of ComponentSpec.sources are configured on demand

Currently `ComponentSpec.sources` is a `ModelMap`, but in `BaseComponentSpec` the implementation is backed by a `FunctionalSourceSet` instance, with values pushed to a node-backed map on creation. This means that `ComponentSpec.sources` doesn't have the usual semantics of a `ModelMap`: elements configured on demand, and visible to model rules. Switching to use a _real_ node-backed `ModelMap` instance will enable on-demand configuration of elements in `component.sources`.

### Implementation notes

- `CUnitPlugin` uses methods on `FunctionalSourceSet` that are not available on `ModelMap`. Need to convert to use `ModelMap`.
- A previous story introduced `FunctionalSourceSet.baseDir` that is used to configure the source locations for a `LanguageSourceSet`. Revert this rule to use `ProjectIdentifier.projectDir` directly.
- Each `LanguageSourceSet` needs a 'parentName', that is used to construct a project-scoped unique name, as well as configure default source locations. `LanguageSourceSet` instances created in `component.sources` are provided with the component name to determine the source location. The `NodeInitializer` that constructs a `LanguageSourceSet` will need to be aware of this relationship, and provide the correct parent name when adding an element to `component.sources`.

### Test cases

- Configuration for elements in `component.sources` is evaluated only when element is requested the collection:
    - Configuration supplied via `component.sources.beforeEach`, `component.sources.afterEach` and `component.sources.all`.
    - Configuration supplied when adding an element to `component.sources`

## Story: `BinarySpec.sources` has true `ModelMap` semantics

The work involves converting `BinarySpec.sources` to a node-backed `ModelMap` implementation, and adding test coverage to ensure that it has true `ModelMap` semantics:
- Elements appear in the model report, as per `ComponentSpec.sources`
- Elements can be addressed by model rules
- Elements are configured on demand
- Elements are added to the top-level 'sources' container

Converting this to a true `ModelMap` will add consistency, and enable the later transition to managed-type-aware node registration.

### Implementation

- Use the same approach as used to make `ComponentSpec.sources` visible to rules.
    - Will need to make `BaseBinarySpec` node backed, similar to `BaseComponentSpec`.
    - Should refactor to simplify both cases.

### Test cases
- Source sets for a `JarBinarySpec` are visible in the model report
- Source sets for a custom `BinarySpec` subtype are visible in the model report
- Elements in `BinarySpec.sources` are not created when defined: configuration is evaluated on-demand
    - Configuration supplied when registering element
    - Configuration supplied for `beforeEach`, `all` and `afterEach`
- Elements in `BinarySpec.sources`
    - Can be iterated in a model rule
    - Can be directly addressed in a model rule
    - Are added to the top-level 'sources' container
- Reasonable error message when source set added to `BinarySpec.sources` cannot be constructed

## Story: Standalone `FunctionalSourceSet` has true `ModelMap` semantics

This work involves converting `FunctionalSourceSet` to a node-backed `ModelMap` implementation, removing the use of a backing DomainObjectContainer. This change will add consistency, and enable the later transition to managed-type-aware node registration.

We will add test coverage to ensure that it has true `ModelMap` semantics:
- Elements appear in the model report
- Elements can be addressed by model rules
- Elements are configured on demand

### Test cases

- ~~Elements in a standalone `FunctionalSourceSet` are visible in the model report~~
- ~~Elements in a standalone `FunctionalSourceSet` can be addressed by model rules~~
- ~~Elements in a standalone `FunctionalSourceSet` are not created when defined: configuration is evaluated on-demand~~

## Story: Elements of `ProjectSourceSet` container are visible to rules

- TBD: Change `ProjectSourceSet` so that it is bridged in the same way as the `binaries` container, alternatively move `sources` completely into model space.
- TBD: Currently `JavaBasePlugin` contributes source sets to `sources` container.

## Plugin author declares default implementation and internal views for custom `LanguageSourceSet` subtype

Currently, `LanguageTypeBuilder` provides methods to specify both `defaultImplementation` and `internalView`.
The former is functional (possibly without adequate test coverage), and the latter is ignored.
This story is about ensuring there is adequate test coverage for `defaultImplementation`, and supporting the `internalView` functionality.

For this story we should extract the common functionality provided for `BinaryTypeBuilder` and `ComponentTypeBuilder` so that it also applies to `LanguageTypeBuilder`.
This will form the basis for adding support for other 'extensible types' via additional type builders.

Update user guide and samples to show how to implement a custom unmanaged `LanguageSourceSet` type

### Test cases

- user can declare a custom unmanaged `LanguageSourceSet` default implementation
- user can attach unmanaged internal views to custom `LanguageSourceSet`
- fails on registration when:
  - language name is not set
  - model type extends `LanguageSourceSet` without a default implementation
  - default implementation is not a non-abstract class
  - default implementation does not extend `BaseLanguageSourceSet`
  - default implementation does not implement internal views
  - an internal view is not an interface

## Story: Plugin author defines `@Managed` subtype of `LanguageSourceSet`

This story will enable plugin authors to define custom `@Managed` subtypes of `LanguageSourceSet`.
These subtypes may or may not add additional properties to the base type. For example:

    @Managed
    interface MyLanguageSourceSet extends LanguageSourceSet {
        // potential additional properties
    }

and is used as follows:

    plugins {
        id "myLanguage"
    }
    model {
        components {
            main(MyLanguageComponentSpec) {
                sources {
                    myLanguage(MyLanguageSourceSet) {
                      // potential properties
                    }
                }
            }
        }
    }

### Test cases

- ~~user can declare and use a custom managed `LanguageSourceSet`~~
- ~~user can declare custom managed `LanguageSourceSet` based on custom `LanguageSourceSet` component~~
- ~~user can target managed internal views to a custom managed `LanguageSourceSet` with rules~~
- managed `LanguageSourceSet` can be used in all places where an unmanaged `LanguageSourceSet` can be used
    - ~~as a binary's source~~
    - ~~as a component's source~~
    - as a standalone top-level source element
    - as a property of a managed type
    - as an element of a managed collection
- custom managed `LanguageSourceSet` show up properly in `model` report
- custom managed `LanguageSourceSet` show up properly in `component` report
