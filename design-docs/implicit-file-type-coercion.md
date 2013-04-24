There are many natural ways to express the value for a file in the Gradle DSL. This is formally encoded in the `project.file()`
method that takes an Object and returns a File, using well defined coercion rules. Furthermore, many tasks and other DSL exposed
objects accept Object parameters which they implicitly coerce to a File internally. This spec is about making such implicit coercion
automatic, implicit and universal.

This may also lay some groundwork for other implicit type coercions.

# Use cases

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

# Implementation plan

## Story: A DSL user specifies the value for a file property with a value supported by project.file() (no deferred evaluation)

A user sets a value for a file property, using a value that can be converted to a File via project.file(). No deferred evaluation is supported.
That is, all values are coerced immediately. This will be targeted at DSL (or more precisely, Groovy) users. This story does
not include a static Java friendly API for this functionality.

Only property setting is covered, so only setters will be decorated. Arbitrary methods that accept a File are not covered by this story.

### Coercing relative values

For some objects (that are subject to DSL enhancement), there is no good way to coerce a value that equates to a relative path (e.g. `Gradle` and `Settings` objects). For such objects, if the value to be coerced is not coercable to an absolute file it will be an error condition. 

### User visible changes

The only user visible change will the documentation that states that it is possible to assign different types of values to
`File` implicitly in the documentation. There is also no new API or change required by plugin/task etc. authors to leverage this
feature.

### Sad day cases

The given value may not be coercible to `File`. Currently, every type is potentially convertible as the `project.file()` coercion strategy includes a fallback of `toString()`'ing any object and using its string representation as a file path.

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

A complicating factor is that the type coercing dynamic object must be contextual the project that the object belongs to.
It is not straightforward to “push the project” down to the ExtensibleDynamicObject which will create the coercing wrapper.
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

## Story: Build logic authors understand the file type coercion mechanism and can use it in third party code

This story is probably just about adding user guide content specifically aimed at people implementing build logic (where as the DSL ref story is targeted at people using build logic)

## Story: Build logic authors (internal and third party) implement defined strategy for removing old, untyped, File setter methods

Having flexible file inputs is currently implemented by types implementing a setter that takes `Object` and doing the coercion internally. 
Having file type coercion implemented as a decoration means that such setters should be strongly typed to `File`. 

This story will initiate a migration away from these untyped setters to strongly typed setters. 
As part of this, the migration path should be documented and outlined so that third party build logic authors can do the same thing.

## Story: Type coercion is used in conjunction with convention mapping to defer evaluation (incl. coercion)

## Story: A static API user (e.g. Java) specifies the value for a file property with a value supported by project.file() (no deferred evaluation)

# Open issues

This stuff is never done. This section is to keep track of assumptions and things we haven't figured out yet.