This spec covers what is termed the “new configuration model”.

This is a large topic.
Specific sub streams have been broken out into other concurrent specs.

* `managed-model.md`

# Stories

## Node ancestry is self closed before node is used as an input

We do not currently enforce that all ancestors of an input are realised, which means that there may still be pending mutations for the child (as a mutation of the parent).

### Test Coverage

- Rule using child of managed node as input node has all mutations applied, that were expressed as mutations of the parent node
- Rule using child of managed node can depend on a sibling, that has all mutations applied, that were expressed as mutations of the parent node
- Cycle is reported when a rule tries to depend on a child of the subject as an input

## Cycle involving multiple rules of same lifecycle phase does not cause rules to be executed twice

Given the following:

```
class Rules extends RuleSource {
    @Model List<String> m1() { [] }
    @Model List<String> m2() { [] }
    @Model List<String> m3() { [] }

    @Mutate void m2ToM1(@Path("m1") m1, @Path("m2") m2) {
        if (!m1.empty) {
            throw new IllegalStateException("m2ToM1 has executed twice")
        }
        m1 << "executed"
    }

    // in cycle…
    @Mutate void m3ToM1(@Path("m1") m1, @Path("m3") m3) {}
    @Mutate void m1ToM3(@Path("m3") m3, @Path("m1") m1) {}

    @Mutate void addTask(ModelMap<Task> tasks, @Path("m1") m1) {}
}
```

The build will fail because the `m2ToM1` is indeed executed twice.
What should happen is that a cycle should be reported between `m3ToM1` and `m1ToM3`.

The implementation should be done in such a way

### Test Coverage

- As above, build fails by reporting cycle between `m3ToM1` and `m1ToM3`.
- As above but including earlier lifecycle rules (e.g. `@Defaults`) as well, verifying they do not execute twice

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

> This story was started, and then “cancelled” due to initial performance testing demonstrating that model reuse isn't as necessary as initially thought.
> The rule based model is “faster” than the imperative model even without reuse, reducing the need for reuse.
> Reuse will still be necessary later in some form to open up the possibility of “expensive configuration”.

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
    - should also work with `ClassLoaderScope`, so that a state cache can be associated with a scope.
    - use for plugin id -> class mappings, plugin class inspection, task annotation inspection.
    - need to be able to take arbitrary class and map to a scope or state cache for that type.
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
