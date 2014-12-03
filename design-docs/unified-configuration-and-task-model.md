# Introduction

In almost every build, there are dependencies between the build logic that defines the Gradle model and the build logic
that uses that model to do something useful. Here's an example:

* The Java plugin adds domain objects that describe the source of the project, how to compile it, and so on. These domain
  objects are configured according to the Gradle convention.
* The root build script applies the Java plugin and applies some cross-cutting configuration to the domain objects
  to define the convention that the build uses.
* The project build script applies some project-specific configuration to define how this project varies from the
  convention.
* The Java plugin adds some tasks whose configuration is determined by the configuration of the domain objects, according
  to the Gradle convention.
* The root build script applies some configuration of these tasks.
* The project build script applies some configuration of these tasks.

Currently, the build logic execution is eager, and the domain objects are lazy so that they can deal with changes:

1. The root build script applies the Java plugin
2. The Java plugin adds the domain objects and tasks, and wires up the domain objects and tasks so that their configuration
   is calculated lazily, to deal with changes applied in the later steps.
3. The root build script applies cross-cutting configuration of the domain objects and tasks.
4. The project build script applies project specific configuration of the domain objects and tasks.

There are a number of problems with this approach:

1. It is difficult to implement lazy data structures. This has been observed in practise over the last few years of
   writing plugins both within the Gradle distribution and in the community.
2. It is not possible for a plugin to provide different task graphs based on the configuration of domain objects, as the
   tasks must be created before any organisation, build or project specific configuration has been applied.
3. Eager execution means that all build logic must be executed for each build invocation, and it is not possible to skip
   logic that is not required. This in turn means that all domain objects and tasks must be created for each build
   invocation, and it is not possible to short-cicuit the creation of objects that are not required.
4. Lazy data structures cannot be shared between threads for parallel execution. Lazy execution allows build logic to
   be synchronised based on its inputs and outputs.
5. Lazy data structures must retain references to their inputs, which results in a large connected graph of objects
   that cannot be garbage collected. When data is eager, the input objects can be released as soon as the build logic
   has completed (or short-circuited).
6. Validation must be deferred until task execution.

The aim of this specification is to define a mechanism to flip around the flow so that build logic is applied lazily and
domain objects are eager. The new publication DSL will be used to validate this approach. If successful, this will then
be rolled out to other incubating plugins, and later, to existing plugins in a backwards compatible way.

# Milestone 1 - Plugin uses rules to define model and tasks

## Story: Plugin declares a top level model to make available

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

## Story: Plugin defines tasks using model as input (DONE)

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

Due to uncertanties about how we will deal with creating non root elements generally, deferring these.

- `CollectionBuilder` is not part of public API.
- `CollectionBuilder` should have an overload that can accept a rule source class or instance, to allow the configuration rule to have its own inputs that aren't required when the task is declared. Should be consistent with pattern used for dependency management rules.
- Error message when no collection builder of requested type should provide more help about what is available.
- Possibly introduce a new type of rule, that adds model elements to a container, rather than 'mutates' the container.
    
## Story: Build author configures task created by configuration rule supplied by plugin (DONE)

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

## Story: Model DSL rule uses an implicitly typed model element as input via name

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

#### Existing use of 'model block'

Users are already using `model {}` to configure the native and publishing plugins. 
At the moment, this is implemented as a method on project and can therefore be called from anywhere where there is a project instance (e.g. `subprojects {}`).
As we are introducing a compile time transform for the model block this is now a problem.
It is not feasible to transform all uses of `model {}` throughout the statement tree as we cannot reliably infer that the invocation doesn't correspond to a different method.

Therefore, supporting for using `model {}` in nested contexts will be supported in the manner it is now.
That is, usage in a nested context only allows input-less mutation rules and should not be subject to new restrictions.
If the user tries to use inputs, in a nested context, they should be told (via the error message) that this is not supported.

### Transform notes

- Only support string literal arguments to `$()` (anything else is a compile time error)

Transforming closures is difficult due to the implementation of the Groovy compiler.
There's no transform time hook for transforming closure _classes_.
It is easy to get a hold of the closure expression though, which is later converted to a Class.
To transform the closure class, you need to inject a custom verifier (see example [here](https://github.com/ratpack/ratpack/blob/master/ratpack-groovy/src/main/java/ratpack/groovy/script/internal/ScriptEngine.java#L90)).

To make the input information available it should be attached to the class.
This can be done by attaching an annotation at transform time.
Some scheme will have to be devised to communicate this information from the ClosureExpression stage to the verifier stage where the class object is available.
One potential option will be to insert a fake statement as the first statement of the closure body during the transform stage, which is extracted at the class generation stage.

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

## Story: Configuration performed to “bridged” model element made in afterEvaluate() is visible to creation rule

This story adds coverage to ensure that model rules are fired **AFTER** afterEvaluate().

### Test Coverage

1. ~~Project extension configured during afterEvaluate() registered as model element has configuration made during afterEvaluate()~~
1. ~~Task created in afterEvaluate() should be visible for a rule taking TaskContainer as an _input_~~
 
## Story: Instance of rule source type is shared across entire build

When inspecting rule source classes, we should assert _early_ that we can instantiate the type (i.e. during inspection, not rule execution).
Moreover, we should only instatiate the type once for the whole build and reuse the instance.

## Story: Cyclic dependencies between configuration rules are reported

Cycles between rules are fatal.
We should detect them and visualise the cycle so the build author can do something about the cycle.

The visualisation should be similar to the visualisation given when a task dependency relationship is cyclic, as far as it makes sense.

Things to consider:

1. When should cycles be detected? (e.g. as rules are added, on demand)
2. How should the exact source of cycles be reported?
3. What advice can we realistically give to help break the cycle?

## Story: Internal services are made available to configuration rules

Configuration rules should be able to access selected internal services.
We need this for our own infrastructure type plugins.

Currently, as a stop gap measure we make the project service registry injectable into rules.
Instead of doing this, we should allow the project service registry to nominate services it wishes to make available to the service registry.
It only makes sense to expose immutable services, and we can do this on an as needed basis.

As the services will be internal, they should not be represented as candidates in binding failure messages or other diagnostics.
To support this, some visibility concept will need to be added to the registry.
It also doesn't make sense for such services to have an address in the model space.
That is, they just need to be injectable by type.

## Story: Project build dir is available to rules to mutate and use

Currently, the `project.buildDir` is made available in the model space as a `File` object.
This is not by-type binding friendly, and generally weak.
A specific model element should be added for this.

As the build dir is “bridged” we need to consider the value being changed after it has been considered realised in the model space.
We can't prevent this from happening as it would be a breaking change, but we should emit a deprecation warning when this happens.

## Story: Project extension is bridged to the model space with state management

This story provides infrastructure and a pattern for making project extensions available to the model space in a managed way.

As extensions are generally highly mutable, and aren't managed types, they cannot be bridged as is.
They effectively need to be “copied” into a managed type. 
Moreover, some kind of support needs to be provided to warn the user if they modify the extension after it has been copied into the model space.

### Open Questions

- To what extent can we catch modifications to arbitrary extension types in order to issue mutation warnings?

## Story: `Task` instances are instrumented in model space to prevent unmanaged mutation

We can't prevent tasks from appearing in the model space.
We need to do something to minimise the damage their inherent mutability can cause by preventing mutations without changing their API.

## Story: Remove `TaskContainer` from model space

This type should not be available in model space due.

## Story: Bind by path failures understand model space structure and indicate the failed path “link”

Given an attempt to bind to `tasks.foo` where it does not exist, the error message should indicate that `tasks` does exist, but `foo` does not in some way.
It should also only suggest alternatives based on the path components that did sucessfully bind.
That is, it should not suggest `taks.foo` but should suggest `tasks.boo`. 

## Story: Make the Model DSL public

- New chapter in userguide
  - Give context/background for “new approach”
  - Give conceptual foundations of model rules
  - Describe constraints (intended) and current limitations
  - Document that the plugin analog is not yet public
  - Samples
  - Review error messages, link back to user guide where appropriate
- Suitable javadoc/DSL ref for classes in `org.gradle.model.dsl`
- Forum post giving more contextual introduction
 
# Milestone 2 - Build author uses public rule DSL to configure model and tasks

## Story: Build user applies rule source class in similar manner to applying a plugin

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


## Story: Model DSL rule uses a typed model element as input via name

    model {
      thing {
        value = $("theThing", SomeType).value // convenience for non parameterised types
      }  
      otherType {
        List<String> var = $("theThing")  // view as List<String>
      }
    }
    
- Only string literals are valid
- Only class literals are valid
- When inferring type from LHS of assignment, `$(String)` is the only RHS expression (i.e. anything else is not subject to inference and is untyped)

### Test Coverage

- Compile time failure
  - Non string literal given to $(String, Class) method
  - Non class literal given to $(String, Class) method
  - `null` given as either argument
  - `""` (empty string) given as argument
  - Invalid model path given as argument (see validation in `ModelPath`)
- Input binding failure
  - Unbound input (i.e. incorrect path or type) produces error message with line number of input declaration, and suggestions on alternatives (e.g. assume user mistyped name)
- Type is only inferred when RHS is JUST a `$()` call (i.e. is the default type if expression is more complex)
- Success
  - Existing inputs can be used
  - Inputs are finalized when used
  - Can use the same input more than once (e.g. `List<String> a = $("foo"); def b = $("foo", List); assert a == b`)
  - `$(String, Class)` can be used anywhere in code body (e.g. `if` body)

## Story: Model DSL rule uses an anonymous typed model element as input

    model {
      thing {
        List<String> strings = $()
        value = strings*.toUpperCase()
      }  
      otherThing {
        value = $(SomeService).value
      }
    }

- Compile time failure
  - `$()` (no args) can ONLY be used as the sole RHS expression of an assignment
    - Non class literal given to $(Class) method
- Input binding failure
  - Unbound input produces error message with line number of input declaration (no type match, more than one type match)
- Success
  - Existing inputs can be used
  - Inputs are finalized when used
  - Can use more than one anonymous input (e.g. `List<String> a = $(); List<Integer> b = $(); assert a == b`)
  - `$(Class)` can be used anywhere in code body (e.g. `if` body)

# Story: User configures model element as specific type 

    model {
      tasks { CollectionBuilder<Task> tasks ->
        tasks.create("foo") {
          it.dependsOn "bar"
        }
      }
    }

Note: Closure parameter generic types are not available via reflection. They will have to be hoisted up with a transform

## Test coverage

- Compile time failure
  - Cannot declare more than one param to closure
- binding failure
  - Type mismatch produces error
  - Attempt to use readable type that is not also writable produces error message explaining
- Success
  - Subject is in writable state

## Story: Internal Gradle plugin defines lazily created task that is visible during configuration phase

This story aims to replace the `TaskContainerInternal.placeholderActions()` mechanism with model rules which is used for `help`, `tasks`, `wrapper` etc.
The capability to defer task creation generally is covered by previous stories.
This story particularly deals with the backwards compatibility requirements of moving the declaration of these tasks to the model rule infrastructure.

## Story: Build author creates task with configuration based on plugin model element

This story makes it viable for a build author to create a task based on managed model.
This requires a DSL for creating model elements.

## Story: Build user receives useful error message when a plugin they are using has a rule that does not fully bind

This story is about improving the user experience when their is an “infrastructure” failure in that a plugin they are using declares a model rule that does not bind.

The focus is on reporting this in a way that a user of Gradle (opposed to plugin/build developer) can make enough sense of what happen to either resolve the problem, or at least report the problem meaningfully.

## Story: Build user/author receives useful error message when a build script they are using has a rule that does not fully bind

This story is about improving the user experience when the build scripts of the current build declare a model rule that does not bind.

The focus is on reporting this in a way that a user of Gradle (opposed to plugin/build developer) can make enough sense of what happen to either resolve the problem, or at least report the problem meaningfully.

This story is different to the similar story in this spec that focuses on plugins, except that this kind of failure is typically more able to be resolved by the build user.
That is, this error may have been caused by the build user due to bad configuration (e.g. mistyped model path)

## Story: Plugin author receives useful error message when a plugin they are debugging has a rule that does not fully bind

This story is about helping plugin developers understand why a rule defined by their plugin (or a collaborating plugin) did not bind, in the context of an actual build.

## Story: Build user views report that shows information about the available model

Add some command-line report to show basic details of the model space.

## Story: Profile report contains information about execution time of model rules

## Story: Build author declares model element in build script

## Story: Plugin provides unmanaged object as model element

For example, an extension.

### Open issues

- Prevent access to extension after used as rule input?

## Story: Build author provides unmanaged object as model element

For example, an extension or some ad hoc model object.

### Open issues

- Provide some canned model objects, e.g. the project layout (project dir, build dir, file() method, etc).

## Story: Make public the plugin rules mechanism

- Document how to use plugin mechanism, include samples.
- Indicate on `PluginAware` and `PluginContainer` what the conditions are for the plugin target to be able to take model rule plugins

### Open issues

- Improve logging and error message feedback, given that we have better insight into what's happening.

## Story: Language and publication plugins use plugin rules mechanism to define tasks

- Change the native language, jvm language and the publication plugins, to use this mechanism to define tasks (only) from their models.

# Milestone x - Make things faster

## Feature: Tasks are not created or configured when not referenced in a build

Only fire the rules to create and configure a task when it is referenced in a build:

- Added to the task graph.
- When required for `gradle tasks`
- Building certain tooling API models.
- Using `TaskContainer` to query task instances.

### Test cases

- Build script configuration closure is executed when `someTask` is required:
    - Task is added to task graph
    - Running `gradle tasks`
    - Building `GradleProject` tooling API model.
    - Using `TaskContainer` to query task instances.
- Build script configuration closure is not executed when `someTask` is not required:
    - Running `gradle help`
    - Running `gradle someTask` in another project.
    - Using `TaskContainer` to query task names.

## Feature: Rule is not executed when its outputs are up to date

Short-circuit the execution of all configuration rules whose outputs are up-to-date:
    - Inputs have not changed.
    - Rule implementation has not changed.

Continue to execute all legacy DSL.

To implement this, model objects will need to be serializable in some form.

For up-to-date checks, the implementation of a rule also forms input to the rule. Need to include this and invalidate cached outputs. Fix this for tasks at the same time.

## Extract all information available at compile time **once** from each script

The DSL for declaring rules (e.g. model {}) allows us to extract information at compile time about rules and their inputs.
Given that the rule definitions should be free of context, this information should only be extracted once.

Consider:

    # scriptPlugin.groovy
    model {
      foo.bar {
      
      }
    }
    
    # root build.gradle of 1000 subproject build
    allprojects {
      apply from: "scriptPlugin.groovy"
    }

There should be no need to actually execute the `model {}` block in `scriptPlugin.groovy` 1000 times because the rule information within can be extracted _once_ at compile time and then reused in each context it is applied.

# Backlog

Potential stories and items of work.

## Diagnostics

- When a task cannot be located, search for methods that accept `CollectionBuilder<Task>` as subject but are not annotated with `@Mutate`.
- Error message when applying a plugin with a task definition rule during task execution should include more context about the failed rule.
- Some kind of explorable/browsable representation of the model elements for a build

## Cleanup

We've introduced different mechanisms for deferring configuration.
These should be rationalised and ideally replaced with model rules.

- DeferredConfigurable
- TaskContainer.addPlaceholderAction
- SonarRunnerExtension.sonarProperties

## Misc

- Task command line arguments are applied _after_ all configuration rules

## Testing

- Plugin author verifies that plugin makes model element available
- Plugin author verifies that plugin correctly configures model element, based on supplied “other” model elements