The big story for Gradle 2.4 is the improved performance.
While it's not unusual for a new Gradle release to be the fastest Gradle yet, Gradle 2.4 is _significantly_ faster.
Many early testers of Gradle 2.4 have reported that overall build times have improved by 20% up to 40%.

There are two main components to the improved performance; [general configuration time optimizations](#significant-configuration-time-performance-improvements),
and the [class reuse with the Gradle Daemon](#improved-performance-of-gradle-daemon-via-class-reuse).
As such, using the [Gradle Daemon](userguide/gradle_daemon.html) now provides an even greater performance advantage.

If you are using Gradle to build native code (e.g. C/C++), the news gets even better with the introduction of [parallel compilation](#parallel-native-compilation).

Other highlights include [support for Amazon S3 dependency repositories](#support-for-amazon-web-services-s3-backed-repositories) 
and [support for using annotation processors with Groovy code](#support-for-“annotation-processing”-of-groovy-code) and more. 

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Significant configuration time performance improvements

Gradle 2.4 features a collection of performance improvements particularly targeted at “configuration time”
(i.e. the part of the build lifecycle where Gradle is comprehending the definition of the build by executing build scripts and plugins).
Several users of early Gradle 2.4 builds have reported build time improvements of around 20% just by upgrading to Gradle 2.4.

Most performance improvements were realized by optimizing internal algorithms along with data and caching structures.
Builds that have more configuration (i.e. more projects, more build scripts, more plugins, larger build scripts) stand to gain more from the improvements.
The Gradle build itself, which is of non trivial complexity, realized improved configuration times of 34%.
Stress tests run as part of Gradle's own build pipeline have demonstrated an 80% improvement in configuration time with Gradle 2.4.

No change is required to builds to leverage the performance improvements.

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

### Parallel native compilation

Starting with 2.4, Gradle uses multiple concurrent compilation processes when compiling C/C++/Objective-C/Objective-C++/Assembler languages. 
This is automatically enabled for all builds and works for all supported compilers (GCC, Clang, Visual C++). 
Up until this release, Gradle compiled all native source files sequentially.

This change has dramatic performance implications for native builds.
Benchmarks for a project with a 500 source files on a machine with 8 processing cores available exhibited reduced build times of 53.4s to 12.9s. 

The degree of concurrency is determined by the new [“max workers” build setting](#new-“max-workers”-build-setting).

### New “max workers” build setting (i)

The new `--max-workers=«N»` command line switch, and synonymous `org.gradle.workers.max=«N»` build property (e.g. specified in `gradle.properties`) determines the degree of build concurrency.

As of Gradle 2.4, this setting influences [native code compilation](#parallel-native-compilation) and [parallel project execution](userguide/multi_project_builds.html#sec:parallel_execution).
The “max workers” setting specifies the size of these _independent_ worker pools.
However, a single worker pool is used for all native compilation operations.
This means that if two (or more) native compilation tasks are executing at the same time, 
they will share the worker pool and the total number of concurrent compilation options will not exceed the “max workers” setting.

Future versions of Gradle will leverage the shared worker pool for more concurrent work, allowing more precise control over the total build concurrency.

The default value is the number of processors available to the build JVM (as reported by 
[`Runtime.availableProcessors()`](http://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors\(\))).
Alternatively, it can be set via the `--max-workers=«N»` command line switch or `org.gradle.workers.max=«N»` build property where `«N»` is a positive, non-zero, number.

Please note: the `--parallel-threads` command line switch [has been deprecated](#setting-number-of-build-execution-threads-with---parallel-threads) in favor of this new setting.

### Reduced memory consumption when compiling Java source code with Java 7 and 8

By working around JDK bug [JDK-7177211](https://bugs.openjdk.java.net/browse/JDK-7177211), Java compilation requires less memory in Gradle 2.4.
This JDK bug causes what was intended to be a performance improvement to not improve compilation performance and use more memory.
The workaround is to implicitly apply the internal compiler flag `-XDuseUnsharedTable=true` to all compilation operations.

Very large Java projects (building with Java 7 or 8) may notice dramatically improved build times due to the decreased memory throughput which in turn
requires less aggressive garbage collection in the build process.

### Support for Amazon Web Services S3 backed repositories (i)

It is now possible to consume dependencies from, and publish to, [Amazon Web Services S3](http://aws.amazon.com/s3) stores
when using [`MavenArtifactRepository`](dsl/org.gradle.api.artifacts.repositories.MavenArtifactRepository.html) 
or [`IvyArtifactRepository`](dsl/org.gradle.api.artifacts.repositories.IvyArtifactRepository.html).

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

Downloading dependencies from S3 is supported for Maven and Ivy type repositories as above.

Publishing to S3 is supported with both the [`ivy-publish`](userguide/publishing_ivy.html) and [`maven-publish`](userguide/publishing_maven.html) plugins, 
as well as when using an [`IvyArtifactRepository`](dsl/org.gradle.api.artifacts.repositories.IvyArtifactRepository.html) with an [`Upload`](dsl/org.gradle.api.tasks.Upload.html) task
(see section [8.6. Publishing artifacts](userguide/artifact_dependencies_tutorial.html#N10669) of the User Guide).

Please see section [50.6. Repositories](userguide/dependency_management.html#sec:repositories) of the User Guide for more information on configuring S3 repository access.

This feature was contributed by [Adrian Kelly](https://github.com/adrianbk).

### Support for publishing to Maven repositories over SFTP using `maven-publish` plugin

In previous releases, it was not possible to publish to a Maven repository (via the [`maven-publish` plugin](userguide/publishing_maven.html))
via SFTP in the same manner that it was for an Ivy repository or when downloading dependencies.
This restriction has been lifted, with the publishing now supporting all of the transport protocols that Gradle currently supports (`file`, `http(s)`, `sftp` and `s3`).

This change will also make it possible to seamlessly use any transports that Gradle will support in the future at that time.

Please see section [50.6. Repositories](userguide/dependency_management.html#sec:repositories) of the User Guide for more information on configuring SFTP repository access.

### Depending on a particular Maven snapshot version

It is now possible to depend on particular Maven snapshot, rather than just the “latest” published version.

    depenencies {
      compile "org.company:my-lib:1.0.0-20150102.010203-20"
    }
    
The Maven snapshot version number is a timestamp and snapshot number.
The snippet above is depending on the snapshot of version `1.0.0` published on the 2nd of January 2015, at 01:02:03 AM which was the 20th snapshot published.

This feature was contributed by [Noam Y. Tenne](https://github.com/noamt).

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
to add or modify the task in `build.gradle`:

<pre><tt>gradle wrapper --gradle-version 2.3</tt></pre>

And with a distribution URL:

<pre><tt>gradle wrapper --gradle-distribution-url https://myEnterpriseRepository:7070/gradle/distributions/gradle-2.3-bin.zip</tt></pre>

This feature was contributed by [Lóránt Pintér](https://github.com/lptr).

### Customization of application plugin start script generation (i)

The [application plugin](userguide/application_plugin.html) can be used to create “executable” distributions Java-based application, including operating system specific start scripts.
While certain values in the generated scripts (e.g. main class name, classpath) were customizable, the script content was generally hardcoded and cumbersome to change.
With Gradle 2.4, it is now much easier to fully customise the start scripts.

The generation of the scripts is performed by a [`CreateStartScripts`](dsl/org.gradle.jvm.application.tasks.CreateStartScripts.html) task.
Please consult its [DSL reference](dsl/org.gradle.jvm.application.tasks.CreateStartScripts.html) for customization examples.

### Tooling API improvements (i)

The tooling API allows Gradle to be embedded in other applications, such as an IDE. This release includes a number of improvements to the tooling API:

An application can now receive test progress events as a build is executed. Using these events, an application can display or report on test execution
as tests run during the build.

An application receives test events using the
[`LongRunningOperation.addTestProgressListener()`](javadoc/org/gradle/tooling/LongRunningOperation.html#addTestProgressListener-org.gradle.tooling.TestProgressListener-) method.

The following additions have been added to the respective [Tooling API](userguide/embedding.html) models:

* [`GradleProject.getProjectDirectory()`](javadoc/org/gradle/tooling/model/GradleProject.html#getProjectDirectory--) returns the project directory of the project.
* [`GradleEnvironment.getGradleUserHome()`](javadoc/org/gradle/tooling/model/build/GradleEnvironment.html#getGradleUserHome--)
returns the effective Gradle user home directory that will be used for operations performed through the Tooling API.

### Rule based model configuration improvements 

A number of improvements have been made to the rule based model configuration used by the native language plugins in this release.
There is also a [new User Guide chapter that explains just what “rule based model configuration” is](userguide/new_model.html).

Key additions and improvements in Gradle 2.4:

- A basic [model report](userguide/new_model.html#N17A51) task that visualizes the model space for a project.
- Addition of [`@Defaults`](javadoc/org/gradle/model/Defaults.html) and [`@Validate`](javadoc/org/gradle/model/Validate.html) lifecycle rules.
- Additions to the API of [`CollectionBuilder`](javadoc/org/gradle/model/collection/CollectionBuilder.html) and 
[`ManagedSet`](javadoc/org/gradle/model/collection/ManagedSet.html) that allow rules to be applied to all elements in the collection, 
or to a particular element, or all elements of a given type.

It is now also possible to [create model elements via the DSL](userguide/new_model.html#N17A0F).

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

### Setting number of build execution threads with `--parallel-threads`

The, incubating, `--parallel-threads` command line switch has been superseded by the new `--max-workers` command line switch and synonymous `org.gradle.workers.max` build property.
Likewise, the [`StartParameter.getParallelThreadCount()`](javadoc/org/gradle/StartParameter.html#getParallelThreadCount\(\)) has also been deprecated.

This setting configured [parallel project execution](userguide/multi_project_builds.html#sec:parallel_execution).

The `--parallel-threads` is still respected, until removed.
If not specified, the value specified for `--max-workers` will be used.

If you were using an invocation such as:

<pre><tt>./gradlew build --parallel-threads=4</tt></pre>

The replacement is now:

<pre><tt>./gradlew build --max-workers=4 --parallel</tt></pre>

Alternatively, the following can be used, which will use the default value for `--max-workers`:

<pre><tt>./gradlew build --parallel</tt></pre>

### Lifecycle plugin changes

The tasks `build`, `clean`, `assemble` and `check` are part of the standard build lifecycle and are added by most plugins, typically implicitly through the `base` or `language-base` plugins.
Due to the way these tasks are implemented, it is possible to redefine them simply by creating your own task of the same name.
This behavior has been deprecated and will not be supported in Gradle 3.0.
That is, attempting to define a task with the same name as one of these lifecycle tasks when they are present will become an error just like any other attempt to create a task with the same name as an existing task.

## Potential breaking changes

### Incompatibility with Team City 9.0.3 and earlier

An internal change to Gradle test execution has broken the Gradle integration provided by [JetBrains' TeamCity integration server](https://www.jetbrains.com/teamcity/).
JetBrains have acknowledged the issue and have fixed the problem for the pending 9.0.4 release of TeamCity.

Please see [https://youtrack.jetbrains.com/issue/TW-40615](https://youtrack.jetbrains.com/issue/TW-40615) for more information on the problem.

If you are affected by this issue, you can install a pre-release version of the Gradle plugin for TeamCity which is available via the above link.

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

### Updated default Scala Zinc compiler version

The default version of the Scala Zinc compiler has changed from 0.3.0 to 0.3.5.3.

### `MavenDeployer` no longer uses global Maven `settings.xml`

When publishing to a Maven repository using an `Upload` task (e.g. `uploadArchives`) and the `mavenDeployer` repository type,
the global Maven `settings.xml` file is no longer consulted.

Previously, the “mirror”, “authentication” and “proxy” settings defined in the global `settings.xml` file were effecting publishing,
making builds less portable which is no longer the case.

This resolves [GRADLE-2681].

The _user_ settings file (i.e. `~/.m2/settings.xml`) is still consulted for the location of the local repository when performing a local install
(e.g. `gradle install` or `gradle publishToMavenLocal`).

### `PublishToMavenLocal.repository` property has been removed

Previously, the [`PublishToMavenLocal`](javadoc/org/gradle/api/publish/maven/tasks/PublishToMavenLocal.html) task 
(as used by the [`maven-publishing` plugin](userguide/publishing_maven.html)) could be configured with an `ArtifactRepository` instance, 
which would specify the location to install to.
The default repository was the repository that returned by
[`RepositoryHandler.mavenLocal()`](dsl/org.gradle.api.artifacts.dsl.RepositoryHandler.html#org.gradle.api.artifacts.dsl.RepositoryHandler:mavenLocal\(\)).

It is no longer possible to provide an arbitrary repository to this task.
If you need to publish to an arbitrary repository, please use the [`PublishToMavenRepository`](javadoc/org/gradle/api/publish/maven/tasks/PublishToMavenRepository.html) task type instead.

### Changed default assembler executable for GCC/Clang tool chains

The default tool used when turning assembler into object files is now `gcc` or `clang` instead of `as`.  
Some arguments that were specific to `as` were converted to their GCC/Clang equivalents.  
If you were passing arguments to the assembler, you may now need to use `-Wa` when passing them.  

Please see [GCC's documentation](https://gcc.gnu.org/onlinedocs/gcc/Assembler-Options.html) for passing arguments to the assembler.

### Changes to `CommandLineToolConfiguration.withArguments()` usage

The [`CommandLineToolConfiguration`](javadoc/org/gradle/nativeplatform/toolchain/CommandLineToolConfiguration.html) allows fine grained configuration of a tool execution for the native domain,
for example when configuring a [`GccPlatformToolChain`](javadoc/org/gradle/nativeplatform/toolchain/GccPlatformToolChain.html).
There are changes to the semantics of the `withArguments()` method.

The `withArguments()` method used to be called just before Gradle built the command-line arguments for the underlying tool for each source file.
The arguments passed to this would include the path to the source file and output file. 
This hook was intended to capture "overall" arguments to the command-line tool instead of "per-file" arguments. 
Now, the `withArguments()` method is called once per task execution and does not contain any specific file arguments.  
Any changes to arguments using this method will affect all source files.

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

### Changes to `AuthenticationSupported.getCredentials()` method

Due to the addition of support for [AWS S3 backed repositories](#support-for-amazon-web-services-s3-backed-repositories), some behavioral changes have been made to the
[`AuthenticationSupported`](dsl/org.gradle.api.artifacts.repositories.AuthenticationSupported.html) interface, which is implemented by 
[`MavenArtifactRepository`](dsl/org.gradle.api.artifacts.repositories.MavenArtifactRepository.html) and [`IvyArtifactRepository`](dsl/org.gradle.api.artifacts.repositories.IvyArtifactRepository.html).

The `getCredentials()` and `credentials(Action)` methods now throw an `IllegalStateException` if the configured credentials are not of type 
[`PasswordCredentials`](javadoc/org/gradle/api/artifacts/repositories/PasswordCredentials.html), now that it is possible to use different types of credentials.

### Changes to API of `AntlrTask`

The [`AntlrTask`](dsl/org.gradle.api.plugins.antlr.AntlrTask.html) previous unnecessarily exposed the internal methods `buildArguments()` and `evaluateAntlrResult()`.

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
   - The latest checkstyle version currently available is 6.4.1 but be aware that this version is not java 1.6 compliant
   - Be aware that there is was a breaking change of the `LeftCurly` rule introduced in checkstyle 5.8 (see https://github.com/checkstyle/checkstyle/issues/247)
* The `pmd` plugin now uses version 5.2.3 as default (was 5.1.1).
* The `findbugs` plugin now uses version 3.0.1 as default (was 3.0.0).
* The `codenarc` plugin now uses version 0.23 as default (was 0.21).

### Deprecated ComponentMetadataHandler.eachComponent() has been removed

This method (and all overloads) has been removed in 2.4, after [being deprecated in Gradle 2.3](http://gradle.org/docs/2.3/release-notes#component-metadata-rule-enhancements)
and superseded by the [`all()` method](javadoc/org/gradle/api/artifacts/dsl/ComponentMetadataHandler.html#all\(org.gradle.api.Action\)).

### Removal of package scoped methods of `LogLevel`

The package scoped methods of the [`LogLevel`](javadoc/org/gradle/api/logging/LogLevel.html) enumeration have been removed.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Adrian Kelly](https://github.com/adrianbk) - Support for Amazon Web Services S3 dependency repositories
* [Victor Bronstein](https://github.com/victorbr)
    - Convert NotationParser implementations to NotationConverter
    - Only parse Maven settings once per project to determine local Maven repository location (GRADLE-3219)
* [Vyacheslav Blinov](https://github.com/dant3) - Fix for `test.testLogging.showStandardStreams = false` (GRADLE-3218)
* [Michal Bendowski](https://github.com/bendowski-google) - Documentation improvements
* [Daniel Siwiec](https://github.com/danielsiwiec) - Documentation improvements
* [Andreas Schmid](https://github.com/aaschmid) - Improved test coverage for facet type configuration in `GenerateEclipseWtpFacet`
* [Roman Donchenko](https://github.com/SpecLad)
    - Fix PatternSet so that all files are not excluded when Ant global excludes are cleared (GRADLE-3254)
    - Improvements to `Spec` implementations
* [Lóránt Pintér](https://github.com/lptr) - Allow setting wrapper version on command-line
* [Andreas Schmid](https://github.com/aaschmid) - Retain defaults when using `EclipseWtpComponent.resource()` and `EclipseWtpComponent.property()`
* [Mikolaj Izdebski](https://github.com/mizdebsk) - Use hostname command as fallback way of getting build host name in Gradle build
* [Andrea Cisternino](https://github.com/acisternino) - Make JavaFX available to Groovy compilation on Java 8
* [Will Erickson](https://github.com/Sarev0k) - Support for annotation processing of Groovy code
* [Noam Y. Tenne](https://github.com/noamt) - Declare a dependency on a specific timestamped Maven snapshot
* [Thomas Broyer](https://github.com/tbroyer) - Better defaults for Java compilation source path

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
