## New and noteworthy

Here are the new features introduced in this Gradle release.

### Performance improvements

TODO - needs some detail

### Improved performance of Gradle Daemon via class reuse

The [Gradle Daemon](userguide/gradle_daemon.html) is now much smarter about reusing classes across builds.
This makes all Gradle builds faster when using the Daemon, and builds that use non-core plugins in particular.
This feature is completely transparent and applies to all builds.

The Daemon is a persistent process.
For a long time it has reused the Gradle core infrastructure and plugins across builds.
This allows these classes to be loaded _once_ during a “session”, instead of for each build (as is the case when not using the Daemon).
The level of class reuse has been greatly improved in Gradle 2.4 to also cover build scripts and third-party plugins.

This improves performance in several ways.
Class loading is expensive and by reusing classes this just happens less.
Classes also reside in memory and with the Daemon being a persistent process reuse also reduces memory usage.
This also reduces the severity of class loader leaks (because fewer class loaders actually leak) which again reduces memory usage.

Perhaps more subtly, reusing classes across builds also improves performance by giving the JVM more opportunity to optimize the code.
The optimizer typically improves build performance _dramatically_ over the first half dozen builds in a JVM.

The [Tooling API](userguide/embedding.html), which allows Gradle to be embedded in IDEs automatically uses the Gradle Daemon.
The Gradle integration in IDEs such as Android Studio, Eclipse, IntelliJ IDEA and NetBeans also benefits from these performance improvements.

If you aren't using the [Gradle Daemon](userguide/gradle_daemon.html), we urge you to try it out with Gradle 2.4.

### Support for AWS S3 backed repositories

Gradle now supports S3 backed repositories. Here's an example on how to declare a S3 backed Maven repository in Gradle:

    repositories {
        maven {
            url "s3://someS3Bucket/maven2"
            credentials(AwsCredentials) {
                accessKey "someKey"
                secretKey "someSecret"
            }
        }

        ivy {
            url "s3://someS3Bucket/ivy"
            credentials(AwsCredentials) {
                accessKey "someKey"
                secretKey "someSecret"
            }
        }
    }

S3 backed repositories can be used with both the `ivy-publish` and `maven-publish` plugins, as well as an Ivy repository associated with an `Upload` task.

A big thank you goes to Adrian Kelly for implementing this feature.

### Can use `maven-publish` for publishing via SFTP to Maven repositories

- TBD

### Google Test support (i)

- TBD

### Model rules

A number of improvements have been made to the model rules execution used by the native language plugins:

- Added a basic `model` report to allow you to see the structure of the model for a particular project.
- `@Defaults` annotation allow logic to be applied to attach defaults to a model element.
- `@Validate` annotation allow logic to be applied to validate a model element after it has been configured.
- `CollectionBuilder` allows rules to be applied to all elements in the collection, or to a particular element, or all elements of a given type.

TODO - performance improvements
TODO - creation DSL
TODO - changes to `ManagedSet` and `CollectionBuilder`
TODO - other improvements

### Tooling API improvements

There is a new API `GradleProject#getProjectDirectory` that returns the project directory of the project.

There is a new API `GradleEnvironment#getGradleUserHome` that returns the Gradle user home directory used for all operations that are requested through the Tooling API.

You can now listen to test progress through `LongRunningOperation#LongRunningOperation#addTestProgressListener`. All received
test progress events are of a sub-type of `TestProgressEvent`.

### Dependency substitution accepts projects

You can now replace an external dependency with a project dependency. The `DependencyResolveDetails` object
allows access to the `ComponentSelector` as well:

    resolutionStrategy {
        eachDependency { details ->
            if (details.selector instanceof ModuleComponentSelector && details.selector.group == 'com.example' && details.selector.module == 'my-module') {
                useTarget project(":my-module")
            }
        }
    }

### Unique Maven snapshots

TODO

### Parallel Native Compilation

Gradle uses multiple concurrent compilation processes when compiling C/C++/Objective-C/Objective-C++/Assembler languages. This is automatically enabled for all builds. 
Up until this release, Gradle compiled all native source files sequentially. 

### New --workers option and system property

As a new incubating feature, you can control the number of "workers" Gradle is allowed to use. Currently, only parallel native compilation is influenced by this setting.
The default is the number of available processors.

You can specify the number of workers on the command line with --workers=N or in your gradle.properties by setting org.gradle.workers.max.

### Support for “annotation processing” of Groovy code

It is now possible to use Java's [“annotation processing”](https://docs.oracle.com/javase/7/docs/api/javax/annotation/processing/Processor.html) with Groovy code.
This, for example, allows using the [Dagger](http://square.github.io/dagger) dependency injection library, that relies on annotation processing, with Groovy code.

Annotation processing is a Java centric feature.
Support for Groovy is achieved by having annotation processors process the Java “stubs” that are generated from Groovy code.
The stubs convey the structure of the class, which is typically used to allow Java code to compile against the Groovy code in “one pass”.
Annotations on structural elements (i.e. classes/methods/fields) will be present in the generated stubs.
Annotation processors will detect such annotations on stubs as they would with “normal” Java code.

The support for annotation processing of Groovy code is limited to annotation processors that generate new classes, and not to processors that modify annotated classes.
The official and supported annotation processing mechanisms _do not_ support modifying classes, so almost all annotation processors will work.
However, some popular annotation processing tools, notably [Project Lombok](http://projectlombok.org), that use unofficial API to modify classes will not work.

This feature was contributed by [Will Erickson](https://github.com/Sarev0k).

### Generate wrapper with specific version from command-line

Previously to generate a Gradle wrapper with a specific version, or a custom distribution URL,
you had to change the `build.gradle` file to contain a wrapper task with a configured `gradleVersion` property.

Now the target Gradle version or the distribution URL can be configured from the command-line, without having
to add or modfify the task in `build.gradle`:

    gradle wrapper --gradle-version 2.3

And with a distribution URL:

    gradle wrapper --gradle-distribution-url https://myEnterpriseRepository:7070/gradle/distributions/gradle-2.3-bin.zip

### Customization of application plugin start script generation

The [application plugin](http://gradle.org/docs/current/userguide/application_plugin.html) provides a task named `startScripts` responsible for generating OS-specific start scripts for a Java-based
application. Generation-relevant data like the main classname or the classpath can be tweaked through the
[task's properties](http://gradle.org/docs/current/dsl/org.gradle.api.tasks.application.CreateStartScripts.html). At runtime the task feeds this data to an internal class responsible for generating
Unix and Windows start scripts. While these properties allow for a certain level of customization, the approach is limiting. A user cannot easily change the generation logic or any of the templates
used for generating the scripts.

In this release, we enhanced the API available to the task type `org.gradle.api.tasks.application.CreateStartScripts`. The class now exposes two properties of type
`org.gradle.api.scripting.ScriptGenerator` responsible for the script generation: one for the Unix script generation named `unixStartScriptGenerator` and one for
the Windows script generation named `windowsStartScriptGenerator`. By default Gradle assigns instances of `ScriptGenerator` implementing the logic you know from previous releases.

#### Providing a custom script generation implementation

Let's say you'd want to provide your own implementation for generating start scripts. It's as simple as writing your own implementation of `ScriptGenerator`. `ScriptGenerator`
requires you to implement a single method `void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination)`. The parameter of type
`org.gradle.api.scripting.JavaAppStartScriptGenerationDetails` represents the data e.g. classpath, application name. The parameter of type `java.io.Writer` writes to the target
start script file. The following example demonstrates the use case:

    startScripts {
        unixStartScriptGenerator = new CustomUnixStartScriptGenerator()
        windowsStartScriptGenerator = new CustomWindowsStartScriptGenerator()
    }

    class CustomUnixStartScriptGenerator implements ScriptGenerator<JavaAppStartScriptGenerationDetails> {
        void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
            try {
                destination << "\${details.applicationName} start up script for UN*X"
                destination.flush()
            } finally {
                destination.close()
            }
        }
    }

    class CustomWindowsStartScriptGenerator implements ScriptGenerator<JavaAppStartScriptGenerationDetails> {
        void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
            try {
                destination << "\${details.applicationName} start up script for Windows"
                destination.flush()
            } finally {
                destination.close()
            }
        }
    }

#### Changing the default script template

Sometimes you just want to change the underlying template used for the script generation. For that purpose the default implementations of `ScriptGenerator` also implement
the interface `org.gradle.api.scripting.TemplateBasedScriptGenerator`. `TemplateBasedScriptGenerator` exposes a method for setting the template:
`void setTemplate(Reader reader)`. The following code snippets shows how to assign your own templates:


    startScripts {
        unixStartScriptGenerator.template = new FileReader('customUnixStartScript.txt')
        windowsStartScriptGenerator.template = new FileReader('customWindowsStartScript.txt')
    }

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://forums.gradle.org).

<!--
### Example deprecation
-->

### Setting number of threads with --parallel-threads

Gradle still honors --parallel-threads for inter-project parallelization, but the method of specifying the number of worker threads will be changing in future releases.
As we add more parallelized work to Gradle, we need a more generic way of controlling the number of workers (threads, processes, etc) Gradle may use.

If you were using --parallel-threads to enable parallel-project execution, please consider using just --parallel.

If you were using StartParameter.getParallelThreadCount() to check if parallel-project execution was enabled, please consider using StartParameter.isParallelProjectExecutionEnabled().

### Changing a configuration after it has been resolved

TODO

### Dependency substitution changes

In previous Gradle versions you could replace an external dependency with another like this:

    resolutionStrategy {
        eachDependency { details ->
            if (details.requested.group == 'com.example' && details.requested.module == 'my-module') {
                useVersion '1.3'
            }
        }
    }

This method has been deprecated, and a new DSL has been introduced:

    resolutionStrategy {
        dependencySubstitution {
            withModule("com.example:my-module") { useVersion "1.3" }
        }
    }

There are other options available to match module and project dependencies:

    all { DependencySubstitution<ComponentSelector> details -> /* ... */ }
    eachModule() { ModuleDependencySubstitution details -> /* ... */ }
    withModule("com.example:my-module") { ModuleDependencySubstitution details -> /* ... */ }
    eachProject() { ProjectDependencySubstitution details -> /* ... */ }
    withProject(project(":api)) { ProjectDependencySubstitution details -> /* ... */ }

Note that only `ModuleDependencySubstitution` has the `useVersion()` method. For the other substitutions you can use `useTarget()`:

    resolutionStrategy {
        dependencySubstitution {
            withProject(project(":api")) { useTarget group: "org.utils", name: "api", version: "1.3" }
        }
    }

### Lifecycle plugin changes

Defining custom tasks named `build`, `clean` or `check` when using the standard Gradle lifecycle plugins (e.g. the `base` plugin) has been deprecated.

### Changes to methods of `LogLevel`

All usages of methods of `org.gradle.api.logging.LogLevel` have been removed from Gradle codebase therefore all methods defined by that enum type have been deprecated.

## Potential breaking changes

### Model DSL changes

There have been some changes to the behaviour of the `model { ... }` block:

- The `tasks` container now delegates to a `CollectionBuilder<Task>` instead of a `TaskContainer`.
- The `components` container now delegates to a `CollectionBuilder<ComponentSpec>` instead of a `ComponentSpecContainer`.
- The `binaries` container now delegates to a `CollectionBuilder<BinarySpec>` instead of a `BinaryContainer`.

Generally, the DSL should be the same, except:

- Elements are not implicitly created. In particular, to define a task with default type, you need to use `model { tasks { myTask(Task) { ... } }`
- Elements are not created or configured eagerly, but are configured as required.
- The `create` method returns void.
- The `withType()` method selects elements based on the public contract type rather than implementation type.
- Using create syntax fails when the element already exists.
- There are currently no query method on this interface.

### Updated default zinc compiler version

The default zinc compiler version has changed from 0.3.0 to 0.3.5.3

### MavenDeployer no longer uses global Maven settings.xml

- User settings file was never used, but global settings.xml was considered
- Mirror settings no longer cause GRADLE-2681
- Authentication and Proxy settings are not used

- Local repository location in user settings.xml _is_ honoured when deploying (it was always honoured when installing)

### PublishToMavenLocal task ignores repository setting

Previously, the `PublishToMavenLocal` task could be configured with an `ArtifactRepository` instance, which would specify the
location to `install` to. The default repository was `mavenLocal()`.

It is no longer possible to override this location by supplying a repository to the `PublishToMavenLocal` task. Any supplied repository
will be ignored.

### DependencyResolveDetails.getTarget() returns a `ComponentSelector`

The `getTarget()` method on the now deprecated `DefaultDependencyResolveDetails` returns a `ComponentSelector` instead of a `ModuleVersionSelector`.

### CommandLineToolConfiguration.withArguments() semantics have changed

`withArguments()` used to be called just before Gradle built the command-line arguments for the underlying tool for each source file.
The arguments passed to this would include the path to the source file and output file. This hook was intended to capture "overall"
arguments to the command-line tool instead of "per-file" arguments. We've changed it so that `withArguments()` is called once per
task execution and does not contain any specific file arguments.  Changes to arguments using this method will affect all source files.

### Implicit Groovy source compilation while compiling build script is now disabled

The Groovy compiler by default looks for dependencies in source form before looking for them in class form.
That is, if Groovy code being compiled references `foo.bar.MyClass` then the compiler will look for `foo/bar/MyClass.groovy` on the classpath.
If it finds such a file, it will try to compile it.
If it doesn't it will then look for a corresponding class file.

As of Gradle 2.4, this feature has been disabled for _build script_ compilation.
It does not affect the compilation of “application” Groovy code (e.g. `src/main/groovy`).
It has been disabled to make build script compilation faster.

If you were relying on this feature, please use the [`buildSrc` feature](userguide/organizing_build_logic.html#sec:build_sources) as a replacement.

### Changes to Groovy compilation when annotation processors are present

When annotation processors are “present” for a Groovy compilation operation, all generated stubs are now compiled regardless of whether they are required or not.
This change was required in order to have annotation processors process the stubs.
Previously the stubs were made available to the Java code under compilation via the source path, which meant that only classes actually referenced by Java code were compiled.
The implication is that more compilation is now required for Groovy code when annotation processors are present, which means longer compile times.

This is unlikely to be noticeable unless the code base contains a lot of Groovy code.
If this is problematic for your build, the solution is to separate the code that requires annotation processing from the code that does not to some degree.

### Changes to default value for Java compilation `sourcepath`

The source path indicates the location of source files that _may_ be compiled if necessary.
It is effectively a complement to the class path, where the classes to be compiled against are in source form.
It does __not__ indicate the actual primary source being compiled.

The source path feature of the Java compiler is rarely needed for modern builds that use dependency management.

The default value for the source path as of this release is now effectively an empty source path.
Previously Gradle implicitly used the same default as the `javac` tool, which is the `-classpath` value.
This causes unexpected build results when source accidentally ends up on the classpath, which can happen when dependencies surprisingly include source as well as binaries.

This improvement was contributed by [Thomas Broyer](https://github.com/tbroyer).

### Changes in behaviour of AuthenticationSupported.getCredentials()

`AuthenticationSupported.getCredentials()` now throws an IllegalStateException if the configured credentials are not of type `PasswordCredentials`.

### Changes to API of AntlrTask

The AntlrTask previous unnecessarily exposed the internal methods `buildArguments()` and `evaluateAntlrResult()`.
These methods have been removed.

### Updated libraries used by the Gradle API

Some dependencies used in Gradle have been updated.

* **Slf4j** - 1.7.7 to 1.7.10
* **Groovy** - 2.3.9 to 2.3.10
* **Ant** - 1.9.3 to 1.9.4

These libraries are expected to be fully backwards compatible.
It is expected that no Gradle builds will be negatively affected by these changes.

### Updated default tool versions for code quality plugins

The default version of the corresponding tool of the following code quality plugins have been updated:

* The `checkstyle` plugin now uses version 5.9 as default (was 5.7).
    (Note: The latest checkstyle version currently available is 6.4.1 but be aware that this version is not java 1.6 compliant)
* The `pmd` plugin now uses version 5.2.3 as default (was 5.1.1).
* The `findbugs` plugin now uses version 3.0.1 as default (was 3.0.0).
* The `codenarc` plugin now uses version 0.23 as default (was 0.21).

### Repository credentials

TODO - methods of `AuthenticationSupported` now work slightly differently, in particular will fail when credentials are not instance of `PasswordCredentials`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Adrian Kelly](https://github.com/adrianbk)
    - Support for resolving from AWS S3 backed Maven and Ivy repositories
    - Support for publishing to AWS S3 backed Maven and Ivy repositories
    - Don't run assemble task in pull-request validation builds on [travis-ci](https://travis-ci.org/gradle/gradle/builds)
* [Daniel Lacasse](https://github.com/Shad0w1nk) - support GoogleTest for testing C++ binaries
* [Victor Bronstein](https://github.com/victorbr)
    - Convert NotationParser implementations to NotationConverter
    - Only parse Maven settings once per project to determine local Maven repository location (GRADLE-3219)
* [Vyacheslav Blinov](https://github.com/dant3) - fix for `test.testLogging.showStandardStreams = false` (GRADLE-3218)
* [Michal Bendowski](https://github.com/bendowski-google) - six webDist userguide example
* [Daniel Siwiec](https://github.com/danielsiwiec) - update `README.md`
* [Andreas Schmid](https://github.com/aaschmid) - add test coverage for facet type configuration in `GenerateEclipseWtpFacet`
* [Roman Donchenko](https://github.com/SpecLad)
    - Fix PatternSet so that all files are not excluded when Ant global excludes are cleared (GRADLE-3254)
    - Specs.or: use satisfyAll/None instead of instantiating an anonymous class
    - Fix a bug in `org.gradle.api.specs.OrSpecTest`
* [Lorant Pinter](https://github.com/lptr), [Daniel Vigovszky](https://github.com/vigoo) and [Mark Vujevits](https://github.com/vujevits) - implement dependency substitution for projects
* [Lorant Pinter](https://github.com/lptr) - add setting wrapper version on command-line
* [Andreas Schmid](https://github.com/aaschmid) - Retain defaults when using `EclipseWtpComponent.resource()` and  `EclipseWtpComponent.property()`
* [Mikolaj Izdebski](https://github.com/mizdebsk) - Use hostname command as fallback way of getting build host name in Gradle build
* [Andrea Cisternino](https://github.com/acisternino) - Make JavaFX available to Groovy compilation on Java 8
* [Will Erickson](https://github.com/Sarev0k) - Support for annotation processing of Groovy code
* [Noam Y. Tenne](https://github.com/noamt) - Declare a dependency on a specific timestamped Maven snapshot
* [Thomas Broyer](https://github.com/tbroyer) - Better defaults for Java compilation source path

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
