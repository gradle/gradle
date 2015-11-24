# Feature: basic usability

## Story: Transformed DSL is enabled by default

- Allow transformed DSL rules to access project and script, for now.
- Update user guide to mention syntax.
    - Can use `$('p')` in any expression inside rule closure.
    - Can use in rule closure parameters.
- Update release notes to mention potential breaking change.

## Story: DSL rule references input using path expression syntax

- Replace `$('path')` with `$.path`
- Update user guide to mention syntax
- Update samples to use this syntax

### Test cases

- Cannot use `$` expressions in non-transformed closure.

### Backlog

- Handle case where path traverses an unmanaged element, eg `$.tasks.compileJava.destinationDir`
- Improve error message when `$.p` expression is used outside a `model { }` block.

## Story: DSL rule configures elements of ModelMap

- Apply to creation, configure by name, configure all, configure all with type, before each, after each, etc.
- Apply to chained `withType()` and rule method calls
- Allow arbitrary code to do:
    - for each in collection, apply a rule
    - if some condition is true, apply a rule
    - TBD: handle `each { ... }`, `with { ... }` etc
    - TBD: handle `{ binaries.all { ... } }` etc
- Allow configuration of an element to take the configuration of a sibling as input.
- Prevent unqualified access to owner closure
- Out-of-scope: allow configuration of an element to take the configuration of siblings with type as input.

For example:

    model {
        components {
            main(JavaLibrary) {
                // Can reference other elements, this is treated as an input to the inner rule, not the outer components { } rule.
                targetPlatform = $.platforms.java9
            }
            test {
                // Can reference a sibling as input
                targetPlatform = $.components.main.targetPlatform
            }
            beforeEach {
                targetPlatform = $.platforms.java6
            }
            withType(SpecialLibrary).beforeEach {
                targetPlatform = $.platforms.java7
            }
            if (somecode) {
                // Conditional creation
                otherLib(JavaApplication) {
                    targetPlatform = $.platforms.java12
                }
            }
            for (def item: [some, collection]) {
                // For each item in a collection create a library
                create("${item}Lib", JavaLibrary) {
                    targetPlatform = $.platforms.java7
                }
            }
            [some, collection].each { create(...) { ... } } // out-of-scope for this story, won't work
        }
    }

### Test cases

- Works with `ModelMap<T>` and specialized subtypes such as `ComponentSpecContainer`.

## Story: DSL rule configures children of a `@Managed` type

- Allow deferred configuration of any property of any non-scalar type of a `@Managed` type, by mixing in configuration methods that accept a Groovy closure.
- When used from Groovy, these methods attach the closure as a rule action.
- When used from the model DSL, these methods define a nested rule.
- Allow configuration for a nested structure to take configuration for a sibling as input.
- Allow arbitrary code to conditionally configure a nested target.
- TBD: Apply to properties of non-managed software model types and views, and remove ad-hoc configure methods.
- TBD: Apply only to mutable views?

For example:

    model {
        components {
            main {
                sources {
                    java {
                        baseDir = $.project.projectDir('src')
                    }
                }
                binaries {
                    jar {
                        outputDir = $.sources.java.baseDir
                    }
                }
            }
        }
    }

### Test cases

- Configuration is deferred when applied to property.
- Works for properties with `@Managed` type, software model type, `ModelMap`, `ModelSet`, `List`, `Set`, unmanaged type.
- Works for subject that is a `@Managed` model elements.
- Works for subject that is a `@Managed` subtype of software model type.
- Works for subject that is a `@Managed` internal view of software model type.
- Does not work for properties with scalar type.

## Story: DSL rule configures children of a `ModelSet`

- Apply to creation, before each, after each, etc.
- Apply to chained `withType()` and rule method calls

For example:

    model {
        someSet {
            create {
                // ...
            }
            beforeEach {
                // ...
            }
            afterEach {
                // ...
            }
        }
    }

### Backlog

- Apply some builder pattern, so that dependencies can be backed by a `ModelSet`.

## Story: DSL rule references input relative to subject

- Allow a closure parameter to be declared, this lvar can be used to reference inputs
- Improve error message when extracted input cannot be bound
- Update user guide and samples to use this syntax
- Update release notes to mention potential breaking change.
- Report actual expression used for input reference in error messages.

For example:

    model { m ->
        components { c ->
            mylib(JavaLibrarySpec) {
                targetPlatform m.platforms.java6 // resolve path relative to subject `m`
            }
            test {
                targetPlatform c.test.targetPlatform
            }
        }
        tasks { t ->
            thing(MyTask) {
                dependsOn t.otherTask // resolve path relative to subject `t`
            }
        }
    }

### Test cases

- Can configure a component using a sibling component as input.

## Story: DSL rule configures task action

- Allow input references using subject parameter

For example:

    model {
        tasks {
            myTask(Task) {
                doLast { // TBD - perhaps use a new syntax here or at least a better name
                    ...
                }
            }
        }
    }

## Story: Deprecate access to project and script from DSL rules

- Warn when the project or script are used from the transformed DSL.
- Add some way to get at project dir and build dir, as this is common use case that project/script are used for.
- Lock down access to lvars defined in the script.
- Remove access after one or two releases.

## Story: Syntax to apply DSL rules to multiple projects

- Add block to `settings.gradle` to allow this.
- Update user guide and samples.

For example:

    projects {
        all {
            // Later story might add a plugins { } block here
            components {
                main(JavaLibrarySpec) {
                    ...
                }
            }
            binaries.withType(NativeExecutableBinarySpec) {
                ...
            }
        }
        project('thing') {
            ...
        }
        root {
            ...
        }
    }

## Story: Deprecate `project.model { }` method

- Should use the DSL from the previous story instead.
- Remove after one or two releases.

# Backlog

- Add DSL to attach rules to children of current subject
    - For example, a nested closure
    - TBD - Implicitly use subject as input to nested rule, or require an explicit reference?
- Improve creation DSL to allow parameterized types
- Improve creation DSL to allow type to be left out when it can be inferred or there is a reasonable default
- Improve creation DSL to allow name to be left out when there is a reasonable default
- Improve configuration DSL to allow subject's view type to be declared
- Improve DSL to allow rule input's view type to be declared
- Improve DSL so that it is more self-describing:
    - Make more obvious that `<call> { ... }` defines a rule, where the closure runs later
    - Distinguish between API methods such as `all { ... }` and dynamic config methods such as `someName { ... }`
    - Distinguish between API methods such as `withType(Type) { ... }` and dynamic creation methods such as `someName(Type) { ... }`
- Conveniences to target cases where subject is child with name, subject is children with type, subject is children that meet some criteria etc
- Type coercion
- Conveniences where subject is collection
- Property references instead of nesting for simple configuration
- Nice error reporting
- Possibly use `?.` to allow optional references?
- Possibly use `['name']` expressions in input references?
- Support DSL rule references input relative to another input reference
- Etc
