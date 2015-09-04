This spec defines some work to add some basic reporting on the configuration model, to give insight into:

- What is available in the model?
- What are certain values in the model set to?
- Where did a particular model element or value come from?
- What are the dependencies between the part of the model?

# Story: Model report shows display value for model elements with no children

Change the `model` report to show some display value for model elements with no children, eg the result of
`toString()` on its value.

## Test Coverage
- empty project
- polyglot project
- simple project with `@Managed` objects with primitive data types.

# Story: Model report shows type information for model elements


Change the `model` report to show some information about the views that are available for each element.

- Component model
- `@Managed` types

> get the 1st projection of the nodes model adapter

# Story: Model report shows details of rule that created a model element

Change the `model` report to show the descriptor of the creator rule for each element.

Possibly don't show this when same as the parent element (see how it looks)

Possibly add a 'show details' command-line option to enable this.


`org.gradle.model.internal.core.ModelCreator.getDescriptor()`


# Story: Model report shows details of rules that affected a model element

Change the `model` report to show the descriptor of those rules that affected each element.

Will need to collect this in the model nodes.

Biggest one so far
- Investigate `org.gradle.model.internal.registry.RuleBindings`
- Order is important
- some of them are internal/mechanical?

# Story: Model report shows more concise name for rule source method rules

- Change descriptor for such rules to use simple type name, and no parameters
- Ensure overloading of rule methods is forbidden (so that dropping the params doesn't make it ambiguous)
- Ensure that rules methods from enclosed classes include the top level class in the description (e.g. `ComponentModelPlugin$Rules`)

# Story: DSL based rules include relative path to script, from project root
When we toggle `org.gradle.model.dsl` we provide better descriptors for DSL rules.
Currently, we include the absolute path to the script containing the rule.
- This should be relative to the project root - the path to the script defining the rule, relative to the “root project dir” (i.e. `Project.getRootDir()`).
- Must include the relative path, line number and column number to 'applied' scripts. i.e. `apply from: '../../../someScript.gradle'`

### Test scenarios
 - model dsl from the main build script but is named something other than `build.gradle`
 - apply from a script somewhere inside the project's root dir
 - apply from a script via http
 - apply from a script somewhere outside the project's root dir

# Story: Rule binding failure errors use consistent terminology

See ModelRuleBindingFailureIntegrationTest.

Two problems:

1. Use of `mutable` & `immutable`: should be `subject`, `inputs`
1. Use of `+` for “did bind” and `-` for “did not bind” is too subtle - needs to be clearer what is happening

The of error messages arising from the rules with unbound subjects or inputs is as follows:
```
The following model rules could not be applied due to unsatisfied dependencies:
  MyPlugin$Rules#mutateThing2
    Subject:
      foo.nar MyPlugin$MyThing2 (parameter 1) [UNBOUND]
        suggestions: foo.bar
    Inputs:
      foo.narre MyPlugin$MyThing3 (parameter 2) [UNBOUND]
        suggestions: foo.bar. some
      foo.narre MyPlugin$MyThing4 (parameter 3)

[UNBOUND] - indicates that the subject or input could not be found (i.e. the reference could not be bound)
see: http://linktodocs
```

# Backlog

# Story: Model report shows hidden nodes

Add a 'show hidden elements' command-line option to show hidden nodes.

`--all`

# Story: Add report that shows dependencies between model elements

Add a new report that shows the dependency graph between model elements.

# Story: Report shows details about which plugin defined a rule

Improve the display value for rules defined in plugins, to show the plugin id instead of the detailed
method descriptor.

Perhaps use a 'show details' command-line option to enable display of the method descriptor.
