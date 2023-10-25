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
            prop4 = f()
        }
    }
    prop5 = f()
}

fun f(): MyData {
    val d = MySubData("data")
    return MyData(
        d1 = d,
        d2 = d
    )
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

```kotlin
myData {
    x = 1
    y = 1
    x = 2 // error: reassigning a value is not allowed
}
```

This results in a single source of truth for a value and helps with introducing out-of-order semantics.

### External readable object and functions

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

### Out-of-order assignment

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

This can follow the semantics of Gradle `Property<T>` overloaded assignment operator, so that the assignments can be performed in any order, given that they are acyclic.

### Builder functions

It should be possible to also intruduce builder semantics for functions, so that an access- or append-function may be a receiver to a chain of other invocations that behave as in the builder pattern:

```kotlin
plugins {
    id("com.example.plugin") // append semantics, returns the new object
        .version("1.0")      // builder function
        .apply(false)        // builder function
}
```

## Implementation

### Resolution

The resolution result operates with "object origin" (see [`ObjectOrigin`](https://github.com/h0tk3y/kotlin-static-object-notation/blob/master/src/main/kotlin/com/h0tk3y/kotlin/staticObjectNotation/analysis/ResolutionOutput.kt#L14), which tells
where an object comes from: a constant, a local variable, a receiver of a configuring lambda, a function invocation etc.

Then, the resolver collects the sets of:
* _assignments_, telling which object (by origin) gets its property assigned to what value (again, by object origin),
* _additions_, telling what functions with _adding_ semantics add their invocation results to what containers.

### Schema extensibility

The context of the script is a schema that contains the allowed types (along with their allowed functions and 
properties), allowed top-level functions and properties, the default import, and the script top-level receiver type.
See [`AnalysisSchema`](https://github.com/h0tk3y/kotlin-static-object-notation/blob/master/src/main/kotlin/com/h0tk3y/kotlin/staticObjectNotation/analysis/AnalysisSchema.kt#L5).

Functions in the schema, as well as their parameters, must specify their semantics ("adding", "configuring", etc.).

The evaluation engine also needs the information about the way to transform the 

## TODO

- Map notation?
- Varargs
- Can a function produce an object that references the receiver or some of its pieces?
- Out-of-scope for now: semantics for  “freeform” data that does not follow any schema?
