
# Feature: basic usability

## Story: Transformed DSL is enabled by default

- Allow transformed DSL rules to access project and script, for now.

## Story: Simplified syntax to reference model elements as input of DSL rule

- Add an alternative to `$('path')` that is closer to idiomatic Groovy

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
        }
    }

## Story: Deprecate access to project and script from DSL rules

- Warn when the project or script are used from the transformed DSL.

## Story: Syntax to apply DSL rules to multiple projects

- Add block to `settings.gradle` to allow this.

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

# Backlog

- Add DSL to attach rules to children of current subject
- Improve creation DSL to allow parameterized types
- Improve creation DSL to allow type to be left out when it can be inferred or there is a reasonable default
- Improve configuration DSL to allow subject's view type to be declared
- Conveniences to target subject is child with name, subject is children with type, subject is children that meet some criteria etc
- Type coercion
- Conveniences where subject is collection
- Nice error reporting
- Etc
