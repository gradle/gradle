
### ~~Plugin creates model element of custom, simple, type without supplying an implementation~~

This story makes the following possible…

    @Managed
    interface Person {
        String getName();
        void setName(String name);
    }

    class RulePlugin {
        @Model
        void createPerson(Person person) {
          person.setName("foo")
        }

        @Mutate
        void addPersonTask(CollectionBuilder<Task> tasks, Person person) {
            tasks.create("echo", t ->
              t.doLast(t2 -> System.out.println(person.getName())); // prints 'foo'
            );
        }
    }

1. No implementation of `Person` is provided
2. A `@Model` method returning `void` indicates the the first arg should be an “empty” instance of the model type and is the thing to be created (all other args are inputs to the rule)
3. Only support for `String` properties is required at this point - it is an error to have a property of any other type
4. Properties conform to the JavaBean convention - it is an error to have a method that doesn't conform to this, or a non read & write property
4. `@Managed` types must be interfaces and cannot extend other interfaces
5. Validation occurs early (when rule is encountered, i.e. before it is executed)

> Note: most of those constraints are just temporary and will be loosened by future stories

#### Test Coverage

- ~~(something like snippet above)~~
- ~~(constraints mentioned above cause errors when violated, error message points to “what” caused the type to be considered)~~
- ~~`void` returning `@Model` method with non `@Managed` type as first arg causes error~~

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

### ~~Plugin creates model element of a collection of managed model elements~~

    @Managed
    interface Person {
      String getName(); void setName(String string)
    }

    package org.gradle.model.collection
    interface ModelSet<T> extends Set<T> {
      void create(Action<? super T> action)
    }

    class Rules {
      @Model
      void people(ModelSet<Person> people) {}

      @Mutate void addPeople(ModelSet<Person> people) {
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

#### Open issues

- Should be able to path to and through the referenced element.
- Implementation should link to the referenced node, rather than a view of the element.
- When a rule receives a view of an object graph, a particular element that is linked into multiple locations in the graph should be represented using the same object instance
  in every location:
    - One link is an 'inherent' property, the rest are references
    - All links are references
    - The links occurs in multiple locations in the view graph
    - Link occurs in a collection
    - The same element appears in different inputs to a rule

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
      ModelSet<Person> getMembers();
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

### User sees useful type name in stack trace for managed model type and while debugging (i.e. not JDK proxy class names)

Use class generation tool that allows specifying the name of a generated class, e.g. cglib or asm.

#### Test coverage

- ~~When a runtime error is thrown from implementation of a managed element setter/getter the stack trace contains reference to the name of the managed type.~~

### “read” methods of ModelSet throw exceptions when set is mutable

It should not be possible to call any of these methods until the set is realised.

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
- ~~Class and its ancestors cannot have protected or private abstract and non-abstract methods~~

#### Open issues

- Abstract class should be able to provide a custom `toString()` implementation, should only be able to use inherent properties of the object.

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
      ModelSet<OperatingSystem> getOperatingSystems()
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

### Support properties of type `File`

    @Managed
    interface Thing {
      File getFile();
      void setFile(File file)
    }

- Similar to `String` etc., getter must be accompanied by setter
- Similar to `String` etc. `setFile()` cannot be called when the object is read only
