This spec outlines some initial stories for what's generally termed “managed model”.
This is a sub stream of the “unified-configuration-model” stream.
   
## Stories
                                            
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

#### Open questions

1. Should we require that types that are designed to be managed model elements be annotated?
2. Do we need to consider non identity based equals/hashCode at this time?

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

### Plugin creates model element of custom type, containing properties of Java boxed primitive-ish types, without supplying an implementation

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

### Plugin creates model element of a collection of managed model elements

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
- All mutative `Set` methods throw `UnsupportedOperationException`
    
#### Test coverage
    
- Attempt to create collection of non managed type
- Attempt to create collection of invalid managed type
    
## Future candidate stories (unordered)


### Plugin creates model element of custom type, containing a collecting of boxed primitive types, without supplying an implementation

i.e. unclear what mutation concerns are in scope 

### User assigns reference type property using indirect identifier

i.e. Something like the current scenario with `Platform.operatingSystem`. There is a set of objects of the referenced type, and they can be assigned as references some convenient way (e.g. by parsing a string)

### User is prevented from mutating managed model object when being used as an input

i.e. Something like the current scenario with `Platform.operatingSystem`. There is a set of objects of the referenced type, and they can be assigned as references some convenient way (e.g. by parsing a string)

### Model type is provided as an abstract class, containing non abstract methods

### Model type is part of an inheritance hierarchy and can be viewed/mutated as any type in the hierarchy

### Plugin creates item of managed type in collection property of managed type
 
### Plugin creates item of managed type in collection property of unmanaged type
 
> Need to find a use case for this to see if it's needed (i.e. do we mix managed/unmanaged) types 