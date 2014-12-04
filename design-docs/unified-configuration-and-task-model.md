This spec covers what is termed the “new configuration model”.

This is a large topic.
Specific sub streams have been broken out into other concurrent specs.

* `managed-model.md`

# Stories

## ~~Plugin declares a top level model to make available~~

Introduce some mechanism where a plugin can statically declare that a model object should be made available.

A mock up:

    public class SomePlugin implements Plugin<Project>
        @RuleSource
        static class Rules {
            @Model("something")
            MyModel createSomething() {
                ...
            }
        }
    }

    apply plugin: SomePlugin

    model {
        something {
            ...
        }
    }

### Test cases

- ~~Build script configuration closure receives the model instance created by the plugin.~~
- ~~Build script configuration closure is executed only when the model is used as input to some rule.~~
- ~~Reasonable error message when two rules create models with same name.~~
- ~~Reasonable error messages when creation rule or configuration closure fail.~~
- ~~Reasonable error messages when plugin does not correctly follow static pattern.~~
- ~~Creation rule returns null.~~
- ~~Rule can declare parameterized type with concrete type vars~~
- ~~Model type cannot be generic~~
- ~~Model type can contain type params~~
- ~~Model element declared with illegal name produces reasonable error message~~

## ~~Plugin defines tasks using model as input~~

Introduce some mechanism where a plugin can static declare a rule to define tasks using its model object as input.

A mock up:

    // Part of Gradle API
    interface CollectionBuilder<T> {
        void add(String name); // Adds element of type T with given name and default implementation for T

        void add(String name, Class<? extends T> type); // Adds elements with given type

        void add(String name, Action<? super T> configAction);
        <S extends T> void add(String name, Class<S> type, Action<? super S> configAction);
    }

    public class SomePlugin {
        ...

        @Rule
        public createTasks(CollectionBuilder<Task> container, MyModel model) {
            // Invoked after MyModel has been configured
        }
    }

    apply plugin: SomePlugin

    model {
        something {
            ...
        }
    }

### Test cases

- ~~Build script configuration closure is executed before rule method is invoked.~~
- ~~Reasonable error message when two rules create tasks with the same name.~~
- ~~Item configuration action cannot create more items~~
- ~~Reasonable error message when rule method fails.~~
- ~~Reasonable error message when rule method declares input of unknown type.~~
- ~~Reasonable error message when rule method declares ambiguous input.~~
- ~~Reasonable error message when rule method declares input by path but incompatible type.~~

### Open issues

#### Deferred

Due to uncertainties about how we will deal with creating non root elements generally, deferring these.

- `CollectionBuilder` is not part of public API.
- `CollectionBuilder` should have an overload that can accept a rule source class or instance, to allow the configuration rule to have its own inputs that aren't required when the task is declared. Should be consistent with pattern used for dependency management rules.
- Error message when no collection builder of requested type should provide more help about what is available.
- Possibly introduce a new type of rule, that adds model elements to a container, rather than 'mutates' the container.
    
## ~~Build author configures task created by configuration rule supplied by plugin~~

1. Build author has prior knowledge of task name (i.e. story does not cover any documentation or tooling to allow discovery of task name)
1. Configuration does not take any external inputs (i.e. all necessary configuration is the application of constants)
1. Task is not required by to be accessed outside model rule (e.g. does not needed to be added as dependency of “legacy” task)
1. Task is not created during “legacy” configuration phase

The majority of what is required for this story is already implemented.
One thing that will be required is improved diagnostics to help the user debug a mistyped task name (see test coverage).

Also, must verify that runtime failures include enough information for the user to find the faulty code.
For this story, it is not necessary for the failure message to fully indicate why the particular rule is being executed.

### Test cases

- ~~User successfully configures task~~
  - ~~Can add dependency on other task using task name~~
  - ~~Can change configuration property of specific task type (e.g. something not defined by `Task`)~~
- ~~User receives useful error message when specified task (i.e using name) is not found~~
  - ~~Error message includes names of X tasks with names closest to given name~~
- ~~User receives useful error message when configuration fails (incl. identification of the rule that failed in the diagnostics)~~

## ~~Model DSL rule uses an implicitly typed model element as input via name~~

This story adds the capability for rules declared in scripts to take inputs.

    // note: DSL doesn't support registration at this time, so elements below have been registered by plugin
    
    model {
        theThing { // registered by plugin
          value = "foo"
        }
        otherThing {
            value = $("theThing").value + "-bar"
            assert value == "foo-bar"
        }
        otherOtherThing {
            value = $("otherThing").value
            assert value == "foo-bar"
        }
        
        // Can address nested registered elements, but not arbitrary properties of registered elements
        tasks.theTask {
          dependsOn $("tasks.otherTask")
        }
    }

### Implementation plan

- Add a compile time transform to “lift” up `$(String)` method invocations in a manner that can be extracted from the closure object
- When registering model closure actions, inspect this information in order to register rules with necessary inputs
- Add notion of 'default' read only type to model registrations (which is what is returned here, given that there is no type information)
- Transform closure implementation in some way to make model element available as result of $() method call (possibly transform in $() implementation, or rewrite statement)

### Test cases

- ~~Compile time failure~~
  - ~~Non string literal given to $() method~~
  - ~~No arguments given to $()~~
  - ~~More than one argument given~~
  - ~~`null` given as string argument~~
  - ~~`""` (empty string) given as argument~~
  - ~~Invalid model path given as argument (see validation in `ModelPath`)~~
- ~~Input binding failure~~
  - ~~Unbound input (i.e. incorrect path) produces error message with line number of input declaration, and suggestions on alternatives (e.g. assume user mistyped name)~~
- ~~Non “transformed” closure given as rule (i.e. `model { def c = {}; someThing(c) }`) produces error~~
- ~~Non “transformed” closure given as model block (i.e. `def c = {}; model(c)`) produces error~~
- ~~Success~~
  - ~~Existing inputs can be used~~
  - ~~Inputs are finalized when used~~
  - ~~Can use the same input more than once (e.g. `def a = $("foo"); def b = $("foo")`)~~
  - ~~`$(String)` can be used anywhere in code body (e.g. `if` body)~~
  - ~~Rule defined in script plugin can access inputs with correct context (i.e. inputs are linked to correct project scope)~~
- ~~Nested `model {}` usage~~
  - ~~Can use model rules in nested context that don't require inputs~~
  - ~~Attempted use of inputs in model rule in nested context yields “unsupported” error message~~
- ~~Individual block has some scope/access as regular closure declared in top level of build script~~

## ~~Configuration performed to “bridged” model element made in afterEvaluate() is visible to creation rule~~

This story adds coverage to ensure that model rules are fired **AFTER** afterEvaluate().

### Test Coverage

1. ~~Project extension configured during afterEvaluate() registered as model element has configuration made during afterEvaluate()~~
1. ~~Task created in afterEvaluate() should be visible for a rule taking TaskContainer as an _input_~~

## ~~Build user applies rule source class in similar manner to applying a plugin~~

This story makes it more convenient to write a plugin that is exclusively based on model rules, while keeping the implementation details of such a plugin transparent to the user.

Support for applying a `@RuleSource` annotated classes that don't implement `Plugin` will be added to `PluginAware.apply(Closure)` and `PluginAware.apply(Map)`:

- rule source plugins can be applied using an id
- rule source plugins (and `Plugin` implementing classes) can be applied by type using a new `type` method/key, i.e. `apply type: MyRuleSource`

The `@RuleSource` annotation can still be used on a nested class in a `Plugin` implementation.

A new API for querying applied plugins that supports both `Plugin` implementing classes and rule source classes will be introduced:

    interface AppliedPlugins {
        @Nullable
        Class<?> findPlugin(String id);
        boolean contains(String id);
        void withPlugin(String id, Action<? super Class<?>> action);
    }
    
    interface PluginAware {
        AppliedPlugins getAppliedPlugins();
    }

### Test Coverage

- ~~Rule source plugin can be applied to Project via `apply()` using an id or type~~
- ~~`Plugin` implementing classes can be applied to Project via `apply(type: ... )`~~
- ~~Rule source plugin can be applied to Project via `plugins {}`~~
- ~~Rule source plugin can be applied in ProjectBuilder based unit test~~
- ~~Rule source plugin cannot be applied to `PluginAware` that is not model rule compatible (e.g. Gradle)~~
- ~~Reasonable error message is provided when the `RulePlugin` implementation violates the rules for rule sources~~
- ~~`Plugin` impl can include nested rule source class~~
- ~~A useful error message is presented to the user if they try to apply a rule source plugin as a regular plugin, i. e. `apply plugin: RuleSourcePlugin` or `apply { plugin RuleSourcePlugin }`~~
- ~~Can use `AppliedPlugins` and ids to check if both `Plugin` implementing classes and rule source classes are applied to a project~~
- ~~A useful error message is presented when using `PluginContainer.withId()` or `PluginContainer.withType()` to check if a rule source plugin is applied~~   

### Open issues

- Plugin management and query methods are not name-spaced on `PluginAware` - should implement something closer to the above spec instead.
- Statically typed 'apply by id' and 'apply by type' methods have been deprecated without statically typed replacement.
- Some method of `PluginContainer` have been deprecated - should deprecate the whole thing instead as the semantics of `PluginContainer` aren't right.
- Responsibilities are smeared between `PluginManager` and `DefaultPluginContainer` - `PluginManager` should take care of applying all plugins regardless of type and
  notify the plugin container when a `Plugin` impl is applied.
- `PluginManager` applies plugins with deprecation logging disabled - this means that all deprecated usages in `apply()` will be ignored and not reported. Need to fix
  and add an integration test for this case.

#### Other issues

- `ModelRuleInspector` does not do any caching. Should share caching with `ModelRuleSourceDetector`.
    - `ModelRuleSourceDetector` should be global scope and use weak references to classes.

# Open Questions

- How to order mutations that may derive properties from the subject

# Backlog

Potential stories and ideas.
Unordered and not all appropriately story sized.

## Diagnostics

- When a task cannot be located, search for methods that accept `CollectionBuilder<Task>` as subject but are not annotated with `@Mutate`.
- Error message when applying a plugin with a task definition rule during task execution should include more context about the failed rule.
  This essentially means more context in the 'thing has been closed' error message.
- Some kind of explorable/browsable representation of the model elements for a build
- Profile report contains information about execution time of model rules
- Rule source types are instantiated early to fail fast (i.e. constructor may throw exception)
- Cyclic dependencies between configuration rules are reported
- Build user is alerted when a required model rule from a plugin does not bind (i.e. plugin was expecting user to create some element)
- Build user is alerted when a build script model rule does not bind (i.e. some kind of configuration error)
- Bind by path failures understand model space structure and indicate the failed path “link” (e.g. failure to bind `tasks.foo` informs that `tasks` exists and what it is)
- Collapse rule descriptors to “plugin” granularity where appropriate in error message (i.e. plugin users typically don't need information about plugin internals, but need to know which plugin failed).

## Cleanup

We've introduced different mechanisms for deferring configuration.
These should be rationalised and ideally replaced with model rules.

- DeferredConfigurable
- TaskContainer.addPlaceholderAction
- SonarRunnerExtension.sonarProperties
- Move native plugin suite to new mechanism
- Move publishing plugin suite to new mechanism

## Tasks

- Remove `TaskContainer` from model space
- Prevent illegal mutation of `Task` in model space

## Misc

- Task command line arguments are applied _after_ all configuration rules
- Select internal services are made available to configuration rules (i.e. remove `ServiceRegistry` from model space)
- Make `buildDir` available in model space
- Remove `ExtensionContainer` from model space
- Semantics of model element removal are not well defined

## Testing

- Plugin author verifies that plugin makes model element available
- Plugin author verifies that plugin correctly configures model element, based on supplied “other” model elements
- Plugin author verifies that all plugin configuration rules bind

## Performance

- Defer creating task instances until absolutely necessary
- Cache/reuse model elements, avoiding need to run configuration on every build
- Extract rules from plugins once per build (and possibly cache) instead of repeating for each project
- Extract rules from scripts once per build (and possibly cache) instead of each time it is applied
- Instance of rule source type is shared across entire build

## DSL

- Specify type of rule input
- Specify type of rule subject
- Plugin author specifies model type to use in DSL when type is unspecified
- Mechanism for declaring new top level (adhoc?) model elements in build script

## Productization

- Document general configuration rule concepts (incl. rationale for change)
- Document rule source plugin concepts

## Migration/Bridging

- Provide supported pattern for migrating plugins