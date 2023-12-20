# Kotlin Static Object Notation

This repository is a proof-of-concept static interpreter of _restricted_ Kotlin Script, which
allows extracting data from it without compiling and running the code.

Here is a script example (some features are not yet supported):

```kotlin
import com.example.myObj // could be an implicit import in the schema
import com.example.MyData

myObj {
    item("zero") // "add" semantics
    item("one")
    ext1 { // "access" semantics
        prop2 = "two"
        obj3 = myObj(3) {
            prop4 = MyData()
        }
    }
    prop5 = MyData()
}
```

## Design

Unlike executable KTS, the semantics of such a script is an object graph representation of the data.

### “Append” vs “access” semantics

Some functions should produce a new object on each invocation. This object is somehow added to the receiver container:

```kotlin
// creates a new item:
item("foo", "bar")

// the results can still be assigned for reuse purposes:
val baz = item("baz", "qux") 
objItem("foo") {
    x = baz
}
```

Other functions should idempotently access an object and configure it, like:

```kotlin
f {
    x = "foo"
}
f { // configures the same object
    y = "bar"
}
```

### Reassigning values should be forbidden

⚠️ This feature is not yet implemented

```kotlin
myData {
    x = 1
    y = 1
    x = 2 // error: reassigning a value is not allowed
}
```

This results in a single source of truth for a value and helps with introducing out-of-order semantics.

### External readable objects and functions

A schema should be able to provide objects that a script can read and invoke functions on them.

```kotlin
import com.example.externalObj
import com.example.systemProperty

obj {
    prop = externalObj.someValue // defined in the schema
    otherProp = externalObj.file("abc.txt")
    oneMoreProp = systemProperty("com.example.prop")
}
```

This should be useful for grouping and injecting data-related APIs.

### Builder functions

It should be possible to also intruduce builder semantics for functions, so that an access- or append-function may be a receiver to a chain of other invocations that behave as in the builder pattern:

```kotlin
plugins {
    id("com.example.plugin") // append semantics, returns the new object
        .version("1.0")      // builder function
        .apply(false)        // builder function
}
```

## Features to consider

### Data functions

⚠️ This feature is not yet implemented

It might be possible to allow functions as reusable pieces of data that can be applied to multiple objects. Function bodies will then have to follow the general language limitations. Example:

```kotlin
myData {
    f()
}

otherMyDataProperty.f()

fun MyData.f(arg: String): MyData {
    myDataStringProperty = arg
    myDataObjProperty = MySubData(arg)
}
```

### Out-of-order assignment

⚠️ This feature is not yet implemented

```kotlin
objA {
    foo = objB.bar // eventually evaluates to "baz"
}
objB {
    bar = objC.baz
}
objC {
    baz = "baz"
}
```

These assignments can be performed in any order, given that they are acyclic.
This can follow the semantics of Gradle `Property<T>` overloaded assignment operator or rely on some other library providing the assign operator implementation with lazy evaluation semantics.

## Implementation

### Resolution

The resolution result operates with "object origin" (see [`ObjectOrigin`](https://github.com/h0tk3y/kotlin-static-object-notation/blob/master/src/main/kotlin/com/h0tk3y/kotlin/staticObjectNotation/analysis/ResolutionOutput.kt#L26), which tells
where an object comes from: a constant, a local variable, a receiver of a configuring lambda, a function invocation etc.

Then, the resolver collects the sets of:
* _assignments_, telling which object (by origin) gets its property assigned to what value (again, by object origin),
* _additions_, telling what functions with _adding_ semantics add their invocation results to what containers.

### Schema extensibility

The context of the script is a schema that contains the allowed types (along with their allowed functions and 
properties), allowed top-level functions and properties, the default import, and the script top-level receiver type.
See [`AnalysisSchema`](https://github.com/h0tk3y/kotlin-static-object-notation/blob/master/src/main/kotlin/com/h0tk3y/kotlin/staticObjectNotation/analysis/AnalysisSchema.kt#L5).

Functions in the schema, as well as their parameters, must specify their semantics ("adding", "configuring", etc.).

There is a built-in schema builder based on Kotlin reflection, see [`schemaFromTypes`](https://github.com/h0tk3y/kotlin-static-object-notation/blob/master/src/main/kotlin/com/h0tk3y/kotlin/staticObjectNotation/schemaBuilder/schemaFromTypes.kt#L26).

The schema builder can be extended:
* [`ConfigureLambdaHandler`](https://github.com/h0tk3y/kotlin-static-object-notation/blob/master/src/main/kotlin/com/h0tk3y/kotlin/staticObjectNotation/schemaBuilder/ConfigureLambdaHandler.kt) to handle custom functional types that should allow lambdas passed at call sites, which is also used in mapping results back to JVM objects;
* [`DataClassSchemaProducer`](https://github.com/h0tk3y/kotlin-static-object-notation/blob/master/src/main/kotlin/com/h0tk3y/kotlin/staticObjectNotation/schemaBuilder/DataClassSchemaProducer.kt) to produce properties for types and filter (not produce yet) functions.

## TODO

- Map notation?
- Varargs
- Define a subset of Kotlin stdlib for some commonly used operations
- Can a function produce an object that references the receiver or some of its pieces?
- Out-of-scope for now: semantics for  “freeform” data that does not follow any schema?
