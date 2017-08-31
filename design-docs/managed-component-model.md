
Currently much of the software component model is not managed, and so is not visible to rules and cannot take any real
advantage of the features of that rule based configuration offers.

This spec outlines several steps toward a fully managed software component model.

# Plugin author uses managed types and internal views to extend the software model

This feature allows a plugin author to extend certain key types using a managed type:

- `ComponentSpec`
- `LanguageSourceSet` (see [managed-source-sets.md](./managed-source-sets.md))
- `BinarySpec`
- `JarBinarySpec`

It is a non-goal of this feature to add any further capabilities to managed types, or to migrate any of the existing subtypes to managed types.

## Implementation

This feature will require some state for a given object to be unmanaged, possibly attached as node private data, and some state to be managed, backed by
individual nodes.

## Custom JarBinarySpec type is implemented as a @Managed type (DONE)

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

## Plugin author declares internal view for custom component type

Given a `ComponentSpec` subtype with default implementation extending `BaseComponentSpec`, allow one or more internal views to be
registered when the component type is registered:

    @ComponentType
    public void registerMyType(ComponentTypeBuilder<MyType> builder) {
        builder.defaultImplementation(MyTypeImpl.class);
        builder.internalView(MyTypeInternal.class);
        builder.internalView(MyInternalThing.class);
    }

- Each internal view must be an interface. The interface does not need to extend the public type.
- For this story, the default implementation must implement each of the internal view types. Fail at registration if this is not the case.
- Model report shows elements as public view type. Internal views and properties defined in them are not shown in the report for this story.
- Internal view type can be used with `ComponentSpecContainer` methods that filter components by type, eg can do `components { withType(MyTypeInternal) { ... } }`.
- Rule subjects of type `ModelMap<>` can be used to refer to nodes by an internal view.
- Each component spec should have the internal view `ComponentSpecInternal` configured by default.

### Test cases

- Internal view type can be used with `ComponentSpecContainer.withType()`.
    - a) if internal view extends public view
    - b) if internal view does not extend public view
- Internal view type can be used with rule subjects like `ModelMap<InternalView>`.
    - a) if internal view extends public view
    - b) if internal view does not extend public view
- Model report shows only public view type properties.
- Error cases:
    - Non-interface internal view raises error during rule execution time.
    - Default implementation type that does not implement public view type raises error.
    - Default implementation type that does not implement all internal view types raises error.
    - Specifying the same internal view twice raises an error.

## Plugin author declares internal view for custom non-managed binary types

Given a `BinarySpec` subtype with default implementation extending `BaseBinarySpec`, allow one or more internal views to be
registered when the binary type is registered:

    @BinaryType
    public void registerMyType(BinaryTypeBuilder<MyType> builder) {
        builder.defaultImplementation(MyTypeImpl.class);
        builder.internalView(MyTypeInternal.class);
        builder.internalView(MyInternalThing.class);
    }

- Each internal view must be an interface. The interface does not need to extend the public type.
- For this story, the default implementation must implement each of the internal view types. Fail at registration if this is not the case.
- Internal view type can be used with `BinarySpecContainer` methods that filter binaries by type, eg can do:

        components {
            myComponent {
                binaries.withType(MyTypeInternal) { ... }
            }
        }

- Internal view type can be used with top-level `BinaryContainer`, eg can do `binaries { withType(MyTypeInternal) { ... } }`.
- Rule subjects of type `ModelMap<>` can be used to refer to child nodes of `binaries` by an internal view.
- Each binary spec should have the internal view `BinarySpecInternal` configured by default.

### Test cases

- Internal view type can be used with `BinarySpecContainer.withType()`.
    - a) if internal view extends public view
    - b) if internal view does not extend public view
- Internal view type can be used with `BinaryContainer.withType()`.
    - a) if internal view extends public view
    - b) if internal view does not extend public view
- Internal view type can be used with rule subjects like `ModelMap<InternalView>`.
    - a) if internal view extends public view
    - b) if internal view does not extend public view
- Error cases:
    - Non-interface internal view raises error during rule execution time.
    - Default implementation type that does not implement public view type raises error.
    - Default implementation type that does not implement all internal view types raises error.
    - Specifying the same internal view twice raises an error.

### Implementation

- Should start to unify the type registration infrastructure, so that registration for all types are treated the same way and there are few or no differences between the implementation of component, binary and source set type registration rules. This will be required for the next stories.

## Plugin author declares default implementation for extensible binary and component type

Given a plugin defines a general type, allow the plugin to provide a default implementation the general type.
This default implementation is then used as the super class for all `@Managed` subtype of the general type. For example:

    class BasePlugin extends RuleSource {
        @ComponentType
        public void registerBaseType(ComponentTypeBuilder<BaseType> builder) {
            builder.defaultImplementation(BaseTypeInternal.class);
        }
    }

    @Managed
    interface CustomType extends BaseType { }

    class CustomPlugin extends RuleSource {
        @ComponentType
        public void registerCustomType(ComponentTypeBuilder<CustomType> builder) {
            // No default implementation required
        }
    }

- Generalise the work done to allow `@Managed` subtypes of `JarBinarySpec` to support this.
- Allow for binaries and components.

### Test cases

- user can declare a base binary type and extended it with a `@Managed` subtype
- user can declare a base component type and extended it with a `@Managed` subtype
- user can attach internal view to custom type
- internal views registered for managed super-type are available on custom managed type
- internal views registered for unmanaged super-type are available on custom managed type
- fails on registration when:
    - registered implementation type is an abstract type
    - registered implementation type does not have a default constructor
    - registered implementation type does not extend `BaseBinarySpec` or `BaseComponentSpec`, respectively
    - registered managed type extends base type without a default implementation (i.e. `BinarySpec`)
    - registered managed type extends multiple interfaces that declare default implementations

## Plugin author declares managed internal view for extensible type

Allow a node with an unmanaged instance of an extensible type (say a `DefaultJarBinarySpec`) to be viewed as a `@Managed` internal view.
The internal view need not be implemented by the default implementation. The state of the properties defined in the internal view are
stored in child nodes (just as with any `@Managed` internal view).

    @Managed
    interface MyJarBinarySpecInternal extends JarBinarySpec {}

    @Managed
    interface MyInternal {}

    class CustomPlugin extends RuleSource {
        @BinaryType
        public void register(BinaryTypeBuilder<JarBinarySpec> builder) {
            builder.internalView(MyJarBinarySpecInternal)
            builder.internalView(MyInternal)
        }

        @Mutate
        void mutateInternal(ModelMap<MyJarBinarySpecInternal> binaries) {
            // ...
        }
    }

    apply plugin: "jvm-component"

    model {
        components {
            myComponent(JvmLibrarySpec) {
                binaries.withType(MyJarBinarySpecInternal) {
                    // ...
                }
                binaries.withType(MyInternal) {
                    // ...
                }
            }
        }
    }

### Test cases

* Can attach `MyJarBinarySpecInternal` that extends `JarBinarySpec`
    * internal view can declare a read-write property that can be set on a Jar binary
    * regular `JarBinarySpec` binaries can be accessed via `component.binaries.withType(MyJarBinarySpecInternal)`
    * instance cannot be accessed as `MyInternal`
* Can attach `MyInternal` that does not extend `JarBinarySpec`
    * internal view can declare a read-write property that can be set on a Jar binary
    * regular `JarBinarySpec` binaries can be accessed via `component.binaries.withType(MyInternal)`
    * instance cannot be accessed as `MyJarBinarySpecInternal`

### Implementation

* Attach managed projections based on the registered `@Managed` internal views for these nodes.

### Open issues

* Managed internal views registered on extensible type are not available in the top-level `binaries` container, e.g. via `binaries.withType(MyJarBinarySpecInternal)`. To fix this the top-level container would need to contain references to the actual component binary nodes instead of the copies of unmanaged views it contains now.

## Plugin author declares internal views for any extensible type

Given a plugin defines a general purpose type that is then extended by another plugin, allow internal views to be declared for the general super type as well as the
specialized type. For example:

    class BasePlugin extends RuleSource {
        @ComponentType
        public void registerBaseType(ComponentTypeBuilder<BaseType> builder) {
            builder.internalView(BaseTypeInternal.class);
        }
    }

    interface CustomType extends BaseType { }

    class CustomPlugin extends RuleSource {
        @ComponentType
        public void registerCustomType(ComponentTypeBuilder<CustomType> builder) {
            builder.internalView(MyCustomTypeInternal.class);
        }
    }

The views defined for the general type should also be applied to the specialized type. So, in the example above, every instance of `CustomType` should have the
`BaseTypeInternal` view applied to it.

- Allow for all types that support registration.
- Change all usages of `@ComponentType` and `@BinaryType` in core plugins to declare internal view types.
- Add a rule to the base plugins, to declare internal view types for `ComponentSpec` and `BinarySpec`.
- Change node creation so that implementation is no longer available as a view type.


## Plugin author defines `@Managed` subtype of core type without providing implementation

Change core plugins to declare default implementations for `ComponentSpec` and `BinarySpec`. This will allow `@Managed` subtypes of each
of these types.

```
@Managed
interface MyComponentSpec extends ComponentSpec {
    String getValue()
    void setValue(String value)
}

class Rules extends RuleSource {
    @ComponentType
    void registerMyComponent(ComponentTypeBuilder<MyComponentSpec> builder) {
    }
}

model {
    components {
        myThing(MyComponentSpec) {
            value = "alma"
        }
    }
}
```

- Include in release notes

### Implementation

- Default implementations needs to be allowed for multiple levels. In case of multiple default implementations the most specific one should be used.
  Example: `BinarySpec` has its own default implementation, yet `JarBinarySpec` can specify its own. `@Managed` types extending `JarBinarySpec` should delegate to the default implementation of `JarBinarySpec` as it is more specific than the default implementation declared for `BinarySpec`.

### Test cases

- A `@Managed` subtype of `ComponentSpec`, `LibrarySpec` and `ApplicationSpec` can be used to declare a component
    - it should be possible to attach binaries to the `@Managed` component
- A `@Managed` subtype of `BinarySpec` can be used to declare a binary
    - `isBuildable()` should return `true`
- An unmanaged subtype of `BinarySpec` (with its more-specific default implementation) can be extended via a `@Managed` subtype
  - this is already covered by `CustomJarBinarySpecSubtypeIntegrationTest`
- Verify how instances of managed subtypes show up in component report


## Plugin author declares internal views for custom managed type

Given a plugin defines a `@Managed` subtype of a general type, allow the plugin to define internal views for that type.

- Allow for all types that support registration.
- Each internal view must be an interface. The interface does not need to extend the public type.
- Each internal view must be `@Managed` when no default implementation is declared.
- Allow an internal view to make a property mutable.
- Allow an internal view to specialize the type of a property. This implicitly adds a view to the property node.
- Allow an internal view to declare additional properties for a node. These properties should be hidden.
- Generate a proxy type for each view type.
- Remove constraint the default implementation should implement the internal view types. Instead, use the proxy type.
- toString() and missing property/method error messages should reflect view type rather than implementation type, for generated views.

## Plugin author declares internal views for any managed type

Allow a rule to declare internal views for any `@Managed` type.

## Convert our plugins to use internal views and managed subtypes of ComponentSpec

Investigate the `ComponentSpec` type hierarchy to find what types could benefit from the following new features, and apply them:
- internal views, they now work for both unmanaged and managed types
- managed subtypes of unmanaged types

### Implementation notes

- Introduce internal views all along the type hierarchy and remove as much casts as possible, mostly from rules.
- Make types `@Managed` starting from leafs of the `ComponentSpec` hierarchy.

#### Identified candidates

- `PlayApplicationSpec`
    - `PlayApplicationSpecInternal` can be made an internal view.
    - `PlatformAwareComponentSpec` aspect of it can be extracted in a dedicated unmanaged super-type (eg. `PlayPlatformAwareComponentSpec`) registered with its own implementation
      which would provide the implementation for the unmanaged `platform(String)` behavior method.
    - Then, `PlayApplicationSpec` can be `@Managed` with a single property: `injectedRoutesGenerator`.
- `NativeTestSuiteSpec`
    - It can be `@Managed` and its `testedComponent` property can be made `@Unmanaged`

### Tests

- No existing test should break

## Convert our plugins to use internal views and managed subtypes of BinarySpec

Investigate the `BinarySpec` type hierarchy to find what types could benefit from the following new features, and apply them:
- internal views, they now work for both unmanaged and managed types
- managed subtypes of unmanaged types

### Implementation notes

- Introduce internal views all along the type hierarchy and remove as much casts as possible, mostly from rules.
- Make types `@Managed` in tests and samples
- Make types `@Managed` starting from leafs of the `BinarySpec` hierarchy.

### Tests

- No existing test should break

## Convert our plugins to use internal views and managed subtypes of LanguageSourceSet

- TBD

## User guide contains details and samples on how to use managed subtypes and internal views

A decision was made during the implementation of other stories on this theme to wait on adding user guide documentation
until all or most of the stories were complete. This card ensures we pay off this debt before calling the overall theme
complete.

Proposed table of content:

*Extending the software model*

- Concepts
    - Public type and base interfaces
    - Internal views
    - The component -> binary -> task chain
- Components
  (extending `ComponentSpec`, mention `LibrarySpec` and `ApplicationSpec`)
- Binaries
  (extending `BinarySpec`)
- Source sets
  (extending `LanguageSourceSet`, language name)
- Putting it all together
    - Generating binaries from components
      (`@ComponentBinaries`)
    - Generating tasks from binaries
      (`@BinaryTasks`)
- Wrap up

The documentation should focus on how to use `@Managed` types (e.g. how to declare a `@Managed` subtype of an extensible
type) and `@Managed` internal views. The unmanaged infrastructure should be treated as internal to Gradle, and should
only be documented where it is necessary for clarity. The goal is for users to be able to play around with the managed
infrastructure, and exposing the unmanaged infrastructure is not required to reach that goal.

- Insert a new user guide section titled "Extending the software model" last in the "The Software model" chapter.
- Update/Create samples to show how to implement a custom `@Managed` `ComponentSpec`, `BinarySpec` and `LanguageSourceSet` type
- Eventually create samples to support the user guide content.

## Model report does not show internal properties of an element

Infer a model element's hidden properties based on the parent's views:

- When a property is declared on any of the parent's public view types, that property should be considered public, even if it is also declared on an internal view type
- When a property is declared only on the parent's internal view types, that property should be considered hidden and not shown.
- Add an option to model report to show all hidden elements and types, named `showHidden`

### Test cases

- Add a test that register customs binaries, components and languages all having internal views and assert their internal properties are not present in the report
- Add a test that do the same as the test above but requires the model report to show all hidden elements and types and assert their presence in the report
- Add a test that register an element that have some property declared both in public and internal views and assert is not hidden

### Implementation

`ModelNode` already has a `hidden` flag that is taken into account by `ModelReport`.
This is how services are hidden in the model report.

Implementation goal is then to set this `hidden` flag on `ModelNode`s backing internal views.

This should be done as core model finalization rules for Component, Binary and Language types that would use a newly introduced `InstanceFactory.getHiddenProperties(ModelType<T> type)` method.
`InstanceFactory` being the guy who knows all public and internal views and so is able to resolve properties publicity.

### Open Issues

Only extensible types (BinarySpec, ComponentSpec, LanguageSourceSet) can have internal views for now.
Once any type can have internal views, another story will be needed to assert that the implementation of this very story for extensible types works for any types.

## Allow managed types to extend non-managed interfaces

Plugin author should be able to reuse existing types (from an existing external library perhaps) as a public type or as an internal view type
in declaring managed types.

We already do most things the same way with managed and unmanaged views of an element. For example we always generate a proxy. It makes sense
to make a further step, and merge managed and unmanaged structs together. The idea is basically to drop `@Managed` as a concept, and simply check
if all methods declared by the public type and internal views can be implemented either by:

* a concrete implementation on the public type (internal views are required to be interfaces, so no implementation is possible there)
* the default implementation
* as a managed property

This also means that all validation that currently happens in schema extraction will be pushed to when the `InstanceFactory` is being validated, or when
`@Model` triggers an instance of a type to be created in a managed fashion.

### Implementation

* Deprecate `@Managed` annotation

* **Struct schema extraction**—instead of a separate managed/unmanaged version, we only have one `StructSchema`. The schema extraction is lenient (i.e. it does not report any errors), as we need to extract schemas from types that will never be used to instantiate model elements, and we must allow "errors" to be present there (think of `ClassLoader.getResource(URL)`). All validation happening in schema extraction needs to be moved to before instantiation.

  * first we get the methods from the type as usual, indexed into a `Multimap` by signature with all their overrides (the overrides are only needed in order to extract annotations defined in super-types)
  * we try to find methods that belong to a property (`getX()`, `isX()`, `setX()`) and index them by property name; we leave all methods we cannot identify as belonging to a property in an "arbitrary methods" bucket.
  * we check each property if we can actually make the methods belonging to it work as a property (i.e. check if they getter returns a type that's compatible with the setter etc.)—if we _can_ make it a property, we create a `ModelViewProperty` out of it; otherwise we put the methods back into "arbitrary methods".
  * we extract aspects from the properties we could make sense of in the usual way.

* **Type registration** happens as it does now, supplying a public type, some internal view types, and a default implementation.

* **Managed type validation** happens in two places: a) when `InstanceFactory` is validated (we have internal views and a potential default implementation to take into account here), and
b) when a type is instantiated in a managed fashion via `@Model`.

  * check view types
    * view types must not have state (i.e. non-static fields)
    * internal view types must be interfaces
    * (public) view type should not provide concrete implementations for any `Object` (except `toString()`) or `GroovyObject` methods or define `propertyMissing()` or `methodMissing()` methods
  * we collect all the methods from the public type and the internal views: these are the methods that the delegate object needs to provide; the different view proxies are going to delegate to this instance
    * we tag each method that is not abstract with `IMPLEMENTED_BY_VIEW_TYPE`
    * we try to match the rest of the methods against the methods from the default implementation (if any)   and mark them as `IMPLEMENTED_BY_DEFAULT_IMPLEMENTATION`
    * the rest of the methods remain tagged as `GENERATED`
  * we repeat the same process with the methods as we did for the schema, and group the methods belonging to potential managed properties; we leave all other methods in an "arbitrary methods" bucket.
  However, if there is a method that does not make sense as a property accessor (e.g. `getX(int)`), or if the property itself does not make sense (incompatible type derived from getter and setter), we fail.
  * depending on the methods collected for each potential managed property:
    * if all methods are implemented (either by the public type or the default implementation), we throw that property away (we won't need to add a child node for it)
    * if all methods are abstract, we run the usual validation required for managed properties, and store a `ManagedProperty` instance with the data about the property (we will create a child node for this property); we also record if the getter was annotated with `@Unmanaged`.
    * if some of the methods are abstract, while others have implementations, we fail
  * if there are any methods left in the "arbitrary methods" bucket without an implementation, we fail
  * we store the information about the `ManagedProperty`'s and the "arbitrary methods" in the `ManagedType` object (should probably be cached in `InstanceFactory`, too)

* **Node initialization** happens similar to how it does now with some changes:

  * the `ManagedNodeInitializer` keeps a reference to the `ExtensibleTypeRegistration`
  * during the discovery of the node (`Discover`), projections are created based on the type registration

* **Viewing the node**

  * the proxy generator takes the schema of the requested view and the `ExtensibleTypeRegistration` (both received from the `StructProjection`)
  * it generates each method found in the view schema based on the type registration; it either
    * delegates to the implementation in the view type (only allowed for the public type now)
    * delegates to the default implementation
    * delegates to a managed property
  * the logic in the proxy generator is reduced, as there are no decisions to make there (and hence no chance of illegal states to arise)

### Test cases

* all current test cases for managed and unmanaged type validation and instantiation should keep working, but without `@Managed` being specified
* using `@Managed` on a type shows a deprecation warning
* managed type validation

# Feature 6: Managed Model usability

Some candidates:

- Consistent validation when managed type, Collection, ModelMap and ModelSet are mutated as inputs.
    - Directly
    - When parent is input.
- Consistent validation when managed type, Collection, ModelMap and ModelSet are mutated after view is closed.
- Consistent validation when managed type, Collection, ModelMap and ModelSet are mutated as subject that is not mutable (eg in validation).
    - Directly
    - When parent is subject
- Consistent error message for managed type property, ModelMap<T> and ModelSet<T> where T is not managed.
- Consistent usage of ModelMap and ModelSet with reference properties.
- Consistent mutation methods on ModelMap and ModelSet.
- Enforce that `@Defaults` rules cannot be applied to an element created using non-void `@Model` rule.
- Enforce that subject cannot be mutated in a validate rule.
- Rename (via add-deprecate-remove) the mutation methods on ModelMap and ModelSet to make more explicit that
  they are intended to be used to define mutation rules that are invoked later. For example `all { }` or `withType(T) { }` could have better names.
- Add methods (or a view) that allows iteration over collection when it is immutable.
- Rename (via add-deprecate-remove) `@Mutate` to `@Configure`.
- Allow empty managed subtypes of ModelSet and ModelMap. This is currently available internally, eg for `ComponentSpecContainer`.
- Adjust naming schemes so that it excludes only those variant dimensions with a single value provided by a `@Defaults` rule.

# Feature 7: Build author applies cross-cutting rules for software model types

Link all `ComponentSpec`, `LanguageSourceSet`, `BinarySpec` and `Task` instances into top level containers where rules can be
applied to them regardless of their location in the model.

Non-goal is to provide this as a general capability for arbitrary types.

### Backlog

- Improve error message when input or subject cannot be bound due to a null reference.
- Deal with case where by-path binding points to a null reference or null scalar value. Currently we supply a null value to the rule, should probably fail.
- Deal with by-type binding to a non-null reference.
- Currently an input or subject reachable via a reference can be viewed only as the types from the reference definition. Instead, should be able to view the target
using any type that the view supports.
- The target of a reference value can be changed while mutation is allowed. Treat reference change as remove and add.
- Can't remove an element when it is the target of a reference.

## Model report shows references between elements

- Creator and mutator rules should be those that affected the value of the reference, not the target.

### Test cases

- Reference is `null`.
- Reference is not `null`.
- Can mutate reference value during configuration.
- Cycle from child to parent.

### Backlog

- A reference is almost always set via a rule on the parent or an ancestor. This is not captured, so that in the report the reference does not appear to be
configured by any rule.

## Referenced element can be used as subject for a rule

- For defaults, finalization and validation rules.
- Can only be applied when the target of the reference still allows these rules to be applied.
- Error messages when rule cannot be applied.
- Out of scope: locating referencing elements in the model, in order to inject rules via the references. This is intended to be used internally
only to implement the top level containers.

## Model containers allow elements to be added as references

- Adding a managed element to a model container should be treated as adding a reference to the target element.

## Language source sets are linked into top level container

- Change `LanguageSourceSet` implementations so that they are node backed.
- Apply the above capabilities to the `sources` top level container.

## Binaries are linked into top level container

- Change `BinarySpec` implementations so that they are node backed.
- Apply the above capabilities to the `binaries` top level container.

## Run only those rules that define cross-cutting configuration

- Don't need to discover the elements of a top-level container in order to apply cross-cutting rules. However, the approach so
far forces all elements to be discovered.

## Apply cross cutting configuration to all `LanguageSourceSet` instances

- All `LanguageSourceSet` instances are visible through `sources` container
- Depends on improvements to reference handling define in previous feature.
- TBD: Need to traverse schema to determine where source sets may be found in the model. Ensure only those model elements that are required are realized.
- TBD: Need to split this up into several stories.

## Backlog

- Apply to `ComponentSpec`, `Task`, etc.
- Apply cross-cutting defaults, finalization, validation.
- Allow rules to be applied to any thing of a given type, relative to any model element.

# More consistent validation of model types

## Story: Consistent validation of model types

- Validate model elements of the following types (check for existing test coverage, some of this may already exist):
    - ModelMap<T> and ModelSet<T> where T is not constructable. Should report which types are constructable (should not mention `@Unmanaged`)
    - List<T> and Set<T> where T is not a scalar type. Should report which types are scalar.
    - A `@Managed` type with a read-only property of type T where T is not constructable. Should report which types are constructable.
    - A `@Managed` type with a read-write property without `@Unmanaged` of type T where T is not scalar and not constructable. Should report scalar and constructable types.
    - Any T where T is not constructable. Should report which types are constructable.
- Ensure a consistent error message for each failure, should describe the available T for each case.

### Implementation
Coverage for all of this is already in place. The validation logic will change to give contextual error messages. For example:
When attempting to define a managed model element of type `SomeManagedType` with a property of type `List<T>` where `T` is not a
scalar type the error message should read:

```
> A model element of type: 'SomeManagedType' can not be constructed.
  Its property 'List propertyName' is not a valid scalar collection type.
  A scalar collection type is a List<T> or Set<T> where 'T' is a scalar type (String, Boolean, Character, Byte, Short, Integer, Float, Long, Double, BigInteger, BigDecimal, File)
```

`org.gradle.model.internal.core.DefaultNodeInitializerRegistry` and `org.gradle.model.internal.core.ModelTypeInitializationException` should be refactored to take the context of what is being constructed
 (i.e. is it a property, is it a top level model element, if it is a property the property's name)

## Story: Report available types for a `ModelMap` or `ModelSet` when element type is not constructible

When adding an element to a `ModelMap<T>` or `ModelSet<T>` and `T` is not constructible, use a specific error message that informs
the user that an element of type `T` cannot be added to the collection. Error message should include the constructible types:

- When `T` extends BinarySpec or ComponentSpec, report on the registered subtypes.
- Otherwise, report on the constructible types that are assignable to `T`.

### Test cases

- Fix `ComponentModelIntegrationTest.reasonable error message when creating component with no implementation`. This used to report the available types.
- Fix `ComponentModelIntegrationTest.reasonable error message when creating component with default implementation`. This used to report the available types.
- Add `ComponentModelIntegrationTest.reasonable error message when creating binary with no implementation`.
- Add `ComponentModelIntegrationTest.reasonable error message when creating binary with default implementation`.
- Add `ManagedNodeBackedModelMapTest.reasonable error message when creating a non-constructible type`.
- Add `UnmanagedNodeBackedModelMapTest.reasonable error message when creating a non-constructible type`.
- Add `DomainObjectCollectionBackedModelMapTest.reasonable error message when creating a non-constructible type`.

For all theses tests, assert that the reported constructible types list contains appropriate types and only them.

### Open Issues

`ModelSet` contract does not allow to specify the type of element when adding one. The `elementType` is always used.
In other words, there's no way to add an element of a different type that the `ModelSet` parameterized one.

So, this test has not been implemented:

- Add `ModelSetIntegrationTest.reasonable error message when creating a non-constructible type`.

## Backlog

- `ModelMap` Does not fail when type not within bounds is created or added.
- There are inconsistent error messages when a 'schema' vs 'node-initialization' problem is found with a `@Managed` type
    - For example, adding a read-only property of type `boolean` and `Boolean` fail in completely different ways

## Story: Validate model types more eagerly

- Validate the type of all top level elements, regardless of whether they are used or not in the current build.
- Do this at the same time as `ModelRegistry.bindAllReferences()` is used.
- Do not validate projects that are not used in the build current build.
- Validate elements added via DSL and rules.
- Other elements should be validated as they are realized.

TBD:
One option is to do so in `ModelRegistry.bindAllReferences()` (which might be renamed to `validateRules()`). It could just transition everything currently known to ‘discovered'
that should shake out a bunch of errors without closing the universe. The idea isn’t necessarily to catch every possible failure that might happen, just to be a reasonable trade off between
coverage and the cost of the coverage

## Story: Allow `@Unmanaged` properties of type `List` or `Set`

This is a bugfix for a regression introduced in Gradle 2.8.

Allow a read-write property marked with `@Unmanaged` of a `@Managed` type to have type `List<T>` or `Set<T>` for any `T`.

# Later features

# Feature: Key component model elements are not realized until required

This feature continues earlier work to make key properties of `BinarySpec` managed.

- `BinarySpec.tasks`

# Feature: Build logic defines tasks for generated source sets and intermediate outputs

This feature generalizes the infrastructure through which build logic defines the tasks that build a binary, and reuses it for generated source sets and intermediate outputs.

A number of key intermediate outputs will be exposed for their respective binaries:

- Native object files
- JVM class files
- Generated source for play applications

Rules implemented either in a plugin or in the DSL will be able to define the tasks that build a particular binary from its intermediate outputs, an intermediate output from its input source sets, or a particular source set. Gradle will take care of invoking these rules as required.

Rules will also be able to navigate from the model for a buildable item, such as a binary, intermediate output or source set, to the tasks, for configuration or reporting.

The `components` report should show details of the intermediate outputs of a binary, the input relationships between the source sets, intermediate outputs and binaries, plus the task a user would run to build each thing.

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

- `BuildableComponentSpec` represents a physical thing.
- `BinarySpec.tasks` and properties on `BuildableComponentSpec` represent the tasks that build the thing.
- `BinarySpec.sources` represents the inputs to a binary.
- A `@BinaryTasks` rule defines the tasks that build the binary, as do various methods on `LanguageSourceSet` and `BuildableComponentSpec`.
- Various types, such as `JvmClasses` and `PublicAssets` represent intermediate outputs.

The implementation would be responsible for invoking the rules when assembling the task graph, so that:

- When a physical thing is to be built, the tasks that build its inputs should be configured.
- When a physical thing is used as input, the tasks that build its inputs, if any, should be determined and attached as dependencies of those tasks
that take the physical thing as input.

# Feature: Plugin author uses managed types to define intermediate outputs

This feature allows a plugin author to declare intermediate outputs for custom binaries, using custom types to represent these outputs.

Allow a plugin author to extend any buildable type with a custom managed type. Allow a custom type to declare the inputs for the buildable type in a strongly typed way.
For example, a JVM library binary might declare that it accepts any JVM classpath component as input to build a jar, where the intermediate classes directory is a kind of JVM classpath component.

## Implementation

One approach is to use annotations to declare the roles of various strongly typed properties of a buildable thing, and use this to infer the inputs of a buildable thing.

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

For a binary's input source sets, one option would be to change the behaviour so that a binary receives a copy of its component's source sets. These copies would then be owned by the binary and can be further customized in the context of the binary.

For a test suite's component under test, one option would be to restructure the relationship, so that test suite(s) become a child of the component under test.
