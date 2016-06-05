This spec covers what is termed the “software model”.

This is a large topic.
Specific sub streams have been broken out into other concurrent specs.

* `managed-model.md`
* `component-model-for-jvm-components.md`
* `model-rules-dsl.md`
* `model-reporting.md`
* `task-bridging.md`

# Backlog

Potential stories and ideas.
Unordered and not all appropriately story sized.

## Rule ordering

- Node ancestry should be self closed before node is used as an input, to pick up rules and configuration performed by a rule with ancestor as subject.

## Diagnostics

- Error message for rule execution/validation failure:
    - Should include source file and line information, when known
    - Should include information about 'identity' of the rule, eg the same method attached to different projects.
    - Should include information about 'why' the rule was executed, eg during build script execution, as input to some rule.
- Progress logging should show something about rule execution, eg when closing the task container for a project.
- When a task cannot be located, search for methods that accept `CollectionBuilder<Task>` as subject but are not annotated with `@Mutate`.
- Error message when applying a plugin with a task definition rule during task execution should include more context about the failed rule.
- Profile report contains information about execution time of model rules
- Build user is alerted when a required model rule from a plugin does not bind (i.e. plugin was expecting user to create some element)
- Build user is alerted when a build script model rule does not bind (i.e. some kind of configuration error)
- Bind by path failures understand model space structure and indicate the failed path “link” (e.g. failure to bind `tasks.foo` informs that `tasks` exists and what it is)
- Collapse rule descriptors to “plugin” granularity where appropriate in error message (i.e. plugin users typically don't need information about plugin internals, but need to know which plugin failed).
- Error message when no collection builder of requested type should provide more help about what is available
- When should validation of rule reference binding occur?
- Error message when DSl rule or method rule fail should report the purpose (eg could not configure task thing) rather than
  the mechanics (eg SomeClass.method() threw an exception).
- Error message on mutating input:
    - Should include some information about the identity of the input, or its display name, and probably how it was referenced in the code
    - Should provide some information about what the mutation was.

## Software model

- Validate (or otherwise encode) the names of components, binaries, LSS, etc so that the result makes sense for file names and task names. 

## Documentation

- How to write a rule based plugin is not documented
- Migration guide and/or build migration assistance

## Reporting

- Model report shows too much detail by default, should probably not include tasks and just show 'data'
- Model report should be robust in the face of failures, as it will likely be used to diagnose such failures.
- Values for properties with primitive and other scalar types do not show up in model report unless they have been set
- Value for property with type collection of scalars is not shown in model report unless value has been set.
- `null` value for mutable property with type collection of scalars is not shown in model report (but empty list is).
- Creator for tasks defined by a `@BinaryTask` rule are wrong
- Creator for binaries defined by a `@ComponentBinaries` rule contain a bunch of confusing and irrelevant detail

## RuleSource

- Methods of rule source classes must be private, or be declared as rules, but cannot be both.
    - Remove constraints on private methods (eg allow parameterized private methods).
- Allow helper (i.e. non rule) methods to be parameterized for types in rule source plugins (currently we do not allow any parameterized methods)

## Managed types

### Scalar collections

- Need reasonable `toString()` value for scalar collection implementations. Should match that of other managed types.
- Documentation does not mention copy-on-write semantics for mutable properties of type scalar collection.
- Support `Collection` of scalar as property type, at least for mutable property.

### Inspection

- Collect all validation problems
- Detect zero-args constructors (or allow)
- Detect overrides of Groovy MOP methods (or allow)

Same for `RuleSource` inspection

## Cleanup

We've introduced different mechanisms for deferring configuration.
These should be rationalised and ideally replaced with model rules.

- DeferredConfigurable
- TaskContainer.addPlaceholderAction
- Move native plugin suite to new mechanism
- Move publishing plugin suite to new mechanism

## Tasks

- Support build items as input, infer dependencies
- Support `copy.from $.some.buildItem`
- Support `dependsOn $.tasks.withType(t)`
- Remove `TaskContainer` view from model space
- Prevent illegal mutation of `Task` in model space
- Task command line arguments are applied _after_ all configuration rules

## Misc

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

## Reuse

- Discard `Task` instances, plus rules and model elements reachable from or that have access to the legacy API/DSL.
- Discard rules and model elements defined by any rule that is not reusable.
- Discard model elements defined by any rule whose inputs are not reusable.
- Fine grained reuse on build logic changes (e.g. connect rules to source that changed, and invalidate)
- Mechanism for force purging the “cached” model (?)
- Sugar for common types of external (volatile) inputs
- Short circuit invalidation propagation downstream by stopping transitive invalidation when rebuilt element matches “previous” state
- Model is reused when changing between different logical projects
- `BasePlugin.configureAssemble()` implicitly retaining the project instance which is a problem for reuse

## Migration/Bridging

- Provide supported pattern for migrating plugins
