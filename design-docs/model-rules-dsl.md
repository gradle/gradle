# Feature: basic usability

## Story: Transformed DSL is enabled by default

- Allow transformed DSL rules to access project and script, for now.
- Update user guide to mention syntax.
- Update release notes to mention potential breaking change.

## Story: DSL rule references input using path expression syntax

- Replace `$('path')` with `$.path`

## Story: DSL rule references input relative to subject 

- Allow a closure parameter to be declared, this lvar can be used to reference inputs
- Improve error message when extracted input cannot be bound
- Update user guide and samples to use this syntax
- Update release notes to mention potential breaking change.

For example:

    model { m ->
        components { c ->
            mylib(JavaLibrarySpec) {
                targetPlatform m.platforms.java6
            }
            test {
                targetPlatform c.test.targetPlatform
            }
        }
        tasks { t ->
            thing(MyTask) {
                dependsOn t.otherTask // resolve path relative to subject
            }
            components.withType(JavaLibrarySpec).each { lib ->
                ... define a task using properties of `lib` ...
            }
        }
    }

### Test cases

- Can configure a component using a sibling component as input.

## Story: DSL rule configures element of ModelMap 

- Allow input references using subject parameter
- Named, all, with type, before, after, etc


    model {
        components {
            all {
                targetPlatform platforms.java6
            }
        }
    }
    
## Story: DSL rule configures child of a structure
 
- Allow input references using subject parameter


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

- Allow input references using subject parameter


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
- Conveniences to target cases where subject is child with name, subject is children with type, subject is children that meet some criteria etc
- Type coercion
- Conveniences where subject is collection
- Property references instead of nesting for simple configuration
- Nice error reporting
- Etc
