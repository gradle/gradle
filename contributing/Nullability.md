# Nullability Guide

This page describes the approach to nullability of types in the Gradle Build Tool codebase.[^1]

Eventually, we want to make all production Java source be [non-null by default](#non-null-by-default-initiative), but we are not there yet.

Starting from Gradle 9.0.0, we use [JSpecify](https://jspecify.dev/) annotations (`org.jspecify.annotations.*`) with [NullAway](https://github.com/uber/NullAway) static analysis enforcement.

We previously used [JSR-305](https://jcp.org/en/jsr/detail?id=305) annotations (`javax.annotation.*`), and continue to use them in Groovy code due to some compatibility issues.

For more details, see the [JSpecify user guide](https://jspecify.dev/docs/user-guide/) and the [NullAway wiki](https://github.com/uber/NullAway/wiki).

## Static analysis with NullAway

Gradle uses [NullAway](https://github.com/uber/NullAway) to enforce null-safety at compile time.
Unsafe code becomes a compilation error.

During the transitional period, NullAway checking is enabled on a per-project basis.
Project's dependencies must all be null-checked for it to be able to opt into NullAway checks itself.

You can enable NullAway checks with the following snippet in the project's `build.gradle.kts`:
```kotlin
errorprone {
    nullawayEnabled = true
}
```

For practical reasons, NullAway doesn't rely on `@NullMarked` annotations in Gradle code but considers all code in `org.gradle.*` packages to be non-null by default.
Combined with the dependency requirement above, it reduces churn on our way to the goal of having everything checked.

## How to null-annotate code

- Use `@org.jspecify.annotations.NullMarked` annotation for packages and classes
- Use `@org.jspecify.annotations.Nullable` to annotate types where required
- Do not use `@org.jspecify.annotations.NonNull`, except when constraining generic type parameters (see below)

### Annotate packages

The highest unit where Java allows to apply annotations is packages.
By annotating a package in its `package-info.java` file we make this package non-null by default.

```java
// package-info.java file

@NullMarked
package org.gradle.internal.metaobject;

import org.jspecify.annotations.NullMarked;
```

**All new packages must be annotated**. This is enforced by an ArchUnit rule.

### Annotate classes and interfaces

Only when annotating a whole package is not desirable, annotate each individual class.

```java
@NullMarked
public class VersionCatalogGenerator { /* ... */ }
```

### Annotate fields, methods and constructors

Fields, method parameters and return types should only be annotated with `@Nullable`.
Instead of annotating these elements with a non-null annotation, annotate the enclosing class or package with `@NullMarked`.

```java
@NullMarked
public class MyClass {
//  (1)
    @Nullable     /*  (2)                   (3)      */
    public String run(@Nullable Integer p1, String p2) {/*...*/}

    // (4)
    @Nullable
    private String field;
}
```

1. Method returning a nullable `String`
2. A nullable `Integer` parameter
3. A non-nullable `String` parameter
4. A field holding a nullable `String`

We prefer to place the field and return type annotations on a separate line.
Even though [JSpecify suggests](https://jspecify.dev/docs/user-guide/#nullable) to put it next to the return type like `public @Nullable String run()`,
our style has no semantic difference and is already widely used in the project.
For some complex types, like arrays, check the "Type use annotations" section below, as they may **require** different placement to achieve correct semantics.

Equivalent Kotlin code:

```kotlin
class MyClass {
    fun myMethod(param1: Int?, param2: String): String? { /* ... */ }

    var field: String? = null
}
```

### Do not annotate local variables

Local variables should **not** have explicit nullability annotations.
IDEA complains about it, and it cannot be disabled, so having those adds visual clutter and increases warning fatigue.
NullAway and IDEA infer local variable nullability from assignments and control flow.

### Using type use annotations

Unlike JSR-305 nullability annotations, JSpecify annotations are "type-use".
Technically, they aren't applied to methods, parameters, or fields but to their _types_.
This also allows using them in generic types.

There are a few corner cases when using type use annotations with some language constructs.

#### Nested types

When annotating qualified nested types, like `Foo.Bar`, the annotation has to be placed before the name of
the inner type - `Foo.@Nullable Bar`.
The compiler will shout at you otherwise.
This is an acceptable exception to the "annotation on its own line" rule:
```java
class Foo {
    static class Bar {}

    private Foo.@Nullable Bar bar = null;

    public Foo.@Nullable Bar maybeGetBar() { return bar; }
}
```

#### Arrays

There are two cases for arrays:

1. The array itself can be null
2. The array can contain null elements

For when the array itself can be null we add the annotation before the `[]`:

```java
public void run(Object @Nullable [] arguments) { /* ... */ }
```

If this method is overridden in Kotlin, it should have the following signature:

```kotlin
fun run(arguments: Array<Any>?)
```

For when the array can contain null elements we add the annotation before the type of elements:

```java
public void run(@Nullable Object[] arguments) { /* ... */ }
```

If this method is overridden in Kotlin, it should have the following signature:

```kotlin
fun run(arguments: Array<Any?>)
```

Both can be combined to express that an array parameter can be a null array or an array with null elements:

```java
public void run(@Nullable Object @Nullable [] arguments) { /* ... */ }
```

If this method is overridden in Kotlin, it should have the following signature:

```kotlin
fun run(arguments: Array<Any?>?)
```

For more details, see JSpecify's [guide on annotating arrays](https://jspecify.dev/docs/user-guide/#type-use-annotation-syntax).

#### Varargs

Varargs are similar to arrays.
When some arguments can be null, we add the annotation before the type of elements:

```java
public void run(String name, @Nullable Object... arguments)
```

If this method is overridden in Kotlin, it should have the following signature:

```kotlin
fun run(name: String, vararg arguments: Any?)
```

When no arguments are passed the parameter array will be non-null and empty in the body of the Java method or Kotlin function.
There's no need to annotate the parameter itself as nullable.

### Generic types and type parameters

Type parameters of generic types may accept or forbid nullable types, like `@Nullable String`.
By default, nullable types are forbidden.

For type variables that should accept nullable types, use `@Nullable` in the `extends` bound:

```java
@NullMarked
public class Container<T extends @Nullable Object> {
    public T getOrDefault(T defaultValue) {
        // Can handle both Container<String> and Container<@Nullable String>
    }
}
```

Without the `@Nullable` in the bound, you cannot use nullable type arguments:
- With `<T>` or `<T extends Object>`: `Container<@Nullable String>` is invalid
- With `<T extends @Nullable Object>`: `Container<@Nullable String>` is valid

Note that Kotlin uses an opposite convention: generic type parameters accept nullable types by default.
```kotlin
fun <T> convert(foo: T): T { /* ... */ }  // Java: <T extends @Nullable Object> T convert(T foo)

fun <T : Any> convert(foo: T): T { /* ... */ }  // Java: <T> T convert(T foo)
```

When using the type parameter, it is possible to override its nullability:
```java
@NullMarked
class Container<T> {
  T returnsNonNull() { /* the return value is non-nullable, because T doesn't accept nullable types */}
  @Nullable T returnsNullable() { /* the return value is nullable T, because it is explicitly defined */}
}

@NullMarked
class NullableContainer<T extends @Nullable Object> {
  @NonNull T returnsNonNull() { /* the return value is non-nullable T, because it is explicitly defined */}
  @Nullable T returnsNullable() { /* the return value is nullable T, because it is explicitly defined */}
  T returnsSameType() { /* the nullability depends on the type argument, additional restrictions apply to the implementation */}
}
```

Using `@NonNull` to define "definitely non-null" variation of the type parameter is the only allowed use of this annotation.

When implementing methods that work with a type parameter `T extends @Nullable Object`, using values of type `T` is restricted:
- You must null-check values of this type before using as if it were `@Nullable`.
- You cannot assign `null` to variables of this type, including returning `null` from the method that returns `T`.
- Very rarely you may need to use `Cast.unsafeStripNullable` to adapt the value, see its javadoc for further information.

For more details, see the [JSpecify user guide on generics](https://jspecify.dev/docs/user-guide/#generics).

## Best practices

### Keep null checks at public API boundaries

- Never remove null checks despite (lack of) nullability annotations (users may not have annotations enforced)
- Avoid marking parameters `@Nullable` solely to throw `NullPointerException` when you get a null
- Do not use `@Contract` on public API

### Avoid PolyNull methods

Methods where nullability of the return value depends on nullability of arguments should be avoided.
An example could be the `@Nullable T Cast.uncheckedCast(@Nullable Object arg)` method.
It returns `null` if and only if its argument is `null`, therefore, when used with a non-nullable argument you may expect a non-nullable result.
However, this cannot be expressed with type annotations alone, and NullAway will require you to check the result for null.

Instead:

1. When nullable arguments are rare, and handling them is trivial, keep the parameter non-nullable and have the check at the call site
2. Create separate named overloads for nullable and non-nullable argument (e.g., `T cast(Object foo)` and `@Nullable T castNullable(@Nullable Object foo)`)
3. When annotating widely used existing code, use `@org.jetbrains.annotations.Contract`

#### Using `@Contract`

NullAway understands a [subset](https://github.com/uber/NullAway/wiki/Supported-Annotations#contracts) of JetBrains' `@Contract` annotations. Some useful expressions are:
```java
// Non-nullable input guarantees non-null output.
// Implication: when a non-nullable input is supplied, there is no need to check the result for null.
@Contract("!null -> !null")
@Nullable String foo(@Nullable String arg) { /* ... */}

// Method throws on null input.
// Implication: no need for an extra null check of the argument after calling the method
@Contract("null -> fail")
void myCheckNotNull(@Nullable foo) { /* ... */ }

// Method returns false on null input.
// Implication: no need for an extra null check of the argument in the positive if branch
@Contract("null -> false")
boolean isNonNull(@Nullable foo) { /* ... */}
```

NullAway performs a basic verification of these statements.

`@Contract` has no meaning for Kotlin code, so the return type annotations should be "pessimistic": use `@Nullable` if a method can ever return null.

## Suppressing NullAway errors

NullAway errors should generally be fixed, not suppressed.
Often, the code can be restructured to avoid the failed null check.
However, in certain cases NullAway may not be able to infer the type correctly.
Sometimes, adding explicit type arguments or extracting parts of the code into an explicitly typed variable helps.

When this doesn't help and suppression is necessary:

1. **Use `Objects.requireNonNull()`** - For cases where you know a value is non-null but NullAway cannot verify it. With a small performance penalty, we get fail-fast behavior at runtime:
    ```java
    if (map.contains("key")) {
      String value = Objects.requireNonNull(
          map.get("key"),
          "Where did my key go?"
      );
      // ...
    }
    ```
2. **Use  [`Cast.unsafeStripNullable`](../platforms/core-runtime/stdlib-java-extensions/src/main/java/org/gradle/internal/Cast.java)** - For cases where you know the value conforms to the type but
   NullAway cannot infer, mostly happens with creative generic usage. Use sparingly.
3. **Use `@SuppressWarnings("NullAway")`** - Only as a last resort with a comment explaining why it is safe and with the minimal possible scope.

## Known quirks in nullability tools

### NullAway
- [[NullAway#681](https://github.com/uber/NullAway/issues/681)] NullAway reports `AtomicReference<T>.get()` as nullable, regardless of the actual nullability of `T`.
- [[many issues](https://github.com/uber/NullAway/issues?q=sort%3Aupdated-desc%20is%3Aissue%20is%3Aopen%20label%3Ajspecify)] NullAway cannot always infer nullability of the generic type arguments. For example, this emits an error for `compare` not accepting nulls, even though the receiver is
  `Ordering<@Nullable String>`:
  ```java
    return Ordering.natural().nullsLast().compare(nullableA, nullableB);
  ```
  This can be rewritten with an explicit type that compiles without an error:
  ```java
    Ordering<@Nullable Comparable<String>> order = Ordering.natural().nullsLast();
    return order.compare(nullableA, nullableB);
  ```
- [[JDK-8341779](https://bugs.java.com/bugdatabase/view_bug?bug_id=8341779)] Sometimes NullAway does not recognize `@Nullable` annotations on method parameters.

### IDEA
- IDEA may emit false positives in non-`@NullMarked` code, because unlike NullAway it relies on these annotations.

- [[IDEA-383015](https://youtrack.jetbrains.com/issue/IDEA-383015)] IDEA considers `Optional.ofNullable` to return `Optional<@Nullable T>`, and complains about converting it to `Optional<T>`.
  For example, IDEA emits a warning for:
  ```java
    @Nullable String foo;
    Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }
  ```
- Sometimes IDEA considers values of generic type parameter `T` nullable even if `T` is non-nullable in the context.
  This mostly happens when there is a supertype with a nullable type bound on the parameter.
  ```java
      interface Foo<T extends @Nullable Object> {
        void accept(T t);
    }

    static class Bar<T> implements Foo<T> {
        @Override
        public void accept(T t) {
            // Method invocation 'toString' may produce 'NullPointerException' - but T is non-nullable!
            System.out.println(t.toString());
        }
    }
  ```

## Non-null by default initiative

### Context

The majority of the production code of the Build Tool is written in Java.
For historical reasons, the Java language treats every non-primitive type as "nullable".
In practice, however, production code tends to be non-nullable, meaning that handling null values is not expected.

The Java language had an [attempt](https://jcp.org/en/jsr/detail?id=305) of addressing this with official annotations, but it was never complete.
But since the problem was still present, the ecosystem tried to continue solving it with annotations in the [JSpecify](https://jspecify.dev/) project.

Some major open-source projects such as Spring Boot migrated their codebase to be non-null by default.
This means that any type seen in the codebase not directly annotated by `@Nullable` is considered to be non-nullable.
In Spring Boot this applies to method parameters, method return types, and even field types.

The Gradle Build Tool public API were explicitly null-annotated since some time ago.
However, the rest of the production code is not explicitly annotated and is null-by-default (aka vanilla Java).

The Gradle Kotlin DSL [enables](https://docs.gradle.org/current/userguide/kotlin_dsl.html#sec:kotlin_compiler_arguments) the Kotlin compiler [Java interoperability](https://kotlinlang.org/docs/java-interop.html#null-safety-and-platform-types) to follow nullability annotations in a [strict](https://kotlinlang.org/docs/java-interop.html#nullability-annotations) manner.

### Vision

> **Everything is non-null by default**
>
> We want the full production Java codebase to be non-null by default.
> We also want each place where a null value is possible to be explicitly annotated, be it a parameter, return type or field.

What remains to be done is:

1. ensure all internal packages/classes are null marked
2. automatically null mark all packages without the need to create a package-info file

[^1]: This was decided by [ADR](../architecture/standards/0008-use-nullaway.md), see it for more details and rationale.
