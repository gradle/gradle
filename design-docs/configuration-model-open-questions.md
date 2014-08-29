## Decoration of model objects

Given I declare a model object of some type T:

- Where and how do we present a proxy for that object to rules?
- How can I declare an implementation type (if supported) or internal and public contract type(s) to present the object as?
- How do I do this from the DSL? A plugin?
- Should share approach and implementation with whatever proxying the tooling API does

## Model object relationships

- How do I define a graph of model objects?
- How can different rules contribute to the graph?
- Given that I ask for an object, how do I declare that I want the graph (or sub-graph) that is reachable from that
objects, vs (say) just the properties of that object? Same question for inputs and outputs.
- What does the DSL look like?

## Model identity

Dealing with model objects that appear in multiple locations in the namespace, or can be reached in various
different criteria (eg path or type) from the DSL or a plugin.

## Model views

- How do I define views for a given object or class of objects?
- How can I extend an object to add more stuff to it?

# Mock ups

## Some notes about rules - Adam

A rule:

- Declares which objects are the input of rule.
- Declares which objects are the output of the rule.
- Declares the view it requires of each object.
- Declares some function to execute to produce outputs from some given input.
- The function is invoked once for each set of input objects that meet the declared criteria.

Considerations:

- An input may also be an output, for example when the rule mutates an object.
- There may be relationships between the inputs and outputs, for example to configure a native test suite I want to
use the component under test as input to determine the variants to build for the test suite.
- A DSL or API should declare as much as possible statically.
- Should take advantage of static information when selecting objects.
- A 'function' here may be user provided logic or it might be some implicit behaviour provided by the runtime. In other words,
a function is some work that is performed, for which we have varying degrees of knowledge of what it does: is it 'fast' or 'slow'?
is it deterministic? do we know all its inputs?

Types of rule actions:

- Declare some top level object (in some scope).
- Declare some object as the value of a single-valued property of some other object.
- Declare some object in a multi-valued property of some other object.
- Apply conventions to an object before it is configured
- Configure some object.
- Apply conventions to an object after it is configured.
- Validate some object before it is used as an input.
- Extend an object to attach properties.
- Specialize an object.
- Declare meta-data about some type.
- Declare further rules.

We can consider the outer scope as simply a multi-valued property on some root object, which means that declaring a top level object can
be treated the same way as attaching some object to some container. We can also consider the definition of rules as declaring rules in some
container object.
Given this, each of these rules above mutate zero or more of its inputs.
This means that there is no need to express criteria to select an output from the namespace. This is implicit
from the criteria for the inputs. This has implications for the DSL.

A rule can be considered as a template, which is some criteria to select input objects and a function. From this template zero or more actions are created,
one for each set of matching input objects. Each action is a function bound to a particular set of inputs.

Under this, a `Task` is an action.

A rule generally can be transformed into a rule with broader criteria whose action defines a further rule with the specific criteria.
This has implications for the DSL, as some criteria can be expressed statically in method signatures and some additional criteria can be expressed as code.

## Component model

There are several properties of a component that define 'what' the component is:

- The 'entry points' through which the component may be invoked:
    - API
    - Main method
    - J2EE servlet application
    - Play application
    - JNI method implementations
    - Gradle plugin
- The source languages that the component is built from. Or, more generally, the type of inputs the component is built from.

For example:

- A component that provides an API is-a library
- A component built from a JVM language is-a JVM component (and so runs on the JVM)
- A component built from a native language is-a native component (and runs on the native C runtime)
- A component that provides a main method is-a command-line application
- A component that provides a Web servlet is-a Web application
- A library that is built from Java is-a Java library
- A library that is built from Java and Scala is-a JVM library
- A library built from C and C++ is-a native library
- A component that provides an API and a main method is both a library and a command-line application.

Not every combination of entry points and source languages makes sense, but many do.

The DSL should be able to express any legal combination, plus some way to conveniently express common combinations. For example:

- A Java library -> built from Java and provides an API
- A C library -> built from C and provides an API
- A C executable -> built from C and provides a main method

A 3rd fundamental property of a component is the set of target runtimes. A runtime, or at least the type of runtime, can often be inferred from either the source
languages or the entry point.

### Option 1 - Static types

Each combination is bound together in a Java interface. This is more or less what we are doing now:

    model {
        jvm {
            libraries {
                mylib {
                    // Type is implicit given the context: This is a Jvm Library built from some implicit source languages
                }
            }
        }
    }

Or:

    model {
        components {
            lib1(JavaLibrary) {
                // The explicit type parameter means that this is-a Jvm library built from Java source
            }
            lib2(JvmLibrary) {
                // This is-a Jvm library built from some implicit source languages
            }
        }
    }

Or, some syntax variations:

    model {
        mylib(JavaLibrary) {
            // This is implicitly a top-level component
        }
    }

    model {
        // Some syntax variants
        myLib = component(JavaLibrary) { ... }
        component('myLib', JavaLibrary) { ... }
        component myLib(JavaLibrary) { ... }
        JavaLibrary myLib = { ... }

        // Or, given we know the component types, can generate factory methods
        myLib = javaLibrary { ... }
        javaLibrary(myLib) { ... }
        javaLibrary myLib { ... }
    }

### Option 2 - Mix-in static types

Certain capabilities are mixed-in when the component is defined. This is really just a more general case of the above:

    model {
        components {
            lib1(Library, JvmComponent) {
                // Mix two capabilities together: this is a JVM library with implicit languages
            }
            app1(CommandLineApplication, NativeComponent) {
                // This is a native executable
            }
            comp2(CommandLineApplication, Library, NativeComponent) {
                // This is a both a native command-line application and a native library
                // It would produce executables, static libs and shared libs as output
            }
            lib2(JavaLibrary) {
                // Can still bundle stuff up together into a specific combination
            }
        }
    }

Or, with a different syntax:

    model {
        lib1 {
            // These statements have to be first in the block
            isA Library
            isA ServletWebApplication

            // Other configuration
        }

        // Or
        lib2 {
            provides JvmApi
            provides ServletWebApplication

            // Other configuration
        }
    }

### Option 3 - Infer type from certain property values

First determine the source languages and entry points of a component, then infer the types of the component. The types would
determine which additional properties are available and the views which the component can be presented as:

    model {
        components {
            mylib {
                // This is a JVM library built from Java and Scala

                // This is an implicit code block of 'static' facts
                // These statements must be assignment statements whose RHS references immutable things only (constants, other model elements and factory methods that return immutable things)
                provides = api
                source = langs.java, langs.scala
                targetPlatforms = platforms.scala("2.10"), platforms.scala("2.11")

                // Additional configuration. This must be declared after the above. Can contain arbitrary code
                // The delegate for this code is-a JavaComponent and is-a ScalaComponent and is-a JvmLibrary
                source { ... }
            }
            lib2 {
                // Type could be implicit based on how various rules configure the properties
            }
        }
    }

Or:

    model {
        // Define then configure
        myLib = component {
            // Can only express entry points and language properties here
            provides = api
            source = lang.java
        }
        myLib {
            // Other configuration goes here
        }

        // Infer the type from the static declarations
        component myLib
        myLib.source = lang.java
        myLib.targetPlatforms = platforms.java("1.6")
        myLib.source.java.sourceLanguage = lang.java("1.6")
        myLib { ... }
    }

Or:

    model {
        // Declare certain static facts about a component
        library myLib
        commandLineApplication myLib
        myLib.source lang.java, lang.scala
    }

For these options, rules would be able to receive and mutate some 'definition' view to determine the types of an object. These types would be
mixed together and the properties made available for mutation by other rules. The properties of the definition view essentially form the
constructor parameters of the object.

There would still be static types that represent certain capabilities, and these would be available for use in rules.

### Option 4 - Infer type from code structure:

    model {
        mylib {
            // 'api' block means this provides an API
            api { ... configure the API ... }
            // 'source.java' block means this is built from Java
            source {
                java { ... configure Java source ... }
            }
        }

        // Would have to state these things even if not required
        lib1 {
            api
            source.java
        }
        lib2 {
            executable
            source.c
            source.windowsResources
        }
    }

## Add more stuff here
