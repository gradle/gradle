This spec contains stories and describes improvements to be made to how we “enhance” objects for usage in the DSL.

Objects designed for use in the DSL get enhanced with extra functionality to make their use more convenient and to support Gradle idioms.
This enhancements provide:

1. Extra properties
1. Extensions
1. Implicit type coercion when calling methods
1. “set methods” (foo(String s) variant for setFoo(String s))
1. Closure overloads for Action methods
1. Dynamic properties (deprecated in favor of extra properties)
1. Convention plugins (predecessor to extensions)
1. Convention mapping (lazily derived values)

Enhancement is currently done by dynamically generating subclasses at runtime using ASM. 

This spec has some crossover with the spec on improving the documentation.

# Use cases

## Implicit Type Coercion

For API such as:

    class MyTask extends DefaultTask {
        File someFile
    }

The user should be able to set this value flexibly:

    myTask {
        someFile "project/relative/path"
    }

Instead of the long hand:

    myTask {
        someFile project.file("project/relative/path")
    }

There are two user oriented views of this feature:

1. The user trying to set a file value
2. The build “plugin” author accepting a file value from the “user”

This type coercion should be entirely transparent to group #2 and simple and predictable for group #1. That is, for any object exposed
in the DSL, a user should be able to set `File` properties of objects using different (well defined) types.

There are other potential coercions, such as string to enum value.

# Implementation plan

## Story: A DSL user specifies the value for a file property with a value supported by project.file() (no deferred evaluation)

A user sets a value for a file property, using a value that can be converted to a `File` via `project.file()`. No deferred evaluation is supported.
That is, all values are coerced immediately. This will be targeted at DSL (or more precisely, Groovy) users. This story does
not include a static Java friendly API for this functionality.

Only property setting is covered, so only setters will be decorated. Arbitrary methods that accept a File are not covered by this story.

### Coercing relative values

Every DSL object currently lives in one of the following scopes. The scope associated with a DSL object determines the strategy for resolving a relative path to
a `File`:

- Project scope: all objects owned by a project, including tasks, project extensions and project plugins. Relative paths are resolved using the project directory
  as the base directory.
- Gradle scope: all objects owned by a gradle object. Relative paths are not supported.
- Settings scope: all objects owned by a settings object. Relative paths are resolved using the settings directory as the base directory.
- Global scope: everything else. Relative paths are not supported.

Implementation-wise, the scope of a DSL object is encoded in the services that are visible to the object. In particular, a `FileResolver` is available to each object
that is responsible for coercion to`File`. This story does not cover any changes to the above scopes.

### Coercing `Object`

Currently, every type is potentially convertible as the `project.file()` coercion strategy includes a fallback of `toString()`'ing any object and using its string representation as a file path. However, this has been deprecated and scheduled for 2.0 removal. Implicit coercion needs to initially support this fallback strategy but issue a deprecation warning similar to `project.file()`.

### User visible changes

- Documentation that states that it is possible to assign different types of values to `File` implicitly in the documentation. There is also no new
  API or change required by plugin/task etc. authors to leverage this feature.
- Update 'writing custom tasks' user guide chapter and sample to make use of this feature.

### Sad day cases

The produced error message should indicate:

1. The object that the property-to-be-set belongs to
2. The name of the property-to-be-set
3. The string representation of the value to be coerced
4. They type of the value to be coerced
5. A description of what the valid types are (including constraints: e.g. URI type values must be of the file:// protocol)

Values that cannot be coerced:

1. `null`
2. An empty string (incl. an object whose `toString()` value is an empty string) 
3. An effectively relative path where the target object has no "base" and can not resolve that path relative to anything
4. A URL type value where the protocol is not `file`
4. A URL type value where the URL is malformed
5. An object that will be coerced via its `toString()` representation where the `toString()` method throws an exception
6. An object that will be coerced via its `toString()` representation where the `toString()` method returns a value that cannot be interpreted as a path (will involve researching what kind of string values cannot be used as paths by `File`)

### Test coverage

1. User tries to assign relative path as String to File property (and is resolved project relative)
2. User tries to assign absolute path as String to File property
3. Variants on #1 and #2 using other values supported by Project.file()
4. User tries to assign value using the =-less method variant (e.g. obj.someFileProperty("some/path"))
5. User tries to assign value using a statically declared `setProperty(String, Object)` method (Task.setProperty(), Project.setProperty())
6. User tries to assign value using Gradle's `DynamicObject` protocol (e.g. Project.setProperty())

### Implementation approach

High level:

1. Add a type coercing `DynamicObject` implementation.
2. In `ExtensibleDynamicObject` (the dynamic object created in decorated classes), wrap the delegate dynamic object in the type coercing wrapper.

Detail:

A complicating factor is that the type coercing dynamic object must be contextual the scope that the object belongs to.
It is not straightforward to “push the scope” down to the ExtensibleDynamicObject which will create the coercing wrapper.
We have the same problem with pushing down the instantiator to this level to facilitate extension containers being able to
construct decorated objects.

A new type will be created:

    interface ObjectInstantiationContext {
        ServiceRegistry getServices()
    }

When a decorated object is created, there will be a thread local object of this type available for retrieval (like `ThreadGlobalInstantiator`
or `AbstractTask.nextInstance`). `MixInExtensibleDynamicObject` will read the thread global `ObjectInstantiationContext` and use it when constructing
the backing dynamic objects.

Initially the coercion will be implemented by fetching a `FileResolver` from the instantion context service registry and using it to coerce the value.

The "type coercing wrapper" will be implemented as a `DynamicObject` implementation that can wrap any `DynamicObject` implementation. It will intercept methods and identify coercions based on given argument types and parameter types of the methods provided by the real dynamic object. 

## Story: User reading DSL guide understands where type coercion is applicable for `File` properties and what the coercion rules are

## Story: Build logic authors (internal and third party) implement defined strategy for removing old, untyped, File setter methods

Having flexible file inputs is currently implemented by types implementing a setter that takes `Object` and doing the coercion internally. 
Having file type coercion implemented as a decoration means that such setters should be strongly typed to `File`. 

The strategy will be to simply overload setters and set methods with `File` accepting variants, and `@Deprecated`ing `Object` accepting variants.

## Story: A static API user (e.g. Java) specifies the value for a file property with a value supported by project.file() (no deferred evaluation)

## Story: DSL enhancement (aspects thereof) are opt-in

Currently, object enhancement is a function of where/how it is used. 
This story makes enhancement opt-in in that types must explicitly declare what enhancements they want.
This declaration can be used by tooling, such as our documentation generator or IDEs trying to provide content assistance.

This story also encompasses breaking up the enhancement from being all or nothing into discrete bits.
Objects may need type coercion and other syntactic conveniences, but not require extensibility

## Story: DSL enhancement is performed at class load time instead of instantiation time

Our current enhancement strategy is based on dynamically generating subclasses at runtime.
This has the following drawbacks:

1. Causes strange behaviour with access to private methods
2. Requires reflective instantiation
3. Requires pushing an Instantiator service around so enhanced objects can be created

This story makes enhancement a class load time function.

## Story: Dynamic object protocol is not two-pass based

Currently, we ask objects if they can respond to a method/property invocation before actually proceeding with the invocation.
This causes code duplication as typically the determination happens in the check and the actual invocation.

This story is about changing the protocol from being “Can you do this? Yes? Go ahead then.” to “Please do this and tell me if you can't”.

This will avoid code duplication and is potentially more efficient.

## Story: Relative file method arguments are contextually absolutised 

When calling methods (incl. setting properties) on enhanced objects with files representing relative paths, the target should receive an absolute file.
It should be resolved to the logical base (e.g. project dir for a task).
This should work when the caller is Java.

## Story: Enhanced object properties are automatically configurable by action/closure

In order to achieve:

    def foo = // create a Foo
    foo {
        bar {
            
        }
    }
    
We have to write:

    class Foo {
        Bar bar
        
        void bar(Action<? super Bar> action) {
            action.execute(bar)
        }
    }
    
This story is about avoiding the need to write the action accepting method in order to achieve this behaviour.

# Open issues
