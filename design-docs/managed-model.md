This spec outlines some initial stories for what's generally termed “managed model”.
This is a sub stream of the “software-model” stream.

## Background

There are several key drivers for this stream of work:

1. Model insight
2. Bidirectional model externalisation
3. Model caching

The term “model insight” (#1) refers to deeply understanding the structure of model elements.
While not a goal with immediate user benefit in itself, it is included in this list as a proxy for general usability enhancements made possible by this.
For example, a model browsing tool would rely on this insight to generate visual representation of elements and facilitate browsing a model (schema and data).

The term “bidirectional model externalisation” refers to being able to serialize a data set to some “external” form (e.g. JSON, YAML, XML, Turtle etc.) for consumption by other systems, and the possibility of using such a format to construct a data set for Gradle to use.

The term “model caching” refers to the ability to safely reuse a previously “built” model element, avoiding the need to execute the user code that contributed to its final state.

Moreover, we consider owning the implementation of model elements an enabler for the general dependency based configuration model.

### Terminology

- *Scalar type*: a type that represents a single immutable value. Supported types:
    - all primitive types
    - all subtypes of Number in java.lang and java.math
    - Boolean
    - Character
    - String
    - File
    - All subtypes of Enum
- *Managed property*: a property of a model element, whose implementation and state is managed by Gradle. Generally only available for `@Managed` types, but there
may also be internal mechanisms to define such properties on other types.
- *Scalar property*: a property of a model element whose type is a scalar type.
- *Reference property*: a property of a model element whose value references another model element.

## Feature: Support more types for managed properties

### Convenient configuration of scalar typed properties from Groovy

- Convert input value:
    - `CharSequence` to any scalar type (eg `GString` to `Long`, `GString` to `String`)
    - Any scalar type to `String`.
    - Note that although Files are scalar types, they're not handled here but rather in their own story
- Update user guide, Javadocs and sample

#### Implementation

- Implementation must reuse `NotationConverter` infrastructure.

#### Test cases

- Nice error message when input value is not a `CharSequence` or of the actual property type
- Nice error message when a `CharSequence` input value cannot be converted to the property type
- `null` values are allowed for non-primitive types
- Nice error message when configuring a primitive type from a `null` input value
- "Groovy truth" is not used for configuring `boolean` or `Boolean` types; to support `boolean`/`Boolean` values resulting from `GString` expressions, only "true" (case-sensitive) is considered `true`
- No conversion is performed on input values already of the expected type
- No tests required for non-existent properties as this is handled earlier in the processing flow

### Convenient configuration of File typed properties from Groovy

- Convert input value `CharSequence` to `File` conversion relative to project directory, as per `Project.file()`.
- Update user guide, Javadocs and sample

#### Implementation

- Add `NotationConverter` support in `DefaultTypeConverters`
- Update `ManagedProxyClassGenerator` to include an overloaded setter for `File`
- Use `FileResolver` from `ServiceRegistry` stored in `ModelElementState`

#### Test cases

- Nice error message when input value is not a `CharSequence`
- Nice error message when a `CharSequence` input value cannot be converted to a File or an error occurs
- Relative and absolute paths resolve as expected
- File URLs resolve as expected
- `String`s and `GString`s with expressions resolve as expected
- Correct project directory is used in multi-project build

### Convenient configuration of File typed properties from Java

TBD: make some kind of 'project layout' or 'file resolver' service available as input to rules, which can convert String and friends to File.

#### Test cases

TBD

### Convenient configuration of collection typed properties from Groovy

- TBD: convert input values? eg add String values to a List<File>?
- Update user guide, Javadocs and samples

#### Test cases

- Nice error message when configuring a property that does not exist, for each supported pattern.
- Nice error message when input value cannot be converted.

### DSL improvements

- support `Collection` (or `Iterable`) and arrays as parameter to setter.
- support conversion of scalar types when added elements to a collection of scalars.
- support the 'setter method' pattern from legacy domain types. For a simple type, the 'setter method' is equivalent to calling `set` or using `=`(equals) in the DSL:

<!-- -->

    model {
        thing {
            baseDir 'some/dir' // same as baseDir = 'some/dir'
            retries 12 // same as retries = 12
        }
    }

- support the 'adder method' and 'setter replaces content' patterns from legacy domain types. For collection types, the 'setter method' adds new elements to the collection,
(possibly transformed to fit the target collection element type), whereas calling `set` or using `=`(equals) in the DSL replaces the collection contents:

<!-- -->

    model {
        thing {
            sourceDirs 'a', 'b' // same as sourceDirs.addAll([convertToFile('a'), convertToFile('b')])
            sourceDirs = ['a'] // same as sourceDirs.clear(); sourceDirs.add(convertToFile('a'))
        }
    }

- support `=`(equals) in the DSL for read-only properties of collection type: in that case, there should not be a call to a (non existent) setter, but it should
be syntactic sugar for `clear` followed by `addAll`.

## Backlog

### Support managed types declaring properties of type `ModelMap<T>`

    @Managed
    interface Thing {
      ModelMap<Foo> getFoos();
    }

- No setter for property allowed
- Element type must be `@Managed`
- Type taking creation methods must support subtypes
- Rule taking methods (e.g. `all(Action)`) must throw when object is read only
- All created elements implementing `Named` must have `name` property populated, matching the node link name
- Can depend on model map element by specific type in rules
- Element type cannot be any kind of type var
- Can be top level element
- Can be property of managed type

#### Backlog

- Allows `FunctionalSourceSet` even when plugin not applied.
- Allows `ModelMap<String>`.
- `ModelMap<T>` allows any kind of constructable object to be created
- Model report for top-level element of type `ModelMap<List<String>>` reports `ModelMap<List>`
    - Same for `ModelMap<ModelSet<T>>`
- `ModelMap<T>` does not allow T = `ModelMap<?>`

### Support polymorphic creation of ModelSet<T> elements

On par with `ModelMap`, add the following method:

    interface ModelSet<T> extends Set<T> {
        <S extends T> void create(Class<S> type, Action<? super T> action);
    }

- `type` given to `create()` must be a valid managed type
- `create` throws exception when set has been realised (i.e. using as an input)

The initial target for this functionality will be to replace the `PlatformContainer` model element, but more functionality will be needed before this is possible.


### Support polymorphic views of and actions on ModelSets

On par with `ModelMap`, add the following methods:

    interface ModelSet<T> extends Set<T> {
        <S> ModelSet<S> withType(Class<S> type);
        <S> void withType(Class<S> type, Action<? super S> configAction);
        <S> void withType(Class<S> type, Class<? extends RuleSource> rules);
    }

## Feature: Tasks defined using `CollectionBuilder` are not eagerly created and configured

### Plugin uses `CollectionBuilder` API to apply rules to container elements

Add methods to `CollectionBuilder` to allow mutation rules to be defined for all elements or a particular container element.

- ~~Add `all(Action)` to `CollectionBuilder`~~
- ~~Add `afterEach(Action)` to `CollectionBuilder`~~
- ~~Add `named(String, Action)` to `CollectionBuilder`~~
- ~~Add `withType(Class, Action)` to `CollectionBuilder`~~
- ~~Add `withType(Class)` to `CollectionBuilder` to filter.~~
- ~~Verify usable from Groovy using closures.~~
- Verify usable from Java 8 using lambdas.
- Reasonable error message when actions fail.

#### Issues

- Sync up on `withType()` or `ofType()`.
- Error message when a rule and legacy DSL both declare a task with given name is unclear as to the cause.
- Fix methods that select by type using internal interfaces, as only the public contract type is visible.
- Type registration:
    - Separate out a type registry from the containers and share this.
    - Type registration handlers should not invoke rule method until required.
    - Remove dependencies on `ExtensionsContainer`.

### Plugin uses method rule to apply defaults to model element

Add a way to mutate a model element prior to it being exposed to 'user' code.

- ~~Add `@Defaults` annotation. Apply these before `@Mutate` rules.~~
- ~~Add `CollectionBuilder.beforeEach(Action)`.~~
- ~~Apply defaults to managed object before initializer method is invoked.~~
- Reasonable error message when actions fail

#### Issues

- Fail when target cannot accept defaults, eg is created using unmanaged object `@Model` method.
- Handle case where defaults are applied to tasks defined using legacy DSL, e.g. fail or perhaps define rules for items in task container early.

### Plugin uses method rule to validate model element

Add a way to validate a model element prior to it being used as an input by 'user' code.

- ~~Add `@Validate` annotation. Apply these after `@Finalize` rules.~~
- ~~Rename 'mutate' methods and types.~~
- Add `CollectionBuilder.validateEach(Action)`
- Don't include wrapper exception when rule fails, or add some validation failure collector.
- Add specific exception to be thrown on validation failure
- Nice error message when validation fails.

#### Issues

- Currently validates the element when it is closed, not when its graph is closed.

### Build script author uses DSL to apply rules to container elements

    model {
        components {
            mylib(SomeType) {
                ... initialisation ...
            }
            beforeEach {
                ... invoked before initialisation ...
            }
            mylib {
                ... configuration, invoked after initialisation ...
            }
            afterEach {
                ... invoked after configuration
            }
        }
    }

- Add factory to mix in Groovy DSL and state checking, share with managed types and managed set.
- Validate closure parameter types.
- Verify:
    - Can apply rule to all elements in container using DSL
    - Can apply rule to single element in container using DSL
    - Can create element in container using DSL
    - Decent error message when applying rule to unknown element in container
    - Decent error message when using DSL to create element of unknown/incorrect type

#### Issues

- Currently does not extract rules or input references at compile time. Rules are added at execution time via a `CollectionBuilder`.

### Tasks defined using `CollectionBuilder` are not eagerly created and configured

- Change `DefaultCollectionBuilder` implementation to register a creation rule rather than eagerly instantiating and configuring.
    - Verify construction and initialisation action is deferred, but happens before mutate rules when target is used as input.
    - Verify initialisation action happens before `project.tasks.all { }` and `project.tasks.$name { }` actions.
    - Reasonable error message is received when element type cannot be created.
- Attempt to discover an unknown node by closing its parent.
- Apply consistently to all model elements of type `PolymorphicDomainObjectContainer`.
- Apply DSL consistently to all model elements of type `PolymorphicDomainObjectContainer`.

### Implicit tasks are visible to model rules

Options:

- Bridge placeholders added to `TaskContainer` into the model space as the placeholders are defined.
- Flip the relationship so that implicit tasks are defined in model space and bridged across to the `TaskContainer`.

Tests:

- Verify can use model rules to configure and/or override implicit tasks.

Don't do this until project configuration closes only the tasks that are required for the task graph.

Other issues:

- Remove the special case to ignore bridged container elements added after container closed.
- Handle old style rules creating tasks during DAG building.
- Add validation to prevent removing links from an immutable model element.

## Feature: Support for managed container of tasks

### ModelSet defers creation of elements

- Reasonable error message when action fails.
- Support 'anonymous' links
- Sync view logic with `CollectionBuilder` views.
- Use same descriptor scheme as `CollectionBuilder`.
- Use more efficient implementation of set query methods.
- Allow `ModelSet` to be used as a task dependency.
- Fail when mutator rule for an input is not bound.

### ModelSet supports Groovy DSL

- Support passing a closure to `create()`
- Validate closure parameter types.

### Support for managed container of tasks

- Rename `CollectionBuilder` to `ManagedMap`.
- Currently it is possible to get an element via `CollectionBuilder`, to help with migration. Lock down access to `get()` and other query methods.
    - Same for `size()`.
- Lock down read-only view of `ManagedMap`, provide a `Map` as view.
- Change `ManagedMap` to extend `Map`.
- Mix in the DSL conveniences into the managed collections and managed objects, don't reuse the existing decorator.
- Allow a `ManagedMap` to be added to model space using a `@Model` rule.
- Synchronisation back to `TaskContainer`, so that `project.tasks.all { }` and `project.tasks { $name { } }` works.
- Need efficient implementation of query methods that does not scan all linked elements to check type.
- Implement `containsValue()`.
- Separate out type registry from the component/binary/source set containers.
- Allow `ManagedMap` and `ManagedMap.values()` to be used as a task dependency.
- Add `toString()` implementation.

### Expose a `ManagedMap` view for all model elements of type `PolymorphicDomainObjectContainer`.

- Currently blocked on `NativeToolChainRegistryInternal.addDefaultToolChains()`
- `TestSuiteContainer` should extend `PolymorphicDomainObjectContainer`.
- `ProjectSourceSet` should extend `PolymorphicDomainObjectContainer`.

### Support for managed container of source sets

- Add `all(Action<? super T)` method to `ModelSet`.
- Add DSL support for `all { }` rule.

### Story: Display managed properties of `@Managed` subtypes of extensible types in reports

Make sure `@Managed` subtypes of extensible types (`ComponentSpec`, `BinarySpec`, `LanguageSourceSet`) show up in a
friendly way in both the `model` and `components` reports.

#### Test cases

- Managed instances in `model` report display their public type
- Managed instances in `components` report display their public type
- Managed properties are visible in `model` report

### ​ModelSchemaExtraction should respect the JavaBean spec wrt. property naming

When extracting model properties from types we map get/setters to property names.
There's something shaky about how we handle property names / get-setters mapping wrt. (un)capitalization of names.
This is about corner cases like property names starting with a single lowercase letter or getters with full-capitalized names.

Here are some examples of what we are doing currently (setters omited for brevity):

    getCCompiler() -> cCompiler
    getcCompiler() -> cCompiler, or "not a property" on managed types
    getURL() -> uRL

If we were following the JavaBean spec, then we should have:

    getCCompiler() -> CCompiler
    getcCompiler() -> cCompiler
    getURL() -> URL

And when used from the Groovy DSL, things get messier.
On his side, Groovy does follows the JavaBean spec.
Mixed Gradle and Groovy behaviour can lead to terrible situations.

If we take the `getCCompiler()` example above, and try to set such a property from Groovy:

    model {
        whatever {
            // Both of theses statements work, note the name mistmatch and presence/abscence of the equal sign:
            cCompiler 'clang'
            CCompiler = 'clang'
            // And both of theses statements fail:
            CCompiler 'clang'   // Groovy: unexpected token '4.12'
            cCompiler = 'clang' // Groovy: No such property
        }
    }

#### Tests

- Gradle property names and Groovy property names are equals
- Gradle property naming respect the JavaBean spec corner cases as documented/implemented in `java.beans.Introspector`.
- Adapt `DefaultModelSchemaExtractorTest` and `DefaultStructBindingStoreTest` to take the specs into account wrt. naming and getters filtering

#### Implementation notes

This boils down to using `java.beans.Introspect.decapitalize(..)` instead of `o.a.commons.lang.StringUtils.uncapitalize(..)` in `PropertyAccessorType`.

#### Potential breaking changes

- `NativeBinarySpec.getcCompiler()`
	- Nothing to do, no impact
- `CoreJavadocOptions.getJFlags()`
	- Should be renamed `getjFlags()`
	- No DSL impact
	- Breaking change for Java API users
- `JUnitTestSuiteSpec` & `JUnitTestSuiteBinarySpec` `.getJUnitTestSuite()`
	Should be renamed `getjUnitTestSuite()`
	No breaking change as this is new stuff

#### Potential breakages outside Gradle

People will no more see name mismatches in groovy DSL.
Build scripts relying on theses mismatches will break.

## Backlog

- Documentation and samples

### Managed type constraints/features

- Value types:
    - Should support `is` style getters for boolean properties.
    - Should support all numeric types, including `Number`. (?)
    - Should support primitives. (?)
    - Should support more value types, such as `File` and `CharSequence`
- Support parameterized non-collection types as managed types

### Type coercions and general conveniences

- Mix DSL and Groovy methods into managed type implementations.
    - Add DSL and Groovy type coercion for enums, closures, files, etc
    - Improve 'missing property' and 'missing method' exceptions.
    - 'Cannot set read-only property' error messages should report public type instead of implementation type.
    - Error message attempting to set property using unsupported type should report public type and acceptable types.
    - Missing method exception in DSL closure reports the method missing on the containing project, rather than the delegate object. Works for abstract Groovy class.
- Add Java API for type coercion

### Collections

- Implement `add()` and treat it as adding a reference, and remove() and treat it as removing a reference.
- Collections of value elements
- Collections of collections
- Map type collections
- Ordered collections
- Semi ordered collections (e.g. command line, where some elements have an order relationship)
- Maps of value elements
- Maps of model elements
- Collections of implicitly keyed elements, acting as a map
- Equality concerns when using sets

### Extensibility & views

- Convenience and/or enforcement of “internal to plugin” properties and views/types
- Extending model elements with new properties
- Specializing model elements to apply new views
- Reporting on usages of deprecated and incubating properties and views/types

### Performance

- ModelSchema does not hold reference to schemas for property types or collection element types
    - Each time a managed object is created, need to do a cache lookup for the schema of each property.
    - Each time a managed set is created, need to do a cache lookup for the schema of the element type.
    - Each time a managed object property is set, need to do a cache lookup to do validation.
- Managed projections should be reused per type
- Unmanaged projections should be reused per type
- Unmanaged instance model views should be used for the life of the node (right now we create a new view each time we need the node value)
- Read only views should not strongly reference backing node once the view has been fully realized

### Behaviour

- Managed types should be able to internally use service (with potentially build local state) to provide convenience methods (?)
- Declaring “services” (i.e. behaviour is interesting, not state) using same “techniques” as managed types?

### Misc

- Audit of error messages
    - Invalid managed types
    - Immutability violations
- Semantics of equals/hashCode
- Support getting "address" (creation/canonical path) of a managed object
- Throw a meaningful exception instead of failing with `OutOfMemoryError` at runtime when a managed type instantiation cycle is encountered (a composite type that contains an instance of itself keeps on creating new instances indefinitely)
- Attempt to call setter method in (abstract class) managed model from non-abstract getter receives error when extracting type metadata (i.e. fail sooner than runtime)
