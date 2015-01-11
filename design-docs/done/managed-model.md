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
