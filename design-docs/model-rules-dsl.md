# Feature: basic usability

## Story: Transformed DSL is enabled by default

- Allow transformed DSL rules to access project and script, for now.
- Update user guide to mention syntax.
- Update release notes to mention potential breaking change.

## Story: DSL rule references input using simplified syntax

- Replace `$('path')` with `$.path`

## Story: Top level DSL rule references input using idiomatic syntax

- Add an alternative to `$('path')` that is closer to idiomatic Groovy
- Resolve an expression `a` to model element with path `a` when subject does not have property `a`.
- Resolve a property expression `a.b.c` to model element with path `a.b.c` when subject does not have property `a`.
- Resolve a method expression `a.b.c()` to method call `c()` on model element with path `a.b` when subject does not have property `a`.
- TBD - resolve paths relative to subject, or root?
- TBD - need to make this work in nested closures, configure rules and task actions in particular. 
- TBD - More efficient detection of subject/script property reference, perhaps using node schema
- Improve error message when extracted input cannot be bound
- Update user guide and samples to use this syntax
- Update release notes to mention potential breaking change.

For example:

    model {
        components {
            mylib(JavaLibrarySpec) {
                targetPlatform platforms.java6
            }
        }
        tasks {
            thing(MyTask) {
                dependsOn tasks.otherTask // resolve path relative to subject
            }
            components.withType(JavaLibrarySpec).each { lib ->
                ... define a task using properties of `lib` ...
            }
        }
    }

### Test cases

- When a property cannot be resolved correct class is referenced in `MissingPropertyException`.
- Can configure a component using a sibling component as input.

## Story: DSL rule configures element of ModelMap 

- Enable idiomatic syntax
- Named, all, with type, before, after, etc


    model {
        components {
            all {
                targetPlatform platforms.java6
            }
        }
    }
    
## Story: DSL rule configures child of a structure
 
- Enable idiomatic syntax


    model {
        components {
            main {
                sources {
                    ...
                }
                binaries {
                    ...
                }
            }
        }
    }

## Story: DSL rule configures task action 

- Enable idiomatic syntax


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
    - Enable idiomatic input references
    - TBD - Implicitly use subject as input to nested rule, or require an explicit reference?
- Support idiomatic input references in nested closures
- Improve creation DSL to allow parameterized types
- Improve creation DSL to allow type to be left out when it can be inferred or there is a reasonable default
- Improve creation DSL to allow name to be left out when there is a reasonable default
- Improve configuration DSL to allow subject's view type to be declared
- Improve DSL to allow rule input's view type to be declared
- Conveniences to target subject is child with name, subject is children with type, subject is children that meet some criteria etc
- Type coercion
- Conveniences where subject is collection
- Property references instead of nesting for simple configuration
- Nice error reporting
- Etc
