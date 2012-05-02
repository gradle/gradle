# What is this?

This is a proposal for a rethinking of the Gradle dependency management and publication model.

# Why?

* To come up with a concrete plan for solving dependency management problems for projects that are not simply 'build my jar' projects. For example,
  projects that build and publish C/C++ binaries, Javascript libraries, and so on.
* To provide a richer model that will allow projects to collaborate through the dependency management system, making these projects less coupled at
  configuration time. This will allow us to introduce some nice performance and scalability features.
* To allow Gradle to move beyond simply building things, and into release management, promotion, deployment and so on.

# Overview

The main design approach taken here is to promote *software components* as the most important concept in dependency management, and indeed Gradle
as a whole. A software component is a logical piece of software, published as a number of binary artefacts. A component might be a library that
runs on the JVM, a native executable, or a Javascript library. Software teams develop, test, release, publish, deploy and collaborate through
software components. Producing working software components is the main purpose of any development team. Our model should reflect this.

The distinction here with our existing approach is that we explicitly model each software component, rather than implicitly describing them through
modules and dependency configurations. This moves the model closer to the reality of the development world, and decouples build logic (including
plugins) that operate on components from the dependency management model. A component definition can be assembled from various sources, not just a
module meta-data descriptor.

A second approach taken here is to strongly type the software components. We model, for example, a JVM library, a J2EE application, a native executable,
and so on. For each such component type, there will be a corresponding strongly typed DSL element (i.e. a Java interface) to represent this. Build
logic will use these DSL elements to interact with the component, either for defining the outgoing components produced by the project, or to query the
incoming components used by the project.

Each component type will have a well defined mapping to various binary repository formats. These mappings (or schemas) will be versioned, so that we
can evolve these schemas over time.

The distinction here with our existing approach is that we use concrete types to represent components, both in build logic code and to exchange between
projects, instead of the overly abstract module and dependency configuration concepts. We also decouple the component definition from the mapping to
a binary repository, so that multiple mappings are possible.

Similarly, artefacts and dependencies will be strongly typed. For example, we will model explicitly a JAR, native dynamic library, or a signature.

This modelling will provide conventions that can be shared between teams, and taken advantage of by tooling. This rich model allows all kinds of
interesting plugins to be developed.

Finally, we add a little more structure to projects and their relationships. Now, a project becomes a container for software components. A project
knows how to build the components that it contains. Projects can also do other things to components, such as release, test, publish, install or deploy
them.

Dependencies are no longer between projects, but between components. In fact, projects will no longer have any dependencies at all.

# Abstract Concepts

Defined below are some of the abstract concepts that make up the dependency management model:

## Software component

A logical component, such as a JVM library, a native executable, or a Javascript library.

* A software component has a unique identifier.
* A software component has a type.
* A software component may have some type specific identifier, such as an OSGi symbolic name or android package name.

### Version

A software component has multiple versions. A version represents some implementation of the component at a particular point in time.

Some component types mandate a particular versioning scheme. For example, Jigsaw modules and OSGi bundle must have a version that conforms to
a particular grammar. Android applications have an integer version code and a version string.

* A version has a human consumable version string. A version string is not necessarily unique.
* A version has a Gradle-assigned unique identifier.
* A version is built from a set of source revisions or branches.
* A version built by a CI server usually has a unique build number.
* A version may have an associated versioning scheme.
* A version may have additional component type specific meta-data.

#### Versioning scheme

A versioning scheme defines the semantics of the version string, such as how versions can be compared to each other, and what compatibility
constraints are relevant to the version string.

### Release

A release is-a version, with some special meaning.

### Variant

A variant is some variation of the component that is functionally equivalent, but can only be used in certain constrained ways. Some examples of
variants are: a native executable compiled for 64-bit windows, or a non-minified Javascript library, or a JVM library compiled into java 6 byte code.
A variant might also represent a WAR tailored for the QA environment.

* A software component version has one or more variants.
* The set of available variants is implied by the component type.
* A variant has a name.
* A variant has some component type specific attributes.

TODO: Are there a few concepts here?

### Packaging

Packaging represents how the component is assembled into artefacts. For example, a web application might be packaged as a WAR, or as a command-line
application with an embedded web container, or as an installer that installs a JVM, web container and the web application. Or all of these.

* A software component version variant has one or more packagings.
* The set of available packagings is implied by the component type.
* A packaging has a name.

### Usage

A usage is some way that a component can be used. For example, a JVM library is used to compile a client component, to compile and execute the client's
unit tests, and at runtime.

* A software component version variant has one or more usages.
* The set of available usages is implied by the component type.
* A usage has a name.
* A usage implies a target packaging.

### Feature

A feature is some optional capability that a component offers.

* A feature has a name.
* A feature implies zero or more additional artifacts and dependencies for given usage.

### Artifact

An artefact is a binary resource. For example, a jar file or native executable.

* An artefact has a unique identifier.
* An artefact belongs to a component version.
* An artefact has a type.
* An artefact has a name.
* A given usage implies a set of zero or more artefacts.

### Bundle

A bundle is-an artefact that includes some other software component. For example, a WAR that includes a JVM library in its WEB-INF/lib folder,
or a native executable that is linked against a static library.

Generally, this means that a bundle includes some of its dependencies, and that the environment in which the bundle is later used in must provide
the remaining dependencies.

A bundle may subsequently be bundled into a larger bundle.

* A bundle includes zero or more other artefacts.
* Bundling is a kind of usage.

### Auxiliary artifact

An artefact that provides some information about the software component, that is usage and variant independent. For example, a source or API
documentation archive. Also includes signature and checksum artefacts.

### Meta-data artifact

An auxiliary artefact that provides meta-data about the component version.

### Dependency

A dependency is a reference to another software component which is required by the component.

* A dependency is a set of criteria for selecting a compatible variant.
* A given usage implies a set of zero or more dependencies.
* A given (usage, dependency) implies a usage from the target component.

Some artefact types encode meta-data about their dependencies. For example, a Jigsaw module or OSGi bundle includes information about the other
modules/bundles that are required. A native binary includes information about which shared libraries are required at runtime.

## Publication

A publication is the binary representation of a component in a repository.

* A publication is a set of artefacts.
* A publication may include auxiliary artefacts.

## Project

A project is a container for software components. A project knows how to perform the various lifecycle steps for the components it owns.
For example, a project may build, release and deploy a component.

* A project builds zero or more components.
* A component build is built by exactly one project.
* A component build is published by exactly one project.

# Component types

Listed below are some of the concrete types of the abstract types defined above:

(note: we won't necessarily model all of these, they are listed here to flesh out the abstract model above)

## JVM component

A component that runs on the JVM.

* Has an implementation.
    * This is a run time usage.
    * Depends on the implementation of zero or more JVM libraries.
    * Depends on a particular language runtime.
* May bundle JVM libraries.

## JVM library

* Is-a JVM component.
* Has an API.
    * This is a compile time usage.
    * A set of classes.
    * Depends on the APIs of zero or more JVM libraries.
* Provides some test fixtures.
    * This is a test compile time usage.
    * Depend on the test fixtures and implementation of zero or more JVM libraries.
* Common variants:
    * bytecode level, e.g. java5 vs java 6.
    * source language, e.g. groovy 1.7 vs groovy 1.8.
    * nodeps vs all-deps vs private-all-deps (e.g. jarjared)
* Commonly packaged as:
    * A single jar artefact that bundles API + implementation, and another jar that bundles test fixtures.
    * A separate API jar, implementation jar and test fixture jar.
    * A zip or tar that bundles the implementation + documentation.

test fixtures have an API and an implementation?

## JVM library with JNI implementation

## Gradle plugin

* Is-a JVM library

## Jigsaw Module

* Is-a JVM library
* Has a Jigsaw specific identifier.

## OSGi bundle

* Is-a JVM library
* Has an OSGi specific identifier, determined by Bundle-Symbolicname and Bundle-Version.
* Versions have an OSGi specific versioning scheme.

## JVM command-line application

* Is-a JVM component
* Commonly packaged as:
    * A zip and/or tar archive that bundles the implementation + documentation.
    * A dmg, rpm, deb native package that bundles the implementation + documentation.
    * An installer.
    * An executable jar.

## Native binary

* Has an implementation
    * This is a runtime usage
    * Depends on zero or more native libraries. Each native library may have an associated install path and soname.
* Common variants
    * Operating system
    * Architecture
    * debug vs non-debug
    * multi-threaded vs single-threaded (on windows)
    * Compiler
    * Statically vs dynamically linked
* May bundle static native libraries.

## Native executable

* Is-a native binary
* Commonly packaged as
    * An executable + debug file (on windows)
    * A tar or zip containing the implementation + documentation
    * A native package
    * An installer

## C/C++ executable

* Implementation depends on a language runtime.

## C# executable

## VB executable

## Native library

* Is-a native binary
* Has a set of exported symbols
    * This is a link-time usage.
    * Sometimes a separate file (e.g. .lib on windows), sometimes not.
    * No dependencies
* Commonly packaged as
    * A shared lib + export and debug files (on windows)
    * A static lib + debug files (on windows)
    * A tar or zip containing the implementation + documentation
* Common variants
    * Static vs dynamic.

## C/C++ native library

* Is-a native library
* Has an API
    * This is compile time usage.
    * A set of header files.
    * Depends on the API of zero or more native libraries.
* Implementation depends on a language runtime

## C# native library

## VB native library

## Javascript library

* Provides javascript source and other resources (css, images)
    * This is a runtime usage
    * Depends on zero or more other javascript libraries
* Common variants
    * minified vs non-minified
    * minimal set of source files vs all source files
* May bundle other javascript libraries
* Commonly packaged as
    * A zip of javascript, css and resources
    * Individual source files
    * A single merged source file

## Web application

* Is-a JVM component
* Has an implementation
    * This is a runtime usage
    * Depends on the implementation of zero or more other web applications, jvm libraries, javascript libraries.
    * Depends on a j2ee runtime.
* May bundle other web applications, jvm libraries, javascript libraries.
* Common variants
    * Deployment environment, e.g. QA vs production
* Commonly packaged as
    * A war
    * A command-line application

## J2EE module

* Is-a JVM library
* API and implementation depend on j2ee API and runtime respectively.

## EJB module

* Is-a J2EE module

## Resource adapter module

* Is-a J2EE module

## J2EE application

* May bundle jvm libraries, j2ee modules, web applications.

## Android application

* Is-a JVM component
* Packaged as a .apk artefact
* Common variants
    * Debug vs release

## Android library

* Can only be packaged as source?

## iPhone application

* Is-a native application

## Database schema

## Flex library

## Flex application

## Website/documentation

# Mapping to a binary repository

## Gradle

We should think about introducing a native Gradle component descriptor. This would describe a component build. Probably as an XML file, with a
versioned schema per component type, or perhaps group of components, along with an extensible 'define your own' component type.

This descriptor would be published as an artefact of the component build, regardless of whether the build is being published to ivy or maven or some
other repository.

## Ivy

A few options:

1. Map a project to an ivy module, encode each (component, usage, variant) as an Ivy configuration.
2. Map each (component, variant) to an ivy module, encode each usage as an Ivy configuration.
3. Map each component to an ivy module, encode each (usage, variant) as an Ivy configuration.
4. Map each component to an ivy module, encode each usage as an Ivy configuration. Fail if > 1 variant.
5. Map each component to an ivy module, encode each usage as an Ivy configuration. Attach variant to each artefact. Fail if any usage does not have
   the same set of dependencies for every variant.
6. Map each component to an ivy module. Map each variant to an ivy module as well.

The mapping for each component type will be versioned, with the mapping version included in the `ivy.xml` as an extra attribute.

## Maven

Similar to above, except encoding using hardcoded scopes + artefact classifiers. Can only really map JVM components.

The mapping for each component type will be versioned, with the mapping version included in the pom.xml somehow (perhaps in the `<properties>` section).

# DSL

Component:

* PublishArtifactsSet is-a Buildable
* PublishArtifactSet has-a set of PublishArtifacts
* ConfigurablePublishArtifactSet is-a PublishArtifactSet that allows composition, etc, similar to ConfigurableFileCollection.
* Component has-a ConfigurablePublishArtifactSet
* Project has-a set of named Components

Publication:

* Publication has-a ConfigurablePublishArtifactSet
* SigningArtifactSet is-a PublishArtifactSet
* SigningArtifactSet transforms a PublishArtifactSet, and contains a signature artifact for each artefact in the original set.
* ChecksumArtifactSet, PomArtifactSet, IvyXmlArtifactSet do similar things.
* Project has-a set of named Publications

Task rules:

* `assemble${componentName}` - builds the artefacts of the component (alternatively, the rule might just be `${componentName}`).
* `assemble${publicationName}` - builds the artefacts of the publication.
* `publish${publicationName}` - builds and publishes the artefacts of the publication to all repositories.
* `assemble` - builds all components
* `publish` - builds and publishes all publications.

Applying a convention plugin (java, cpp-exe, application, war, etc) would add components of the appropriate type. The java plugin would add a java
library, war plugin would add a web application, and so on.

Applying the maven plugin would add a maven-style publication for each component, and probably define publish tasks that attach each maven publication
to each maven repository.

Applying the ivy plugin would add an ivy-style publication for each component, and the appropriate publish tasks.

Applying the signing base plugin (whether that's the current signing plugin or a new one) would add the capability to sign Publications (or probably
PublishArtifactSet more generally), but not actually add any signatures. Applying the signing convention plugin , would add signatures to each remote publication.


# Other stuff to consider

* Network and web services are a usage, and should be considered as a runtime dependency.
* A component that provides a web service may publish wsdl descriptors or a client library or both.
* A component has a lifecycle.
* A component may have an API, or more generally, a contract for its behaviour. Contracts are versioned, and can be used in dependency declarations to
  select compatible builds. Contracts are strongly typed (e.g. wsdl, com idl, java API, db schema).
* A component generally has a runtime dependency on external configuration. Like other dependencies, some of this configuration may be bundled into
  the artefacts, and some will need to be provided by the environment at runtime.
* Interop with other ivy and maven mappings.
* Backwards compatibility:
    * Consuming ivy/maven modules published by older Gradle versions, by Maven, and with hand-coded ivy.xml meta-data.
    * Older Gradle versions consuming modules published by newer Gradle versions.
