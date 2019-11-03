---
type: doc
layout: reference
category: Basics
title: Coding Conventions
---

# Coding Conventions

This page contains the current coding style for the Kotlin language.

* [Source code organization](#source-code-organization)
* [Naming rules](#naming-rules)
* [Formatting](#formatting)
* [Documentation comments](#documentation-comments)
* [Avoiding redundant constructs](#avoiding-redundant-constructs)
* [Idiomatic use of language features](#idiomatic-use-of-language-features)
* [Coding conventions for libraries](#coding-conventions-for-libraries)

### Applying the style guide

To configure the IntelliJ formatter according to this style guide, please install Kotlin plugin version
1.2.20 or newer, go to __Settings | Editor | Code Style | Kotlin__, click __Set from...__ link in the upper
right corner, and select __Predefined style | Kotlin style guide__ from the menu.

To verify that your code is formatted according to the style guide, go to the inspection settings and enable
the __Kotlin | Style issues | File is not formatted according to project settings__ inspection. Additional
inspections that verify other issues described in the style guide (such as naming conventions) are enabled by default.

## Source code organization

### Directory structure

In pure Kotlin projects, the recommended directory structure follows the package structure with
the common root package omitted. For example, if all the code in the project is in the `org.example.kotlin` package and its
subpackages, files with the `org.example.kotlin` package should be placed directly under the source root, and
files in `org.example.kotlin.network.socket` should be in the `network/socket` subdirectory of the source root.

> **On the JVM**: In projects where Kotlin is used together with Java, Kotlin source files should reside in the same source root as the Java source files,
and follow the same directory structure: each file should be stored in the directory corresponding to each package
statement.

### Source file names

If a Kotlin file contains a single class (potentially with related top-level declarations), its name should be the same
as the name of the class, with the .kt extension appended. If a file contains multiple classes, or only top-level declarations,
choose a name describing what the file contains, and name the file accordingly.
Use the [camel case](https://en.wikipedia.org/wiki/Camel_case) with an uppercase first letter (for example, `ProcessDeclarations.kt`).

The name of the file should describe what the code in the file does. Therefore, you should avoid using meaningless
words such as "Util" in file names.

### Source file organization

Placing multiple declarations (classes, top-level functions or properties) in the same Kotlin source file is encouraged
as long as these declarations are closely related to each other semantically and the file size remains reasonable
(not exceeding a few hundred lines).

In particular, when defining extension functions for a class which are relevant for all clients of this class,
put them in the same file where the class itself is defined. When defining extension functions that make sense 
only for a specific client, put them next to the code of that client. Do not create files just to hold 
"all extensions of Foo".

### Class layout

Generally, the contents of a class is sorted in the following order:

- Property declarations and initializer blocks
- Secondary constructors
- Method declarations
- Companion object

Do not sort the method declarations alphabetically or by visibility, and do not separate regular methods
from extension methods. Instead, put related stuff together, so that someone reading the class from top to bottom can 
follow the logic of what's happening. Choose an order (either higher-level stuff first, or vice versa) and stick to it.

Put nested classes next to the code that uses those classes. If the classes are intended to be used externally and aren't
referenced inside the class, put them in the end, after the companion object.

### Interface implementation layout

When implementing an interface, keep the implementing members in the same order as members of the interface (if necessary,
interspersed with additional private methods used for the implementation)

### Overload layout

Always put overloads next to each other in a class.

## Naming rules

Package and class naming rules in Kotlin are quite simple:

* Names of packages are always lower case and do not use underscores (`org.example.project`). Using multi-word
names is generally discouraged, but if you do need to use multiple words, you can either simply concatenate them together
or use the camel case (`org.example.myProject`).

* Names of classes and objects start with an upper case letter and use the camel case:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
open class DeclarationProcessor { /*...*/ }

object EmptyDeclarationProcessor : DeclarationProcessor() { /*...*/ }
```

</div>

### Function names
 
Names of functions, properties and local variables start with a lower case letter and use the camel case and no underscores:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
fun processDeclarations() { /*...*/ }
var declarationCount = 1
```

</div>

Exception: factory functions used to create instances of classes can have the same name as the class being created:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
abstract class Foo { /*...*/ }

class FooImpl : Foo { /*...*/ }

fun FooImpl(): Foo { return FooImpl() }
```
</div>

#### Names for test methods

In tests (and **only** in tests), it's acceptable to use method names with spaces enclosed in backticks.
(Note that such method names are currently not supported by the Android runtime.) Underscores in method names are
also allowed in test code.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
class MyTestCase {
     @Test fun `ensure everything works`() { /*...*/ }
     
     @Test fun ensureEverythingWorks_onAndroid() { /*...*/ }
}
```

</div>

### Property names

Names of constants (properties marked with `const`, or top-level or object `val` properties with no custom `get` function
that hold deeply immutable data) should use uppercase underscore-separated names:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
const val MAX_COUNT = 8
val USER_NAME_FIELD = "UserName"
```

</div>

Names of top-level or object properties which hold objects with behavior or mutable data should use camel-case names:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
val mutableCollection: MutableSet<String> = HashSet()
```

</div>

Names of properties holding references to singleton objects can use the same naming style as `object` declarations:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
val PersonComparator: Comparator<Person> = /*...*/
```

</div>
 
For enum constants, it's OK to use either uppercase underscore-separated names
(`enum class Color { RED, GREEN }`) or regular camel-case names starting with an uppercase first letter, depending on the usage.
   
#### Names for backing properties

If a class has two properties which are conceptually the same but one is part of a public API and another is an implementation
detail, use an underscore as the prefix for the name of the private property:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
class C {
    private val _elementList = mutableListOf<Element>()

    val elementList: List<Element>
         get() = _elementList
}
```

</div>

### Choosing good names

The name of a class is usually a noun or a noun phrase explaining what the class _is_: `List`, `PersonReader`.

The name of a method is usually a verb or a verb phrase saying what the method _does_: `close`, `readPersons`.
The name should also suggest if the method is mutating the object or returning a new one. For instance `sort` is
sorting a collection in place, while `sorted` is returning a sorted copy of the collection.

The names should make it clear what the purpose of the entity is, so it's best to avoid using meaningless words
(`Manager`, `Wrapper` etc.) in names.

When using an acronym as part of a declaration name, capitalize it if it consists of two letters (`IOStream`);
capitalize only the first letter if it is longer (`XmlFormatter`, `HttpInputStream`).


## Formatting

Use 4 spaces for indentation. Do not use tabs.

For curly braces, put the opening brace in the end of the line where the construct begins, and the closing brace
on a separate line aligned horizontally with the opening construct.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
if (elements != null) {
    for (element in elements) {
        // ...
    }
}
```

</div>

(Note: In Kotlin, semicolons are optional, and therefore line breaks are significant. The language design assumes 
Java-style braces, and you may encounter surprising behavior if you try to use a different formatting style.)

### Horizontal whitespace

Put spaces around binary operators (`a + b`). Exception: don't put spaces around the "range to" operator (`0..i`).

Do not put spaces around unary operators (`a++`)

Put spaces between control flow keywords (`if`, `when`, `for` and `while`) and the corresponding opening parenthesis.

Do not put a space before an opening parenthesis in a primary constructor declaration, method declaration or method call.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
class A(val x: Int)

fun foo(x: Int) { ... }

fun bar() {
    foo(1)
}
```

</div>

Never put a space after `(`, `[`, or before `]`, `)`.

Never put a space around `.` or `?.`: `foo.bar().filter { it > 2 }.joinToString()`, `foo?.bar()`

Put a space after `//`: `// This is a comment`

Do not put spaces around angle brackets used to specify type parameters: `class Map<K, V> { ... }`

Do not put spaces around `::`: `Foo::class`, `String::length`

Do not put a space before `?` used to mark a nullable type: `String?`

As a general rule, avoid horizontal alignment of any kind. Renaming an identifier to a name with a different length
should not affect the formatting of either the declaration or any of the usages.

### Colon

Put a space before `:` in the following cases:

  * when it's used to separate a type and a supertype;
  * when delegating to a superclass constructor or a different constructor of the same class;
  * after the `object` keyword.
    
Don't put a space before `:` when it separates a declaration and its type.
 
Always put a space after `:`.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
abstract class Foo<out T : Any> : IFoo {
    abstract fun foo(a: Int): T
}

class FooImpl : Foo() {
    constructor(x: String) : this(x) { /*...*/ }
    
    val x = object : IFoo { /*...*/ } 
} 
```

</div>

### Class header formatting

Classes with a few primary constructor parameters can be written in a single line:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
class Person(id: Int, name: String)
```

</div>

Classes with longer headers should be formatted so that each primary constructor parameter is in a separate line with indentation.
Also, the closing parenthesis should be on a new line. If we use inheritance, then the superclass constructor call or list of implemented interfaces
should be located on the same line as the parenthesis:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
class Person(
    id: Int,
    name: String,
    surname: String
) : Human(id, name) { /*...*/ }
```

</div>

For multiple interfaces, the superclass constructor call should be located first and then each interface should be located in a different line:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
class Person(
    id: Int,
    name: String,
    surname: String
) : Human(id, name),
    KotlinMaker { /*...*/ }
```

</div>

For classes with a long supertype list, put a line break after the colon and align all supertype names horizontally:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
class MyFavouriteVeryLongClassHolder :
    MyLongHolder<MyFavouriteVeryLongClass>(),
    SomeOtherInterface,
    AndAnotherOne {

    fun foo() { /*...*/ }
}
```

</div>

To clearly separate the class header and body when the class header is long, either put a blank line
following the class header (as in the example above), or put the opening curly brace on a separate line:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
class MyFavouriteVeryLongClassHolder :
    MyLongHolder<MyFavouriteVeryLongClass>(),
    SomeOtherInterface,
    AndAnotherOne 
{
    fun foo() { /*...*/ }
}
```

</div>

Use regular indent (4 spaces) for constructor parameters.

> Rationale: This ensures that properties declared in the primary constructor have the same indentation as properties
> declared in the body of a class.

### Modifiers

If a declaration has multiple modifiers, always put them in the following order:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
public / protected / private / internal
expect / actual
final / open / abstract / sealed / const
external
override
lateinit
tailrec
vararg
suspend
inner
enum / annotation
companion
inline
infix
operator
data
```

</div>

Place all annotations before modifiers:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
@Named("Foo")
private val foo: Foo
```

</div>

Unless you're working on a library, omit redundant modifiers (e.g. `public`).

### Annotation formatting

Annotations are typically placed on separate lines, before the declaration to which they are attached, and with the same indentation:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
@Target(AnnotationTarget.PROPERTY)
annotation class JsonExclude
```

</div>

Annotations without arguments may be placed on the same line:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
@JsonExclude @JvmField
var x: String
```

</div>

A single annotation without arguments may be placed on the same line as the corresponding declaration:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
@Test fun foo() { /*...*/ }
```

</div>

### File annotations

File annotations are placed after the file comment (if any), before the `package` statement, and are separated from `package` with a blank line (to emphasize the fact that they target the file and not the package).

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
/** License, copyright and whatever */
@file:JvmName("FooBar")

package foo.bar
```

</div>

### Function formatting

If the function signature doesn't fit on a single line, use the following syntax:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
fun longMethodName(
    argument: ArgumentType = defaultValue,
    argument2: AnotherArgumentType
): ReturnType {
    // body
}
```

</div>

Use regular indent (4 spaces) for function parameters.

> Rationale: Consistency with constructor parameters

Prefer using an expression body for functions with the body consisting of a single expression.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
fun foo(): Int {     // bad
    return 1 
}

fun foo() = 1        // good
```

</div>

### Expression body formatting

If the function has an expression body that doesn't fit in the same line as the declaration, put the `=` sign on the first line.
Indent the expression body by 4 spaces.

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
fun f(x: String) =
    x.length
```

</div>

### Property formatting

For very simple read-only properties, consider one-line formatting:

<div class="sample" markdown="1" theme="idea" data-highlight-only >

```kotlin
val isEmpty: Boolean get() = size == 0
```

</div>

For more complex properties, always put `get` and `set` keywords on separate lines:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
val foo: String
    get() { /*...*/ }
```

</div>

For properties with an initializer, if the initializer is long, add a line break after the equals sign
and indent the initializer by four spaces:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
private val defaultCharset: Charset? =
    EncodingRegistry.getInstance().getDefaultCharsetForPropertiesFiles(file)
```

</div>

### Formatting control flow statements

If the condition of an `if` or `when` statement is multiline, always use curly braces around the body of the statement.
Indent each subsequent line of the condition by 4 spaces relative to statement begin. 
Put the closing parentheses of the condition together with the opening curly brace on a separate line:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
if (!component.isSyncing &&
    !hasAnyKotlinRuntimeInScope(module)
) {
    return createKotlinNotConfiguredPanel(module)
}
```

</div>

> Rationale: Tidy alignment and clear separation of condition and statement body

Put the `else`, `catch`, `finally` keywords, as well as the `while` keyword of a do/while loop, on the same line as the 
preceding curly brace:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
if (condition) {
    // body
} else {
    // else part
}

try {
    // body
} finally {
    // cleanup
}
```

</div>

In a `when` statement, if a branch is more than a single line, consider separating it from adjacent case blocks with a blank line:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
private fun parsePropertyValue(propName: String, token: Token) {
    when (token) {
        is Token.ValueToken ->
            callback.visitValue(propName, token.value)

        Token.LBRACE -> { // ...
        }
    }
}
```

</div>

Put short branches on the same line as the condition, without braces.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
when (foo) {
    true -> bar() // good
    false -> { baz() } // bad
}
```

</div>


### Method call formatting

In long argument lists, put a line break after the opening parenthesis. Indent arguments by 4 spaces. 
Group multiple closely related arguments on the same line.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
drawSquare(
    x = 10, y = 10,
    width = 100, height = 100,
    fill = true
)
```

</div>

Put spaces around the `=` sign separating the argument name and value.

### Chained call wrapping

When wrapping chained calls, put the `.` character or the `?.` operator on the next line, with a single indent:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
val anchor = owner
    ?.firstChild!!
    .siblings(forward = true)
    .dropWhile { it is PsiComment || it is PsiWhiteSpace }
```

</div>

The first call in the chain usually should have a line break before it, but it's OK to omit it if the code makes more sense that way.

### Lambda formatting

In lambda expressions, spaces should be used around the curly braces, as well as around the arrow which separates the parameters
from the body. If a call takes a single lambda, it should be passed outside of parentheses whenever possible.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
list.filter { it > 10 }
```

</div>

If assigning a label for a lambda, do not put a space between the label and the opening curly brace:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
fun foo() {
    ints.forEach lit@{
        // ...
    }
}
```

</div>

When declaring parameter names in a multiline lambda, put the names on the first line, followed by the arrow and the newline:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
appendCommaSeparated(properties) { prop ->
    val propertyValue = prop.get(obj)  // ...
}
```

</div>

If the parameter list is too long to fit on a line, put the arrow on a separate line:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
foo {
   context: Context,
   environment: Env
   ->
   context.configureEnv(environment)
}
```

</div>

## Documentation comments

For longer documentation comments, place the opening `/**` on a separate line and begin each subsequent line
with an asterisk:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
/**
 * This is a documentation comment
 * on multiple lines.
 */
```

</div>

Short comments can be placed on a single line:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
/** This is a short documentation comment. */
```

</div>

Generally, avoid using `@param` and `@return` tags. Instead, incorporate the description of parameters and return values
directly into the documentation comment, and add links to parameters wherever they are mentioned. Use `@param` and
`@return` only when a lengthy description is required which doesn't fit into the flow of the main text.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
// Avoid doing this:

/**
 * Returns the absolute value of the given number.
 * @param number The number to return the absolute value for.
 * @return The absolute value.
 */
fun abs(number: Int) { /*...*/ }

// Do this instead:

/**
 * Returns the absolute value of the given [number].
 */
fun abs(number: Int) { /*...*/ }
```

</div>

## Avoiding redundant constructs

In general, if a certain syntactic construction in Kotlin is optional and highlighted by the IDE
as redundant, you should omit it in your code. Do not leave unnecessary syntactic elements in code
just "for clarity".

### Unit

If a function returns Unit, the return type should be omitted:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
fun foo() { // ": Unit" is omitted here

}
```

</div>

### Semicolons

Omit semicolons whenever possible.

### String templates

Don't use curly braces when inserting a simple variable into a string template. Use curly braces only for longer expressions.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
println("$name has ${children.size} children")
```

</div>


## Idiomatic use of language features

### Immutability

Prefer using immutable data to mutable. Always declare local variables and properties as `val` rather than `var` if
they are not modified after initialization.

Always use immutable collection interfaces (`Collection`, `List`, `Set`, `Map`) to declare collections which are not
mutated. When using factory functions to create collection instances, always use functions that return immutable
collection types when possible:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
// Bad: use of mutable collection type for value which will not be mutated
fun validateValue(actualValue: String, allowedValues: HashSet<String>) { ... }

// Good: immutable collection type used instead
fun validateValue(actualValue: String, allowedValues: Set<String>) { ... }

// Bad: arrayListOf() returns ArrayList<T>, which is a mutable collection type
val allowedValues = arrayListOf("a", "b", "c")

// Good: listOf() returns List<T>
val allowedValues = listOf("a", "b", "c")
```

</div>

### Default parameter values

Prefer declaring functions with default parameter values to declaring overloaded functions.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
// Bad
fun foo() = foo("a")
fun foo(a: String) { /*...*/ }

// Good
fun foo(a: String = "a") { /*...*/ }
```

</div>

### Type aliases

If you have a functional type or a type with type parameters which is used multiple times in a codebase, prefer defining
a type alias for it:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
typealias MouseClickHandler = (Any, MouseEvent) -> Unit
typealias PersonIndex = Map<String, Person>
```

</div>

### Lambda parameters

In lambdas which are short and not nested, it's recommended to use the `it` convention instead of declaring the parameter
explicitly. In nested lambdas with parameters, parameters should be always declared explicitly.


### Returns in a lambda

Avoid using multiple labeled returns in a lambda. Consider restructuring the lambda so that it will have a single exit point.
If that's not possible or not clear enough, consider converting the lambda into an anonymous function.

Do not use a labeled return for the last statement in a lambda.

### Named arguments

Use the named argument syntax when a method takes multiple parameters of the same primitive type, or for parameters of `Boolean` type,
unless the meaning of all parameters is absolutely clear from context.

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
drawSquare(x = 10, y = 10, width = 100, height = 100, fill = true)
```

</div>

### Using conditional statements

Prefer using the expression form of `try`, `if` and `when`. Examples:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
return if (x) foo() else bar()

return when(x) {
    0 -> "zero"
    else -> "nonzero"
}
```

</div>

The above is preferable to:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
if (x)
    return foo()
else
    return bar()
    
when(x) {
    0 -> return "zero"
    else -> return "nonzero"
}    
```

</div>

### `if` versus `when`

Prefer using `if` for binary conditions instead of `when`. Instead of

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
when (x) {
    null -> // ...
    else -> // ...
}
```

</div>

use `if (x == null) ... else ...`

Prefer using `when` if there are three or more options.

### Using nullable `Boolean` values in conditions

If you need to use a nullable `Boolean` in a conditional statement, use `if (value == true)` or `if (value == false)` checks.

### Using loops

Prefer using higher-order functions (`filter`, `map` etc.) to loops. Exception: `forEach` (prefer using a regular `for` loop instead,
unless the receiver of `forEach` is nullable or `forEach` is used as part of a longer call chain).

When making a choice between a complex expression using multiple higher-order functions and a loop, understand the cost
of the operations being performed in each case and keep performance considerations in mind. 

### Loops on ranges

Use the `until` function to loop over an open range:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
for (i in 0..n - 1) { /*...*/ }  // bad
for (i in 0 until n) { /*...*/ }  // good
```

</div>

### Using strings

Prefer using string templates to string concatenation.

Prefer to use multiline strings instead of embedding `\n` escape sequences into regular string literals.

To maintain indentation in multiline strings, use `trimIndent` when the resulting string does not require any internal
indentation, or `trimMargin` when internal indentation is required:

<div class="sample" markdown="1" theme="idea" data-highlight-only auto-indent="false">

```kotlin
assertEquals(
    """
    Foo
    Bar
    """.trimIndent(), 
    value
)

val a = """if(a > 1) {
          |    return a
          |}""".trimMargin()
```

</div>

### Functions vs Properties

In some cases functions with no arguments might be interchangeable with read-only properties. 
Although the semantics are similar, there are some stylistic conventions on when to prefer one to another.

Prefer a property over a function when the underlying algorithm:

* does not throw
* is cheap to calculate (or caсhed on the first run)
* returns the same result over invocations if the object state hasn't changed

### Using extension functions

Use extension functions liberally. Every time you have a function that works primarily on an object, consider making it
an extension function accepting that object as a receiver. To minimize API pollution, restrict the visibility of
extension functions as much as it makes sense. As necessary, use local extension functions, member extension functions,
or top-level extension functions with private visibility.

### Using infix functions

Declare a function as infix only when it works on two objects which play a similar role. Good examples: `and`, `to`, `zip`.
Bad example: `add`.

Don't declare a method as infix if it mutates the receiver object.

### Factory functions

If you declare a factory function for a class, avoid giving it the same name as the class itself. Prefer using a distinct name
making it clear why the behavior of the factory function is special. Only if there is really no special semantics,
you can use the same name as the class.

Example:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
class Point(val x: Double, val y: Double) {
    companion object {
        fun fromPolar(angle: Double, radius: Double) = Point(...)
    }
}
```

</div>

If you have an object with multiple overloaded constructors that don't call different superclass constructors and
can't be reduced to a single constructor with default argument values, prefer to replace the overloaded constructors with
factory functions.

### Platform types

A public function/method returning an expression of a platform type must declare its Kotlin type explicitly:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
fun apiCall(): String = MyJavaApi.getProperty("name")
```

</div>

Any property (package-level or class-level) initialised with an expression of a platform type must declare its Kotlin type explicitly:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
class Person {
    val name: String = MyJavaApi.getProperty("name")
}
```

</div>

A local value initialized with an expression of a platform type may or may not have a type declaration:

<div class="sample" markdown="1" theme="idea" data-highlight-only>

```kotlin
fun main() {
    val name = MyJavaApi.getProperty("name")
    println(name)
}
```

</div>

### Using scope functions apply/with/run/also/let

Kotlin provides a variety of functions to execute a block of code in the context of a given object: `let`, `run`, `with`, `apply`, and `also`.
For the guidance on choosing the right scope function for your case, refer to [Scope Functions](scope-functions.html).

## Coding conventions for libraries

When writing libraries, it's recommended to follow an additional set of rules to ensure API stability:

 * Always explicitly specify member visibility (to avoid accidentally exposing declarations as public API)
 * Always explicitly specify function return types and property types (to avoid accidentally changing the return type
   when the implementation changes)
 * Provide KDoc comments for all public members, with the exception of overrides that do not require any new documentation
   (to support generating documentation for the library)
