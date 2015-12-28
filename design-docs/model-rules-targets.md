## Use cases

Some example rules:

1. For each binary `b`, calculate tasks for `b` from the input source sets for `b` and the language registry.
2. For each binary `b`, the inputs source sets for `b` include all the source sets owned by `b`.
3. For each binary `b` owned by component `c`, the source sets for `b` include all the source sets owned by `c`.

Missing features that prevent such rules from being implemented in a plugin:

- Apply rules to all subjects that match some predicate.
    - Most often match on type.
- Reference subject relative to some other object.
- Reference inputs relative to subject.

Ideally statically typed, for early feedback and authoring support, and to allow inference without execution.

## Programmatic creation and binding of rules

This approach is to build on `RuleSource` and allow some programmatic control over `RuleSource` instances.

- Allow the subject of the rules of a `RuleSource` to be programmatically declared as a property of the `RuleSource`.
- Allow some inputs of the rules of a `RuleSource` to be programmatically declared as properties of the `RuleSource`.
- Implementation of state is managed.
- Add a `@Rules` rule annotation, which is attached to a method to define a rule that defines a `RuleSource`, in the same way as `@Model` defines a model element.
    - Accepts some `RuleSource` subtype as first parameter, which can be mutated to attach references to subject and/or some inputs.
    - Can accept one or more inputs as subsequent parameters.
    - Inputs are not mutable nor readable, only the structure can be queried to reference various model elements.

An example, in a `RuleSource` applied to each `BinarySpec`:

    @Rules
    void defineTaskRules(TaskRuleSource rule, BinarySpec binary) {
        // configures the rule
        rule.tasks = binary.tasks
        rule.sources = binary.inputs
    }
    
    // Can be reused for any thing built from sources
    abstract static class TaskRuleSource extends RuleSource {
        @Subject // When present, defines the subject of all the rules on this `RuleSource`
        abstract ModelMap<Task> getTasks()
        abstract void setTasks(ModelMap<Task> tasks)
        
        @Input // Each such property defines an implicit input of all the rules on this `RuleSource`
        abstract Set<LanguageSourceSet> getSources()
        abstract void setSources(Set<LanguageSourceSet> sources)
        
        @Mutate
        void defineTasks(LanguageRegistry langReg) { // Can supply additional inputs
            getTasks().doStuffWith(getSources(), langReg)
        }
    }
    
    @Rules
    void defineSourceInputs(InputsRuleSource rule, BinarySpec binary) {
        rule.target = binary.inputs
        rule.sources = binary.sources
    }
    
    @Rules
    void defineSourceInputs(InputsRuleSource rule, BinarySpec binary) {
        rule.target = binary.input
        rule.sources = binary.component?.sources // using `null` to mean 'ignore this rule source`, could be more explicit
    }
    
    abstract static class InputsRuleSource extends RuleSource {
        @Subject
        abstract Set<? super LanguageSourceSet> getTarget()
        abstract void setTarget(Set<? super LanguageSourceSet> target)
        
        @Input
        abstract Set<? extends LanguageSourceSet> getSources()
        abstract void setSources(Set<? extends LanguageSourceSet> sources)
        
        @Mutate
        void attachSources() {
            getTarget.addAll(getSources())
        }
    }
    
Or, from a `RuleSource` attached to each `ComponentSpec`:

    @Rules
    void defineBinaryRules(ComponentSpec comp) {
        comp.binaries.all(InputsRuleSource) { rules, binary ->
            // Action to configure the rule, not the binary
            rules.sources = comp.sources 
            rules.target = binary.inputs
        }
    }
        
## Apply rules to multiple elements

Mark a rule as applicable to all elements of a given type.

- Add an `@Each` annotation that can be attached to a rule method, which applies the rule to each element of the given type.
- Cannot be used with `@Path` to select the subject.

For example:

    @Defaults @Each
    void applySourceDirs(JvmBinarySpec binary, JvmPlatforms platforms) {
        binary.targetPlatform = platforms.current
    }
    
    @Finalize @Each
    void applySourceDirs(LanguageSourceSetInternal lss) {
        lss.srcDir = lss.srcDir ?: "${lss.baseDir}/${lss.name}"
    }

And combining:

    @Rules @Each
    void applyBinaryRules(BinaryRules rules, BinarySpec binary) {
        rules.sources = binary.inputs
    }

## Stories

### Rule method can define additional rules by providing a `RuleSource` implementation

- Add `@Rules` annotation and allow to be attached to method.
- A `@Rules` method can accept inputs but not do anything with them at this stage.
- A `@Rules` method must accept two parameters. The first is the `RuleSource` to configure, the second is the target for the `RuleSource`.
- The method is invoked and the rules applied when target is transitioned to `initialized`.
- Can be applied to:
    - Top-level `RuleSource`
    - Rules source applied via `ModelMap.withType()`
    - Rules applied using a `@Rules` rule.
- Userguide and sample
- Error when `@Rule` applied to a method whose first parameter is not a `RuleSource`.
- Error when `@Rule` applied to a method that does not accept 2 parameters.
- Error attempting to create a model element with type `RuleSource`.

#### Test cases

- Plugin can use a `@Rules` method to define a `RuleSource`, and the rules on the `RuleSource` are applied  
- Plugin can specify a target for the `RuleSource` and this is used to resolve by type and by path references
- `RuleSource` applied using `@Rules` can define all kinds of rules, including `@Defaults` rules.
- Error cases as above 
- Useful descriptor for rules in `RuleSource`.

#### Backlog

TBD - Rule precedence
TBD - Support type registration rules?
TBD - Support `@ComponentBinaries` and `@BinaryTasks` rules?

### Rule method can bind inputs of rules of a `RuleSource` implementation

- Allow `@RuleInput` properties to be declared on `RuleSource` subtypes.
- Getter and setter must be abstract, allow `RuleSource` types to be abstract.
- `RuleSource` validation:
    - Validation error when abstract `RuleSource` has abstract method that is not a getter or setter.
    - Validation error when abstract `RuleSource` has concrete method that is a getter or setter.
    - Validation error when abstract `RuleSource` has read-only property.
    - `@RuleInput` is attached to a method other than a getter method.
- Generate and cache an implementation class.
- Allow a `@Rules` method to set the values of the `RuleSource` properties:
     - Inputs should be at or after `initialized`.
     - Inputs should not be mutable.
     - Can read or mutate property during `@Rule` method invocation
- `@Input` properties treated as implicit input for all rules on the `RuleSource`. 
    - Getter should provide a value during rule method invocation.
    - Can read property during `@Rule` method invocation
- Userguide and sample
- Error when `RuleSource` used with `null` value for input property.
- Error when `RuleSource` with inputs is applied as plugin, or to element of ModelMap.
- Error when setting `@RuleInput` property to a value that not a model element or scalar value
- Error when reading or mutating `@RuleInput` property outside `@Rule` method.
- Error when mutating `@RuleInput` property in own rule execution.
- Reports implementation type on missing property/method exception, etc

#### Test cases

- Error cases as above.
- Reasonable `toString` for generated `RuleSource` implementation.
- Reasonable error message for missing method or missing property on generated `RuleSource` implementation.

#### Backlog

TBD - Add an API to allow `RuleSource` to be applied, and inputs build, for `ModelMap` and `ModelSet` elements, plus for a `@Managed` type.

### Rule method can bind subject of rules of a `RuleSource` implementation

- Allow `@Subject` property to be declared on `RuleSource` subtypes.
- Validation as per inputs.
- User guide and samples describe how to use this.

### `RuleSource` methods can be applied to all subjects of matching type

Candidates:

- Add `@Each` annotation. When applied to a rule method, the rule method is applied to each matching subject.
- Change all by-type rules to apply to all matching subjects (which would actually be the case for `@Path` as well). Add a `@Single` annotation to declare that exactly one is expected.

### Rule should not be applied to a given binary more than once

- Change `ModelMap.put()` to add a reference to an element.
- Change rule application to ignore references.
- Change model report to format references as a link to the target element.
- Fix `BinaryTasksModelRuleExtractor` to target each `BinarySpec` elements, rather than the `binaries` container. Should _actually_ target the `tasks` container of each `BinarySpec` element.

### Rule should not be applied to a given task more than once

- Apply the above to `tasks`.

### Rule should not be applied to a given source set more than once

- Apply the above to `sources`.

### Core plugins apply rules using `RuleSource` instead of iteration

Use these features in the core plugins.
