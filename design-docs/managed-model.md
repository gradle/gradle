This spec outlines some initial stories for what's generally termed “managed model”.
This is a sub stream of the “unified-configuration-model” stream.
   
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
   
## Stories
                                            
### ~~Plugin creates model element of custom, composite, type without supplying an implementation~~

    @Managed
    interface Platform {
        String getDisplayName();
        void setDisplayName(String name);
        OperatingSystem getOperatingSystem();
    }
    
    @Managed 
    interface OperatingSystem {
        String getName();
        void setName(String name);
    }
    
    class RulePlugin {
        @Model
        void createPlatform(Platform platform) {
          platform.setDisplayName("Microsoft Windows")
          platform.getOperatingSystem().setName("windows")
        }
        
        @Mutate
        void addPersonTask(CollectionBuilder<Task> tasks, Platform platform) {
            tasks.create("echo", t -> 
              t.doLast(t2 -> System.out.println(platform.getOperatingSystem().getName())); // prints 'windows'
            );
        }
    }

1. It is an error to have a read only property for a type other than a `@Managed` interface
1. The nested model element has the same constraints as the parent

#### Test Coverage

- ~~Nested element is not `@Managed` causes error~~
- ~~Nested element violates constraints (error message indicates that it's being considered due to being nested, and indicates why enclosing class was being considered)~~

### ~~Plugin creates model element of custom, reference having, type without supplying an implementation~~

    @Managed
    interface Platform {
        String getDisplayName();
        void setDisplayName(String name);
        
        OperatingSystem getOperatingSystem();
        void setOperatingSystem(OperatingSystem operatingSystem); // setter for @Managed type indicates it's a reference
    }
    
    @Managed 
    interface OperatingSystem {
        String getName();
        void setName(String name);
    }
    
    class RulePlugin {
        @Model
        void createOs(OperatingSystem os) {
          os.setName("windows");
        }
        
        @Model
        void createPlatform(Platform platform, OperatingSystem os) {
          platform.setDisplayName("Microsoft Windows")
          platform.setOperatingSystem(os)
        }
        
        @Mutate
        void addPersonTask(CollectionBuilder<Task> tasks, Platform platform) {
            tasks.create("echo", t -> 
              t.doLast(t2 -> System.out.println(platform.getOperatingSystem().getName())); // prints 'windows'
            );
        }
    }

#### Test Coverage

- ~~Calling `setOperatingSystem()` with “non managed” impl of `OperatingSystem` is a runtime error (i.e. only managed objects can be used)~~


### ~~Plugin creates model element of custom, composite, type without supplying an implementation with a cyclical type reference~~

    The story makes the following possible:
    
    @Managed
    interface Parent {
        String getName();
        void setName(String name);
        
        Child getChild();
    }
    
    @Managed 
    interface Child {
        Parent getParent();
        void setParent(Parent parent);
    }
    
    class RulePlugin {
        @Model
        void createParent(Parent parent) {
            parent.setName("parent");
            parent.getChild().setParent(parent)
        }
        
        @Mutate
        void addEchoTask(CollectionBuilder<Task> tasks, Parent parent) {
            tasks.create("echo", t -> 
              t.doLast(t2 -> System.out.println(parent.getChild().getParent().getName())); // prints "parent"
            );
        }
    }

#### Test Coverage

- ~(something like snippet above)~
- ~should also support situations where more than two types are taking part in forming a cycle~

### ~~Plugin creates model element of custom type, containing properties of Java boxed primitive-ish types, without supplying an implementation~~

Adds support for:

1. `Boolean`
1. `Integer`
1. `Long`
1. `Double`
1. `BigInteger`
1. `BigDecimal`

Use of non primitive types is not allowed.
Attempt to declare a property of a primitive type should yield an error message indicating that a boxed type should be used instead.
 
1. boolean -> Boolean	
1. char -> Integer
1. float -> Double	
1. int -> Integer
1. long	-> Long
1. short -> Integer	
1. double -> Double

Use of other boxed types is not allowed.
Attempt to declare a property of a such a type should yield an error message indicating that an alternative type should be used (see mappings above).

Use of `byte` and `Byte` is unsupported. 

#### Test coverage

- ~~Can get/set properties of all supported types~~
- ~~Can narrow/widen values as per normal (e.g. set a `Long` property with a literal `int`)~~

#### Open questions

- Is this the right set of things to support? Should we just directly support all of Java's primitive types? 

### ~~Plugin creates model element of a collection of managed model elements~~

    @Managed
    interface Person {
      String getName(); void setName(String string)
    }
    
    package org.gradle.model.collection
    interface ManagedSet<T> extends Set<T> {
      void create(Action<? super T> action)
    }
    
    class Rules {
      @Model
      void people(ManagedSet<Person> people) {}
      
      @Mutate void addPeople(ManagedSet<Person> people) {
        people.create(p -> p.setName("p1"))
        people.create(p -> p.setName("p2"))
      }
    }
    
    model {
      people {
        create { it.name = "p3" }
      }
      
      tasks {
        create("printPeople") {
          it.doLast {
            assert $("people")*.name.sort() == ["p1", "p2", "p3"]
          }
        }
      }
    }
    
Notes:

- No lifecycle management at this stage (i.e. we don't prevent reading the collection when mutating and vice versa)
- All mutative methods of the `java.lang.Set` interface throw `UnsupportedOperationException`
    
#### Test coverage
    
- ~~Attempt to create collection of non managed type~~
- ~~Attempt to create collection of invalid managed type~~
    
### ~~Managed model interface extends other interfaces~~

    interface Named {
        String getName(); void setName(String name);         
    }
    
    @Managed
    interface NamedThing extends Named {
        String getValue(); void setValue(String value);
    }
    
#### Notes

- Super types do not need to be annotated with `@Managed` - but are subject to the same constraints as `@Managed` types
- Specialisation of a generic parent is not supported through this story (i.e. can't do `interface BookList extends List<Book>`)

#### Test Coverage

- ~~Can extend more than one interface~~
- ~~Error message produced when super type is not a “manageable” type indicates the original (sub) type (and the rule that caused it to be extracted)~~
- ~~Can get/set properties of super type(s)~~
- ~~Can depend on super type as input and subject~~
- ~~Two different types can extend same parent~~
- ~~Property conflicts between super types are detected (different types for the same name)~~ 

### ~~Managed model type has a property of collection of managed types~~

    @Managed
    interface Person {
      String getName(); void setName(String string)
    }
    
    @Managed
    interface Group {
      String getName(); void setName(String string)
      ManagedSet<Person> getMembers();
    }
    
    class Rules {
      @Model
      void group(Group group) {
        group.name = "Women in computing"
        group.members.create(p -> p.setName("Ada Lovelace"))
        group.members.create(p -> p.setName("Grace Hooper"))
      }
    }
    
    model {
      tasks {
        create("printGroup") {
          it.doLast {
            def members = $("group").members*.name.sort().join(", ")
            def name = $("group").name
            println "$name: $members"
          }
        }
      }
    }
    
#### Test Coverage

- ~~Something like the snippet above~~
- ~~Can set/get a reference to a collection of managed types~~
    
### ~~Managed model type has enum property~~

#### Notes

- Support for enums of any type
- Enum values are opaque to the model space (i.e. we do not treat enum values as structured objects, e.g. cannot depend on a property of an enum value)
- Enum values are not strictly immutable/threadsafe in Java but almost always are, as such we will consider them to be at this stage
- It doesn't have any impact at this stage, but only the enum value is strictly part of the model (all properties of an enum value are supplied by the runtime)

### ~~Managed model element has unmanaged property~~

    interface MyModel {        
        @org.gradle.model.Unmanaged
        SomeWeirdThing getThing()        
        void setThing(SomeWeirdThing thing)
    }
    
Properties of an unmanaged type must be explicitly annotated with `@Unmanaged`.
The rationale for this is that the use of unmanaged properties will have a significant impact on tooling and functionality in general, as such it should be very clear to model consumers which properties are unmanaged.

Unmanaged properties must be accompanied by a setter.

#### Test Coverage

- ~~Can attach an an unmanaged property~~
- ~~Error when unmanaged property does not have annotation~~
- ~~Subtype may declare setter for unmanaged type~~
- ~~Unmanaged property of managed type can be targeted for mutation~~
- ~~Unmanaged property of managed type can be used as input~~
    
### ~~Model rule accepts property of managed object as input~~
      
    @Managed
    interface Person {
      String getName(); void setName(String string)
    }
    
    class Rules {
      @Model
      void p1(Person person) {
        person.setName("foo");
      }
      
      @Mutate void addPeople(CollectionBuilder<Task> tasks, String personName) {
        tasks.create("injectedByType", t -> t.doLast(() -> assert personName.equals("foo"))
      }
    }
    
    model {
      tasks {
        create("injectedByName") {
          it.doLast {
            assert $("p1.name") == "foo"
          }
        }
      }
    }
    
### Test Coverage

- ~~Can inject leaf type property (e.g. String, Number)~~
- ~~Can inject node type property (i.e. another managed type with properties)~~
- ~~Can inject property of property of managed type (i.e. given type `A` has property of managed type `B`, can inject properties of `B`)~~
- ~~Can inject by “path”~~

### ~~Model rule mutates property of managed object~~
      
    @Managed
    interface Person {
      String getName(); void setName(String string)
      Person getMother();
      Person getFather();
    }
    
    class Rules {
      @Model
      void p1(Person person) {
        person.setName("foo");
      }
      
      @Mutate void setFather(@Path("p1.father") Person father) {
        father.setName("father")
      }
    }
    
    model {
      p1.mother { name = "mother" }
      tasks {
        create("test") {
          it.doLast {
            def p1 = $("p1")
            assert p1.mother.name == "mother"
            assert p1.father.name == "father"
          }
        }
      }
    }
        
#### Test Coverage

(above)

### User receives runtime error trying to mutate managed object outside of mutation

    @Managed
    interface Person {
      Person getPartner();
      String getName(); 
      void setName(String string)
    }
    
    class Holder {
        static Person person
    }
    
    class Rules {
      @Model
      void p1(Person person) {
        person.setName("foo");
        Holder.person = person
      }
      
      @Mutate void setFather(CollectionBuilder<Task> tasks, Person person) {
        Holder.person.setName("foo") // ← runtime error
        Holder.person.partner.setName("foo") // ← runtime error  
        person.setName("foo") // ← runtime error
        person.partner.setName("foo") // ← runtime error
      }
    }

Runtime error received when trying to mutate an immutable object should include a reference to the rule that created the immutable object (i.e. not the model element, but that actual object).

### User receives runtime error trying to mutate managed set when used as input and outside of mutation method

    @Managed
    interface Platform {
      ManagedSet<OperatingSystem> getOperatingSystems()
    }
    
    @Managed
    interface OperatingSystem {
        String getName()
        void setName(String)
    }
        
    class Holder {
      static Platform platform
    }
    
    class Rules {
      @Model
      void p(Platform platform) {
        Holder.platform = platform
        platform.operatingSystems.create { name = "foo" }
      }
      
      @Mutate void setFather(CollectionBuilder<Task> tasks, Platform platform) {
        Holder.platform.create(…) // ← runtime error
        platform.create(…) // ← runtime error
      }
    }

Runtime error received when trying to mutate an immutable object should include a reference to the rule that created the immutable object (i.e. not the model element, but that actual object).

### “read” methods of ManagedSet throw exceptions when set is mutable

It should not be possible to call any of these methods until the set is realised.

### User sees useful type name in stack trace for managed model type and while debugging (i.e. not JDK proxy class names)

Use class generation tool that allows specifying the name of a generated class, e.g. cglib or asm.

#### Test coverage

- ~~When a runtime error is thrown from implementation of a managed element setter/getter the stack trace contains reference to the name of the managed type.~~

### Managed type is implemented as abstract class

- Types must obey all the same rules as interface based impls
- No constructors are allowed (as best we can detect)
- Can not declare any instance scoped fields
- Subclass should be generated as soon as type is encountered to ensure it can be done
- Should use same class generation techniques as existing decoration (but no necessarily share impl)
- Instance should be created as soon as type is encountered to ensure it can be done

#### Test Coverage

- ~~Class based managed type can be used everywhere interface based type can~~
- ~~Subclass impl is generated once for each type and reused~~
- ~~Subclass cache does not prevent class from being garbage collected~~
- ~~Class can implement interfaces (with methods conforming to managed type rules)~~
- ~~Class can extend other classes (all classes up to `Object` must conform to the same rules)~~
- ~~Constructor can not call any setter methods (at least a runtime error)~~
- ~~Class that cannot be instantiated (e.g. default constructor throws)~~

### Managed type implemented as abstract class can have generative getters

    @Managed
    abstract class Person {
        abstract String getFirstName()
        abstract void setFirstName(String firstName)
        abstract String getLastName()
        abstract void setLastName(String lastName)        

        String getName() {
            return getFirstName() + " " + getLastName()
        }
    }
    
- Only “getter” methods are allowed to be non `abstract` 

#### Test Coverage

- ~~Runtime error if provided getter (i.e. non abstract one) calls a setter method~~

### Java 8 interface default methods can be used to implement generative getters

    @Managed
    interface Person {
        String getFirstName();
        void setFirstName(String firstName);
        String getLastName();
        void setLastName(String lastName);
    
        default String getName() {
            return getFirstName() + " " + getLastName();
        }
    }
    
    @RuleSource
    class RulePlugin {
        @Model
        void createPerson(Person person) {
            person.setFirstName("Alan");
            person.setLastName("Turing");
        }
    
        @Mutate
        void addPersonTask(CollectionBuilder<Task> tasks, Person person) {
            tasks.create("echo", task -> {
                task.doLast(unused -> {
                    System.out.println(String.format("name: %s", person.getName()));
                });
            });
        }
    }

- Same semantics as non abstract methods on class based types

#### Test Coverage

- ~~like snippet above~~


### Support for polymorphic managed sets

```
interface ManagedSet<T> implements Set<T> {
  void create(Class<? extends T> type, Action<? super T> initializer);
  <O> Set<O> ofType(Class<O> type);
}
```

- `<T>` does not need to be managed type (but can be)
- `type` given to `create()` must be a valid managed type
- All mutative methods of `Set` throw UnsupportedOperationException (like `ManagedSet`).
- `create` throws exception when set has been realised (i.e. using as an input)
- “read” methods (including `ofType`) throws exception when called on mutable instance
- No constraints on `O` type parameter given to `ofType` method
- set returned by `ofType` is immutable (exception thrown by mutative methods should include information about the model element of which it was derived)

The initial target for this functionality will be to replace the `PlatformContainer` model element, but more functionality will be needed before this is possible.

### Replace PlatformContainer with PolymorphicManagedSet<Platform>

1. Make NativePlatform managed
    - Remove `architecture()` & `operatingSystem()` (make inherent properties)
    - Change Architecture and OperatingSystem to be managed (push methods out so somewhere else, remove internal subclasses)
    - Provide string to Architecture/OS instance as static methods for time being (proper pattern comes later)
2. Make ScalaPlatform managed
3. Remove 'platforms' extension
4. Introduce managed set for 'platforms' model element
5. Change populating in NativePlatforms to use new container
6. Push `chooseFromTargets` implementation out to static method
7. Remove PlatformContainerInternal

## Feature: Tasks defined using `CollectionBuilder` are not eagerly created and configured

### Plugin uses `CollectionBuilder` API to apply rules to container elements

Add methods to `CollectionBuilder` to allow mutation rules to be defined for all elements or a particular container element.

- ~~Add `all(Action)` to `CollectionBuilder`~~
- ~~Add `afterEach(Action)` to `CollectionBuilder`~~
- ~~Add `named(String, Action)` to `CollectionBuilder`~~
- ~~Add `withType(Class, Action)` to `CollectionBuilder`~~
- ~~Add `withType(Class)` to `CollectionBuilder` to filter.~~
- Verify usable from Groovy using closures.
- Verify usable from Java 8 using lambdas.

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
- Apply defaults to managed object before initializer method is invoked.

#### Issues

- Fail when target cannot accept defaults, eg is created using unmanaged object `@Model` method.
- Handle case where defaults are applied to tasks defined using legacy DSL, e.g. fail or perhaps define rules for items in task container early.

### Plugin uses method rule to validate model element

Add a way to validate a model element prior to it being used as an input by 'user' code.

- ~Add `@Validate` annotation. Apply these after `@Finalize` rules.~
- ~Rename 'mutate' methods and types.~
- Add `CollectionBuilder.validateEach(Action)`
- Don't include wrapper exception when rule fails, or add some validation failure collector.

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

### Support for managed container of tasks

- Rename `CollectionBuilder` to `ManagedMap`.
- Currently it is possible to get an element via `CollectionBuilder`, to help with migration. Lock down access to `get()` and other query methods.
    - Same for `size()`.
- Add a read-only view of `ManagedMap`.
- Change `ManagedMap` to extend `Map`.
- Mix in the DSL conveniences into the managed collections and managed objects, don't reuse the existing decorator.
- Allow a `ManagedMap` to be added to model space by a `@Model` rule.
- Synchronisation back to `TaskContainer`, so that `project.tasks.all { }` and `project.tasks { $name { } }` works.
- Need efficient implementation that does not scan all linked elements to check type.
- Lock down read-only view of `ManagedMap`, provide `Map` as view.
- Implement `containsValue()`.
- Separate out type registry.

### Expose a `ManagedMap` view for all model elements of type `PolymorphicDomainObjectContainer`.

- Currently blocked on `NativeToolChainRegistryInternal.addDefaultToolChains()`
- `TestSuiteContainer` should extend `PolymorphicDomainObjectContainer`.
- `ProjectSourceSet` should extend `PolymorphicDomainObjectContainer`.

### Support for managed container of source sets

As above, for `ManagedSet`.

- Change `ManagedSet` implementation to register a creation rule rather than eagerly instantiating and configuring.
    - Allow model nodes to be identified by something other than a path.
- Add `all(Action<? super T)` method to `ManagedSet`.
- Add DSL support for `all { }` rule.

## Open Questions

- Set by reference vs. copy (i.e. what are the implications for pathing, and ordering mutation)
- Should getters of subjects be allowed to be called during mutation rules? (i.e. we can't guarantee they won't change)
- Should we allow setters being called more than once?

## Backlog

### Open questions

1. Should we require that types that are designed to be managed model elements be annotated?
2. Do we need to consider non identity based equals/hashCode at this time?

### Implementations

- Mix DSL and Groovy methods into managed type implementations.
- Support parameterized non-collection types as managed types:

### Collections

- Collections of value elements
- Map type collections
- Ordered collections
- Semi ordered collections (e.g. command line, where some elements have an order relationship)
- Maps of value elements
- Maps of model elements
- Collections of implicitly keyed elements, acting as a map
- Equality concerns when using sets

### Extensibility & views

- Convenience and/or enforcement of “internal to plugin” properties of model elements
- Extending model elements with new properties
- Specializing model elements to apply new views

### Misc

- Allow some control over generated display name property
- Semantics of equals/hashCode
- User receives runtime error trying to mutate managed set elements when used as input and outside of mutation method
    - Also when mutation is transitive (e.g. mutating a property of a managed property of a managed set element)
- Support getting "address" (creation/canonical path) of a managed object
- Throw a meaningful exception instead of failing with `OutOfMemoryError` at runtime when a managed type instantiation cycle is encountered (a composite type that contains an instance of itself keeps on creating new instances indefinitely)
- Attempt to call setter method in (abstract class) managed model from non-abstract getter receives error when extracting type metadata