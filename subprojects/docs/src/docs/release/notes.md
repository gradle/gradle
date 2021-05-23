The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community members for their contributions to this release of Gradle:

[Danny Thomas](https://github.com/DanielThomas),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Victor Merkulov](https://github.com/urdak),
[Kyle Moore](https://github.com/DPUkyle),
[Stefan Oehme](https://github.com/oehme),
[Anže Sodja](https://github.com/asodja),
[Jeff](https://github.com/mathjeff),
[Alexander Likhachev](https://github.com/ALikhachev),
[Björn Kautler](https://github.com/Vampire),
[Sebastian Schuberth](https://github.com/sschuberth),
[Kejn](https://github.com/kejn),
[xhudik](https://github.com/xhudik),
[Anuraag Agrawal](https://github.com/anuraaga),
[Florian Schmitt](https://github.com/florianschmitt),
[Evgeny Mandrikov](https://github.com/Godin).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

<!-- 

================== TEMPLATE ==============================

<a name="FILL-IN-KEY-AREA"></a>
### FILL-IN-KEY-AREA improvements

<<<FILL IN CONTEXT FOR KEY AREA>>>
Example:
> The [configuration cache](userguide/configuration_cache.html) improves build performance by caching the result of
> the configuration phase. Using the configuration cache, Gradle can skip the configuration phase entirely when
> nothing that affects the build configuration has changed.

#### FILL-IN-FEATURE
> HIGHLIGHT the usecase or existing problem the feature solves
> EXPLAIN how the new release addresses that problem or use case
> PROVIDE a screenshot or snippet illustrating the new feature, if applicable
> LINK to the full documentation for more details 

================== END TEMPLATE ==========================


==========================================================
ADD RELEASE FEATURES BELOW
vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv

## Default JaCoCo version upgraded
[The JaCoCo plugin](userguide/jacoco_plugin.html) has been upgraded to the most recent [JaCoCo version 0.8.7](http://www.jacoco.org/jacoco/trunk/doc/changes.html) which includes experimental support for Java 17. 

^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
ADD RELEASE FEATURES ABOVE
==========================================================

-->

## New features and usability improvements

### Improved incremental compilation for Java

The Java incremental compiler received substantial improvements in this release:

1. incremental compilation analysis is now stored in the build cache
2. incremental compilation analysis is faster, uses less memory and disk space
3. change of a constant in an upstream dependency doesn't trigger a full recompilation anymore

In previous Gradle releases, incremental compilation analysis was only stored locally.
This means that if the compile task outputs were fetched from the build cache, then no incremental compilation was performed on the next build: the initial build after a fetch from the cache was fast, but the next one was always a full recompilation.
In Gradle 7.1 this is no longer the case, as the result of the analysis is now stored in the build cache, meaning that the first compilation after fetching from the build cache will be incremental.

The next improvement is about performance: incremental compilation analysis adds overhead to compilation, because Gradle needs to extract symbols from class files, analyze a transitive graph of dependencies to determine the consumers of a particular symbol, etc.
In Gradle 7.1, we have significantly reduced this overhead, as well as reduced the cost of storage of the analysis.
On the `gradle` project itself, we were able to make incremental compilation up to twice as fast!

Last, because of the way the Java compiler works, previous Gradle releases were forced to perform a full recompilation as soon as _any_ constant was changed in an upstream dependency.
In Gradle 7.1, we introduced a compiler plugin which allows us to perform constant usage tracking, and only recompile the consumers of constants when those change.
This should significantly speedup incremental builds for projects using lots of constants, which is often the case for generated code like template engines.

### Better modeling of command line arguments for compiler daemons

When declaring arguments for a compiler daemon using [jvmArgs](javadoc/org/gradle/api/tasks/compile/BaseForkOptions.html#getJvmArgs--), these arguments are always treated as `String` inputs to the compile task.
However, sometimes these arguments represent additions to the classpath or input files whose contents should be included during incremental builds or when calculating a build cache key.
Better modeling of these arguments can improve the incrementality of the compile task and avoid unnecessary cache misses.

Previously, arguments for the Java compiler invocation could be declared in a rich fashion using [compiler argument providers](javadoc/org/gradle/api/tasks/compile/CompileOptions.html#getCompilerArgumentProviders--), but there was no way to do this for the command line arguments of the compiler daemon process itself.
You can now provide these rich command line arguments to the compiler daemon for [JavaCompile](javadoc/org/gradle/api/tasks/compile/JavaCompile.html), [GroovyCompile](javadoc/org/gradle/api/tasks/compile/GroovyCompile.html), and [ScalaCompile](javadoc/org/gradle/api/tasks/scala/ScalaCompile.html) tasks using [jvmArgumentProviders](javadoc/org/gradle/api/tasks/compile/ProviderAwareForkOptions.html#getJvmArgumentProviders--).
[CommandLineArgumentProvider](javadoc/org/gradle/process/CommandLineArgumentProvider.html) objects configured via `jvmArgumentProviders` will be interrogated for input and/or output annotations and will add these inputs/outputs to the enclosing compile task.

```
def javaAgentProvider = new JavaAgentCommandLineArgumentProvider(file('/some/path/to/agent.jar'))
tasks.withType(GroovyCompile).configureEach {
    groovyOptions.forkOptions.jvmArgumentProviders.add(javaAgentProvider)
}

class JavaAgentCommandLineArgumentProvider implements CommandLineArgumentProvider {
    @Internal
    final File javaAgentJarFile

    JavaAgentCommandLineArgumentProvider(File javaAgentJarFile) {
        this.javaAgentJarFile = javaAgentJarFile
    }

    @Classpath
    Iterable<File> getClasspath() {
        return [javaAgentJarFile]
    }

    @Override
    List<String> asArguments() {
        ["-javaagent:${javaAgentJarFile.absolutePath}".toString()]
    }
}
```

### Easier source set configuration in the Kotlin DSL

When using the Kotlin DSL, a special construct was required when configuring source locations. For example, here's how you could configure `groovy` sources:

```kotlin
sourceSets {
    main {
        withConvention(GroovySourceSet::class) {
            groovy {
                setSrcDirs(listOf("src/groovy"))
            }
        }
    }
}
```

Gradle 7.1 defines source sets as an extension in the following plugins:

- `groovy`
- `antlr`
- `scala`

 This means that the Kotlin DSL has auto-generated accessors and `withConvention` block can be omitted:

```kotlin
sourceSets {
    main {
        groovy {
            setSrcDirs(listOf("src/groovy"))
        }
    }
}
```

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
