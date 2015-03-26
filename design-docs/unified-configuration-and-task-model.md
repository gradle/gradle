This spec covers what is termed the “new configuration model”.

This is a large topic.
Specific sub streams have been broken out into other concurrent specs.

* `managed-model.md`

# Stories


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

## Rules are extracted from plugins once and cached globally

Currently, when applying rule based plugins we go reflecting on the plugin and immediately applying rules that we find.
This means that in a 1000 project multi project build where every project uses the Java plugin we do the reflection 1000 times unnecessarily.
Instead, we should separate the reflection/rule extraction and application (i.e. pushing the rules into the model registry) so that we can cache the reflection.

This involves changing `ModelRuleInspector#inspect` to return a data structure that represents the discovered rules, instead of taking a `ModelRegistry`.
The returned data structure can then contain rules in an applicable fashion that can be reused.
A (threadsafe) caching layer can be wrapped over this so that we only inspect a plugin class once.
Care needs to be taken with the caching to facilitate classes being garbage collected.

Invalid plugins do not need to be cached (i.e. it can be assumed that an invalid plugin is a fatal event).

The `ModelRuleInspector` should be available as a globally scoped service.

See tests for `ModelRuleSourceDetector` and `ModelSchemaStore` for testing reclaimability of classes.


### Test Coverage

- ~~Rule based plugin can be applied to multiple projects with identical results, and rules are extracted only once~~
- ~~Cache must not prevent classes from being garbage collected~~
- ~~Rules extracted from core plugins are reused across builds when using the daemon~~
- ~~Rules extracted from user plugins are reused across builds when using the daemon and classloader caching~~

## Task selection/listing realises only required tasks from model registry instead of using task container

1. ~~Add get(ModelPath, ModelNode.State) to ModelRegistry~~
1. ~~Support modelRegistry.get(“tasks”, SelfClosed)~~
1. ~~Realize the task container as tasks are needed instead of at the end of evaluation (TaskNameResolver, DefaultProjectAccessListener, DefaultProjectLocator) - _interim step_~~
1. ~~Change task placeholder mechanism to use model registry (internally in DefaultTaskContainer) instead of ad hoc deferral mechanism~~
1. ~~Change task selection to avoid realising all tasks (i.e. self close the task container, realise just the tasks needed)~~
1. ~~Update ProjectTaskLister (used by Tooling API (GradleProjectBuilder), ‘tasks’ task and GUI) to use model registry etc.~~

Note: Having `DefaultProjectAccessListener` and `DefaultProjectLocator` require the full closing of the task container is ok for this story.

### Test coverage

1. ~~No error when model node is requested at state it is already at~~
1. ~~Error when model node is requested at “previous” state~~
1. ~~Simple task defined via `tasks.named()` is not realised if not requested on command line~~
1. ~~Task container can be self-closed by task selector/lister and then later graph-closed~~
1. ~~Tasks defined via rules are present in `gradle tasks` report~~
1. ~~Tasks defined via rules are present in relevant Tooling API models that request task list, and can be executed by Tooling API~~
1. ~~Tasks defined via rules are present in Gradle UI and can be executed by Gradle UI~~
1. ~~Existing coverage for command line tasks selection and Tooling API models continues to function without change~~

## Rule source plugins are instantiated eagerly

Rules in rule source plugins can be instance scoped.

We should instantiate when extracting rules from the plugin as part of the class level validations.

### Test Coverage

- ~~Rule source plugin throwing exception in default constructor fails plugin _application_~~

## Mutation rules are always executed in a reliable order

This story strengthens the ordering semantics of mutation rules.

Currently, mutation rules are executed in the order in which they fully bind.
This can cause a change in the inputs to a rule to change when the rule is run in surprising ways.
We should always guarantee that rules are executed in discovery order, not binding order.

Rules are discovered through the application of plugins, and execution of build scripts.
We can use the order of plugin application, and rules in build scripts.
Within a plugin, rules can be ordered in some deterministic way (e.g. sort rules by signature).

## Collection mutation rule specifies input taking mutation rule for particular model element

    interface CollectionBuilder<T> {
      void named(String named, Class<?> ruleSource)
    }

The rule source class functions the same as a rule source applied to Project except that bindings are relative to the collection item
(which can be said to be the case already, but until now there has only been one scope).

The rule source must be able to bind to the outer scope. For example…

    @RuleSource
    class AssembleTaskRules {
        void dependOnBinaries(Task assemble, BinaryContainer binaries) {…}
    }

    tasks.named("assemble", AssembleTaskRules)

Here, the subject `assemble` is of the inner scope while the input `binaries` is of the outer scope. 
All by-path bindings will be interpreted relatively.
Input by-type bindings are only capable of binding to the outer scope.
Subject by-type bindings must be of the inner scope (otherwise we are back to anything-can-say-anything-about-anything) and they can only bind to scope element and it's immediate children.

For this story, no lifecycle alignment validation is specifically required beyond ensuring a `@Mutate` rule where the subject is a `ManagedMap` specifying a `@Mutate` rule for an item and that rule being executed when the item is needed.
That is, robust alignment of lifecycle phases is out of scope.

### Test coverage

1. ~~Rule can successfully bind to inputs, which are only realised if rule is required~~
1. ~~Rule input binding failure yields useful error message (including information about binding scope, to help debug bindings)~~
1. ~~Rule execution failure yields useful error message, allowing user to identify failed rule~~
1. ~~Mutate rule about container item added during container mutate rule executes and realises inputs when container item is needed~~
1. ~~Rule source is subject to same blanket constraints as rule sources applied at project level, with error message helping user identify the faulty rule~~
1. ~~Subject by-type and by-path bindings are of inner scope~~
1. ~~Subject can be bound to a child of the scope in which the rule is applied~~
1. ~~Input by-path bindings are of inner scope~~
1. ~~Input by-type bindings are of outer scope~~

## Model infrastructure performance is benchmarked

Benchmarking builds that use the new model infrastructure vs builds doing the same work using legacy configuration mechanisms will allow to verify if the new way of configuring builds is actually faster.
Access to benchmark result history of builds using the new model infrastructure will also allow to verify if introduced improvements (e.g. caching, short-circuiting configuration) bring the expected performance gains.

Benchmarking the effect of changing inputs, configuration and rules is out of scope of this story.

Each benchmark should be executed for the following scenarios:
- build everything
- build a small subset (if it makes sense)
- build nothing (e.g. `help` or `clean`)

### Comparison of old and new Java plugins

- a single variant, plain java build
- for 1, 25 and 500 projects

### Android mock-up

- implemented using a plugin
- creates tasks for combination of flavours and types
- uses fully managed types
- single project
- for small, medium and large model sets (number of flavours and types)

## Model rule bindings are exhaustively validated

Our current model rule validation hinges on detecting rules with “unbound references”.
That is, we look for rules where at that point in time the rule could not be executed because we are unable to satisfy its dependencies.
Our current mechanism does not attempt to find out if the rule's dependencies could be satisfied by realising more of the model.
This story improves validation by doing this.
That is, validating based on the state of the model registry (a.k.a. meta model), not on the current state of the model.

For each unbound rule reference, we will effectively “force” it to bind. 
For unbound by-type bindings this will involve “self closing” the root node (or relevant scope), as this will give us knowledge of all the top level elements (based on rules discovered so far).
For unbound by-path bindings this will involve “self closing” from the root node to the parent of the path.

As validation will now realize elements, preventing future rules from applying, we will have to move validation to occur as late as possible.
Ideally just before execution begins.

We should not perform validation on a project that is not required for a build.

### Test Coverage

- ~~Existing validation coverage~~
- ~~Model rule with dependency on non task related collection element that does exist, passes validation~~
- ~~Model rule that does not bind, specified for project that is not used in build, does not fail the build~~

## Methods of rule source classes must be private, or be declared as rules

This story improves the usability of rule source classes by making it less easy to forget to annotate a rule method.

1. All public and package scope methods of a `RuleSource` must be rules, and be annotated accordingly
2. `private` and `protected` methods cannot be rule methods
3. `RuleSource` class must have at least one rule

In both cases, exceptions should be thrown similarly to other `RuleSource` constraint violations.

### Test Coverage

1. with no methods is invalid
1. with no rule methods (i.e. has private/protected) is invalid
1. private/protected instance/static method with rule annotation fails
1. public/package instance/static method without rule annotation fails

## Model is partially reused across daemon builds, ignoring potential configuration changes

This story adds initial support for persisting aspects of the build configuration in memory and reusing across builds.
As this is a large chunk of work, this story covers initial work and does not take the feature to production readiness.
The initial implementation assumes that configuration does not change across builds, either by a change in build logic or by external state.
It will be wrapped in a feature toggle.

Task instances attached to the model cannot be reused, as they retain references to the `Project` object and other build instance state.
When “reusing the model”, tasks must be rebuilt.
Tasks are examples of _ephemeral_ model elements.
By extension any model element that depends on an ephemeral model element is also implicitly ephemeral.
Later refinements may make it possible to detect that the dependents of ephemeral elements are not actually ephemeral because they do not transfer ephemeral state. 

Steps/stages:

1. Add ability to reconstitute/restore a model registry (and backing graph) from another registry (just assumes state at this stage)
1. Add ability to declare model elements as ephemeral
1. When restoring a registry, realised ephemeral elements are discarded (i.e. reverted to `known` state)
1. All dependants of ephemeral elements (elements whose creators/mutators take ephemeral elements as inputs) are discarded
1. Mark `tasks` model element as ephemeral
1. Add global scope service that retains/provides model registries across builds in some format (store registries by project path)
1. When feature toggle is active, save/restore registries in between builds
1. If a build fails, completely purge the registry store, forcing rebuild on next build

Constraints/assumptions:

1. Daemon builds one logical project for its life (i.e. registry store doesn't consider changes)
1. Build logic does not change for the life of the daemon (incl. used plugins are not “changing”)
1. Any failure may leave the registry in a non reusable state, and therefore must be purged

The feature will depend on the classloader caching feature.

### Test Coverage

1. Ephemeral model element is rebuilt when requested from a restored model registry
1. Dependants of ephemeral model element are rebuilt …
    1. Model element where creator depends on an ephemeral
    1. Model element where “mutator” of all types depends on an ephemeral
1. Descendants of ephemeral model element are rebuilt …
    1. Model element where creator depends on descendant of an ephemeral
    1. Model element where “mutator” of all types depends on a descendant of an ephemeral
1. Model registry is not reused when feature toggle is not active (sanity check)
1. Correct project specific registries are assigned to each project in multi project build
1. Model is completely rebuilt following a build failure
1. Build using Java component plugin can be reused
1. Tasks are rebuilt each time when reusing model registry
1. Error when model reuse enabled but not classloader caching
1. Reuse of a model registry can realise previously unrealised model elements (i.e. required tasks can change between builds, requiring different model element dependencies)

## Rationalize realizing tasks during Gradle lifecycle

Things are a bit tangled right now. There are at least the following aspects:

1. Project.tasks.discoverTasks()
2. Project.fireDeferredConfiguration()
3. ProjectAccessListener
4. TaskNameResolver

This should be simplified.

## Don't 

# Open Questions

- How to order mutations that may derive properties from the subject
- How to add item to collection in Java API, using inputs just for its configuration (i.e. not the parent containers)
- Pattern for declaring rules that directly add items to containers (e.g. a rule that directly creates a task, i.e. inserts into `tasks`)

# Backlog

Potential stories and ideas.
Unordered and not all appropriately story sized.

## Diagnostics

- Error message for rule execution/validation failure should include information about 'identity' of the rule, eg the same method attached to different projects.
- Error message for rule execution/validation failure should include information about 'why' the rule was executed, eg during build script execution, as input to some rule.
- Progress logging should show something about rule execution, eg when closing the task container for a project.
- When a task cannot be located, search for methods that accept `CollectionBuilder<Task>` as subject but are not annotated with `@Mutate`.
- Error message when applying a plugin with a task definition rule during task execution should include more context about the failed rule.
- Profile report contains information about execution time of model rules
- Cyclic dependencies between configuration rules are reported
- Build user is alerted when a required model rule from a plugin does not bind (i.e. plugin was expecting user to create some element)
- Build user is alerted when a build script model rule does not bind (i.e. some kind of configuration error)
- Bind by path failures understand model space structure and indicate the failed path “link” (e.g. failure to bind `tasks.foo` informs that `tasks` exists and what it is)
- Collapse rule descriptors to “plugin” granularity where appropriate in error message (i.e. plugin users typically don't need information about plugin internals, but need to know which plugin failed).
- Error message when no collection builder of requested type should provide more help about what is available
- Force binding induced cycles for rules that use descendants of subject as inputs which won't bind result in a stack overflow
- When validation of rule reference binding should occur (?)
- Error message when DSl rule or compiled rule fail should report the purpose (eg could not configure task thing) rather than
  the mechanics (eg SomeClass.method() threw an exception).

## Cleanup

We've introduced different mechanisms for deferring configuration.
These should be rationalised and ideally replaced with model rules.

- DeferredConfigurable
- TaskContainer.addPlaceholderAction
- SonarRunnerExtension.sonarProperties
- Move native plugin suite to new mechanism
- Move publishing plugin suite to new mechanism
- `CollectionBuilder` is not part of public API
- `@RuleSource` is not documented, nor is the concept of a rule based plugin (need to tidy up docs on `PluginAware` and `ObjectConfigurationAction`)
- Allow helper (i.e. non rule) methods to be parameterized for types in rule source plugins (currently we do not allow any parameterized methods)
- `ModelRuleDescriptor` values extracted from methods should be more concise (e.g. use simple class names)
- `ModelRuleDescriptor` values extracted from scripts should be more concise (e.g. use root dir relative path to script)

## Tasks

- Remove `TaskContainer` from model space
- Prevent illegal mutation of `Task` in model space

## Misc

- Task command line arguments are applied _after_ all configuration rules
- Select internal services are made available to configuration rules (i.e. remove `ServiceRegistry` from model space)
- Make `buildDir` available in model space
- Remove `ExtensionContainer` from model space
- Semantics of model element removal are not well defined
- `tasks.withType(Test).named("compileJava", SomeRules)` - withType() aspect is ignored and does not influence bindings
- Cross project task lookup causes target project task container rules to be fired (and task container to be self closed), whether they are required or not (i.e. requested task may be completely legacy)

## Testing

- Plugin author verifies that plugin makes model element available
- Plugin author verifies that plugin correctly configures model element, based on supplied “other” model elements
- Plugin author verifies that all plugin configuration rules bind

## Performance

- Cache/reuse model elements, avoiding need to run configuration on every build
- Should replace use of weak reference based class caches to strong reference and forcefully evict when we dump classloaders (much simpler code and fewer objects)
- `DefaultProjectLocator` and `DefaultProjectAccessListener` (used by project dependencies) force realisation of complete task container
- DefaultModelRegistry stores RuleBinder implementations twice
- Rule references are bound eagerly (should be deferred until the rule is needed)

## DSL

- Specify type of rule input
- Specify type of rule subject
- Plugin author specifies model type to use in DSL when type is unspecified
- Mechanism for declaring new top level (adhoc?) model elements in build script

## Reuse

- Fine grained reuse on build logic changes (e.g. connect rules to source that changed, and invalidate)
- Mechanism for force purging the “cached” model (?)
- Sugar for common types of external (volatile) inputs
- Short circuit invalidation propagation downstream by stopping transitive invalidation when rebuilt element matches “previous” state
- Model is reused when changing between different logical projects

## Productization

- Document general configuration rule concepts (incl. rationale for change)
- Document rule source plugin concepts

## Migration/Bridging

- Provide supported pattern for migrating plugins
