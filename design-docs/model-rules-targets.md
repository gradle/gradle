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
    void defineTaskRules(TaskRulesSource rule, BinarySpec binary) {
        // configures the rule
        rule.tasks = binary.tasks
        rule.sources = binary.inputs
    }
    
    // Can be reused for any thing built from sources
    abstract static class TaskRulesSource extends RulesSource {
        @Subject // When present, defines the subject of all the rules on this `RulesSource`
        abstract ModelMap<Task> getTasks()
        abstract void setTasks(ModelMap<Task> tasks)
        
        @Input // Each such property defines an implicit input of all the rules on this `RulesSource`
        abstract Set<LanguageSourceSet> getSources()
        abstract void setSources(Set<LanguageSourceSet> sources)
        
        @Mutate
        void defineTasks(LanguageRegistry langReg) { // Can supply additional inputs
            getTasks().doStuffWith(getSources(), langReg)
        }
    }
    
    @Rules
    void defineSourceInputs(InputsRulesSource rule, BinarySpec binary) {
        rule.target = binary.inputs
        rule.sources = binary.sources
    }
    
    @Rules
    void defineSourceInputs(InputsRulesSource rule, BinarySpec binary) {
        rule.target = binary.input
        rule.sources = binary.component?.sources // using `null` to mean 'ignore this rule source`, could be more explicit
    }
    
    abstract static class InputsRulesSource extends RuleSource {
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
        comp.binaries.all(InputsRulesSource) { rules, binary ->
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

### Rule method can define rules using a `RuleSource`

- Add `@Rules` annotation and allow to be attached to method.
- A `@Rules` method can accept inputs but not do anything with them at this stage.
- Invoked and applied when subject is transitioned to `initialized`.
- Can be applied to:
    - Top-level `RuleSource`
    - Rules source applied via `ModelMap.withType()`
    - Rules applied using a `@Rules` rule.
- Error when `@Rule` applied to a method whose parameter is not a `RuleSource`.

- TBD: useful descriptor for rules in `RuleSource`.

### `RuleSource` can declare inputs to be bound

- Allow `@Input` properties to be declared on `RuleSource` subtypes.
- Getter and setter must be abstract.
- Generate and cache an implementation class.
- Allow a `@Rules` method to set values for the `RuleSource` properties:
     - Inputs should be at or after `initialized`.
     - Inputs should not be mutable.
- `@Input` properties treated as implicit input for all rules. 
    - Getter should provide a value during rule invocation.
- Error when `RuleSource` used with null value for input property.
- Error when mutating `@Input` property outside `@Rule` method.
- Error when reading `@Input` property outside self rule execution.
- TBD: useful descriptor for input property.

### `RuleSource` can declare subject to be bound

- Allow `@Subject` property to be declared on `RuleSource` subtypes.
- Validation as per inputs.
- User guide and samples describe how to use this.
- TBD: useful descriptor for subject property.
