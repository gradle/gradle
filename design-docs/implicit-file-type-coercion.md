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
in the DSL, a user should be able to set File properties of objects using different (supported) types.

# Implementation plan

## Story 1: A DSL user specifies the value for a file property with a value supported by project.file() (no deferred evaluation)

A user sets a value for a file property, using a value that can be converted to a File via project.file(). No deferred evaluation is supported.
That is, all values are coerced immediately. This will be targeted at DSL (or more precisely, Groovy) users. This story does
not include a static Java friendly API for this functionality.

### User visible changes

The only user visible change will the documentation that states that it is possible to assign different types of values to
File implicitly in the documentation. There is also no new API or change required by plugin/task etc. authors to leverage this
feature.

### Sad day cases

The given value may not be coercible to File. The produced error message should indicate:

1. The object that the property-to-be-set belongs to
2. The name of the property-to-be-set
3. The invalid type
4. A description of what the valid types are, or how to find out what the valid types are

### Test coverage

1. User tries to assign relative path as String to File property (and is resolved project relative)
2. User tries to assign absolute path as String to File property
3. Variants on #1 and #2 using other values supported by Project.file()
4. User tries to assign value using the =-less method variant (e.g. obj.someFileProperty("some/path"))

### Implementation approach

High level:

1. Add a type coercing DynamicObject implementation.
2. In ExtensibleDynamicObject (the dynamic object created in decorated classes), wrap the delegate dynamic object in the type coercing wrapper.

Detail:

A complicating factor is that the type coercing dynamic object must be contextual the project that the object belongs to.
It is not straightforward to “push the project” down to the ExtensibleDynamicObject which will create the coercing wrapper.
We have the same problem with pushing down the instantiator to this level to facilitate extension containers being able to
construct decorated objects.

A new type will be created:

    interface ObjectInstantiationContext {
        Project getProject()
        Instantiator getInstantiator()
    }

When a decorated object is created, there will be a thread local object of this type available for retrieval (like ThreadGlobalInstantiator
or AbstractTask.nextInstance). MixInExtensibleDynamicObject will read the thread global ObjectInstantiationContext and use it when constructing
the backing dynamic objects.

## Story 2: A static API user (e.g. Java) specifies the value for a file property with a value supported by project.file() (no deferred evaluation)

### User visible changes

TBD

### Sad day cases

TBD

### Test coverage

TBD

### Implementation approach

TBD

# Open issues

This stuff is never done. This section is to keep track of assumptions and things we haven't figured out yet.