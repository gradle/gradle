
Dependency injection is a useful pattern for composing software out of small, decoupled pieces. This specification describes
how we can make this available to plugins, both core and external.

# Use cases

## Terminology

For the following discussion we have:

### Gradle core

The non-plugin parts of the Gradle distribution. Currently represented by the core, coreImpl, launcher, toolingApi projects.

### Gradle core plugins

The plugin parts of the Gradle distribution.

### External plugins

Every other plugin implementation.

## Gradle core provides a capability to plugins, tasks and scripts.

Generally, a capability is represented as a Java(-compatible) interface or class. An example capability might be a `FileResolver`
or a `WorkerProcess` factory.

Currently, to expose a core capability, a property accessor is added to the appropriate Task, Project or Gradle interface.
Internal capabilities are exposed by adding a property accessor to one of the internal variants, or via
the internal `getServices()` method.

Both of these approaches have downsides. Adding methods to Project or one of the other
domain objects encourages these objects to be used as god objects that end up being passed around everywhere. It also means that the
core must know about the capability. Using `getServices()` is imperative and opaque, and there is very little of use that Gradle can offer here.

Dependency injection will provide a consistent and declarative approach to exposing capabilities to plugins, tasks and scripts,
regardless of whether the consumer is a core plugin, an external plugin, a build script, or a task.

## A plugin provides DSL extensions

Almost every plugin adds some extension to an existing domain object, or its own domain objects, to extend the Gradle DSL.

Currently, a plugin registers extensions on the various domain objects programmatically. There are 2 things this does: it makes the
extension available, and it also decorates the extension instance to mix in some DSL bevahiour.

Dependency injection would allow Gradle to automatically instantiate and decorate these extensions. The declarative nature of dependency injection
also allows other types of decoration to be used. For example, we could apply the decoration at class load time or build time, and mix
the DSL behaviour directly into the domain object class. This will allow convention mappings to apply to field access, and final classes and properties,
and fix a number of other issues with decoration.

The declarative nature of dependency injection also allows Gradle to statically infer much of the DSL contributed by a plugin, for inclusion in
the DSL reference or in IDE code completion.

Another issue plugins face in extending the DSL is the management of sets of domain objects. To provide a domain objects container, a plugin uses
`Project.newContainer()` and supplies a factory that can construct the instances. The factory is responsible for decorating the instances
to make them well-behaved DSL elements. This must be done using the internal `Instantiator` service.

Dependency injection would allow Gradle to automatically create and decorate the contents of domain object containers, as a factory would no longer be
required.

## A plugin provides some capability to be shared across projects

Some plugins need to maintain state or a service that is shared across more than one project. For example, the Java plugin
manages and reuses the compiler daemon across all projects. The IDEA, and Eclipse plugins manage a model that
spans multiple projects. The build announcements plugin requires a single, global announcement service that is used for all announcements
generated from all projects (and outside projects).

Currently, to do this, a plugin can extend the Gradle object or maintain some static state. This is awkward to test
and requires some work on the part of the plugin. Static state is also a problem when the daemon is used. Shared
state managed by the plugin also represents a coupling that causes problems for parallel and multi-jvm execution.

Some plugins may also need to maintain some state or a service that is shared across builds executed by the same daemon process.
For example, the Scala plugin might keep a compile daemon to be reused across builds. Or the IDE plugins might provide a service
that listens for changes to a model and notifies interested tooling API clients. The build announcements plugin might use the
announcement service to inform the user of background activities that the daemon is performing. And so on.

Dependency injection will relieve plugins from the resposibility of managing this shared capability. The declarative nature of
dependency injection means that Gradle can potentially take care of some of the issues related to sharing a capability across projects
when parallel or multi-jvm execution is used, and across builds.

## A plugin provides some capability to other plugins

Some plugins are intended to be extended by other plugins, but not necessarily through the DSL. For example, the native binaries plugin
provides a CompilerRegistry service that other plugins, such as the G++ and Visual studio plugins contribute compiler implementations to.
It would also make a lot of sense for our Java, Groovy and Scala plugins to use a similar approach.

Currently, a plugin implements this using one of the approaches described above, for sharing state across projects.

Dependency injection would allow a plugin to consume services provided by other plugins, in a way that is consistent and does not couple the
consuming plugin to the providing plugin. The declarative nature of dependency injection also allows Gradle to infer the dependencies between
plugins, and automatically apply the providing plugin. This inference can be done statically, which allows inclusion in the DSL reference
and allow automatic download of required plugins.

## A plugin provides some capability to its task instances

Some plugins need to make some capability available to task instances, without necessarily exposing them through the DSL. For example, the Java
plugin needs to make a Java compiler implementation available to the `JavaCompile` task instances.

Dependency injection would allow a plugin to inject a service into task instances.

## Evolving a capability in a backwards compatible way

Dependency injection provides some flexibility beyond our add-deprecate-remove approach to backwards compatibility:

* Both a new and old service interface can be made available for injection, without having the new interface extend the old interface.
* Given we can infer plugin dependencies, we can move services between provider plugins.
* Given we can inder plugin dependencies, we can move services from core to a provider plugin and vice versa.

## Gradle core is separated into modules

Given the above use cases have been addressed, Gradle core can be split into several plugins. For example, we may split out profiling, or daemon
management, or the help tasks, as separate plugins. We may bust dependency managament into Ivy, Maven, and transport specific plugins.

Addressing the above use cases also removes much of the distinction between core plugins and external plugins, allowing us to bust up the distribution
into separately downloadable pieces.

## Other goodness

* Provide more extension points out of the core and core plugins, such as Java compiler implementations, resource transports, and the like.
* Dependency injection means that task classes no longer need to extend DefaultTask. Things such as Project, AntBuilder, LoggingManager and so
on can be injected, and the Task interface mixed in through class decoration.
* Injecting actions into tasks might be useful.
* Extending the tooling API.
* Generating documentation for services, and which services are available for which domain objects.

# User visible changes

The implementation will be based on JSR-330 (see the [javax.inject](http://docs.oracle.com/javaee/6/api/javax/inject/package-summary.html) package).

Objects that are to receive dependencies provide a constructor annotated with `@Inject`.

## Exposing core services to plugins

Currently, there are 4 scopes for services:
* Global. These services are instantiated once per JVM. They are reused for multiple builds in the daemon.
* Per build. These services are instantiated once per build.
* Per project. These services are instantiated once per project (which is implicitly per build).
* Per task. These services are instantiated once per task (which is implicitly per project).

With the exception of global services, each scope has an associated domain object. Or, in other words, a domain object may have zero or more services
associated with it. The lifetime of the domain object determines the lifetime of its services. Most domain objects do not have any associated services.

The set of services available for the various types are:

* `Gradle` - all build scoped services.
* `Settings` - all build scoped services.
* `Project` - all project and build scoped services.
* `Task` - all task, project and build scoped services.
* Other objects - all build scoped services.

This spec does not define any capability for attaching services to arbitrary domain objects. Only the above scopes and object types will be supported.
This doesn't rule out future work to allow this.

When a plugin is applied to a domain object, the domain object's services will be available for injection into the plugin's constructor.

## Exposing core services to tasks

When a task is instantiated, all task scoped services will be available for injection into the task's constructor.

## Exposing core services to scripts

Scripts are applied to a domain object, so in this way are similar to plugins. The domain object's services will be made available for injection into the script.

An open question is the syntax to use for the injection. Some candidates:

1. A script property annotated with @Inject:

    @Inject FileOperations files

    task myTask << {
        files.file('my-file').text = 'hi'
    }

The property declaration must appear at the start of the script, or perhaps in the `buildscript` section, so that they can be statically inspected.

2. Mix a dynamic property for each available service into the script instance:

    // implicit getFileOperations() is added for FileOperations service

    task myTask << {
        fileOperations.file('my-file').text = 'hi'
    }

3. Imperative lookup:

    // Add <T> T Script.getService(Class<T>)

    task myTask << {
        getService(FileOperations).file('my-file').text = 'hi'
    }

Options 2 and 3 are imperative options, and so cannot be statically inspected. They allow auto-detection of dependencies on other plugins, but the
dependencies are detected at runtime. We may limit service lookup to configuration time only.

## Injecting services into extensions

When an extension is instantiated for a given domain object, the domain object's services are available for injection into the extension's constructor.
The target domain object is also available for injection.

Dependency injection will only be available for those extension objects that are instantiated by Gradle, using `ExtensionContainer.create()`
(or future equivalent). Dependency injection will not be supported for convention objects.

## Injecting services into domain object container elements

TBD

## Plugin contributed services

A plugin will be able to contribute services by including the service implementation class in its JAR file, and annotating the implementation class with
the appropriate annotation. The annotation specifies which scope the service is available in:

* `@Build` - a service available in build scope.
* `@Project` - a service available in project scope.
* `@Task` - a service available in task scope.

A plugin's services will, by default, be visible only to that plugin.

Every object that is instantiated by Gradle, and contributed by a plugin, has a target domain object associated with it. To recap:

* A plugin's target object is the domain object the plugin is being applied to.
* A task's target object is the task itself.
* An extension's target object is the domain object the extension is being added to.
* A service implementation's target object is either a task, project or build, based on its annotation.

When instantiating a service instance, the services of the associated scope are available for injection into the service's constructor. Fail if any cycles are present.

More specifically, to inject a service of type T into an object with target domain object D, contributed by a plugin P:

1. If D is an instance of T, inject D.
2. Look for an existing service instances contributed by P that is attached to D and is an instance of T. If found, inject. Fail if there are multiple such services.
3. Look in the plugin's implementation JAR file for a class that is assignable to T and annotated with the appropriate scope annotation. If found,
instantiate the service implementation, attach to D, and inject. Fail if there are multiple such candidate service implementations.
3. Repeat the previous step for wider scopes, if any.
4. Look for a core service attached to D. If found, inject.
5. Fail with an appropriate error message.

This means:

* Plugin services are preferred over services from outside the plugin.
* `@Task` services are available for injection into tasks, task plugins, task extensions, and task-scoped services.
* `@Project` services are available for injection into project and task plugins, project and task extensions, and task- and project-scoped services.
* `@Build` services are available for injection into all types of plugins, extensions, and services.

## Exposing services to other plugins

TBD

## Declarative DSL extensions

TBD

## Shared services

TBD

## Global services

TBD

## Sad day cases

TBD

# Integration test coverage

TBD

# Implementation notes

The initial implementation will not support the full JSR-330 specification:

* Only constructor based injection will be supported.
* The `@Named` and `@Qualifier` annotations will not be supported. These annotations will be ignored.

The initial implementation will vary from the JSR-330 specification, for backwards compability reasons:

* If a class has a single constructor, this constructor will be used for injection. A deprecation warning will be logged if this
constructor is not a zero args constructor and is not annotated with `@Inject`.
* If a class has an exact match constructor, this constructor will be used regardless of whether there
are other constructors annotated with `@Inject`. A deprecation warning will be logged if this constructor is not annotated with
`@Inject`.
* A class may have multiple constructors annotated with `@Inject`. The constructor will be selected as above and a deprecation warning will be logged.
* Fields and methods annotated with `@Inject` will be ignored. A deprecation warning will be logged.

In Gradle 1.x, this means that a class that is to receive dependencies must have exactly one constructor, and that constructor must be annotated with `@Inject`, or take no arguments, to avoid a deprecation warning.

In Gradle 2.0, the implementation will be changed so that a class must follow the constructor annotation rules specified in JSR-330. This means a class
must have either a single constructor, and that constructor must take no arguments, or the class must have exactly one of its constructor annotated with
`@Inject`. Support for field and method injection may be added at this point.

## Injecting into extensions

TBD

## Injecting into domain object containers

TBD

## Interaction with class decoration

TBD

# Open issues

* Specify support for Provider<T> and collections.
* It should be possible to use project scoped services for domain objects in the project 'context'. There's some concept of a 'parent' or 'container'
here (an internal concept; we don't necessarily want to expose this):
    * Every managed object belongs to a container.
    * Every container is-a managed object (and so belongs to some other container, with the exception of the root container).
    * A container has zero or more services available.
    * A container has a lifetime.
    * A managed object may use services from its container (and so a container may use the services from its enclosing container).
* How to inject into scripts?
* Should we scan the full runtime classpath for a plugin when looking for service implementations?
* Should there be an associated plugin meta-data file, as an alternative to annotations?
* Should there be an API for contributing services.
* Cleaning up services at the end of the build.
* Shared services and project decoupling. There are a few types of services here:
    1. A shared model.
    2. A service that should be shared with the same JVM, but each JVM can have its own instance.
    3. A service that must be shared across all JVMs.
* Allow a plugin to use a more sophisticated DI container, such as guice or spring. We want to keep the Gradle dependency injection
  very light-weight. Perhaps add a public scope-aware service lookup interface, and allow a plugin to provide a single implementation of this
  instead of using annotated services.
* Consider about how to bridge to OSGi, so that a plugin could be packaged as an OSGi bundle. We don't necessarily want to do this, but it should be
  theoretically possible.
* Think about how to mix in the DSL behaviour statically, so that it is available in Java and other statically typed languages, and in Javadoc, and
IDE code completion, and tests.
