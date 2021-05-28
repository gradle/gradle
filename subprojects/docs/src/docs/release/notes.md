The Gradle team is excited to announce Gradle @version@.

This release revamps [incremental Java compilation](#incremental-java) and makes it easier to [configure Groovy, Scala and Antlr sourcesets](#sourcesets) in Kotlin DSL.

There are also several [new deprecations](userguide/upgrading_version_7.html#changes_7.1) and [small improvements](#cli-compiler-args) to make Gradle easier to use.

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
[Evgeny Mandrikov](https://github.com/Godin),
[Ievgenii Shepeliuk](https://github.com/eshepelyuk),
[Sverre Moe](https://github.com/DJViking).

## Upgrade instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 7.x upgrade guide](userguide/upgrading_version_7.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

## New features and usability improvements

<a name="incremental-java"></a>
### Improved incremental compilation for Java

Gradle has a [Java incremental compiler](userguide/java_plugin.html#sec:incremental_compile) enabled by default that makes incremental builds faster by only compiling Java source files that need to be compiled.

The Java incremental compiler received substantial improvements in this release.

#### Incremental compilation analysis is now stored in the build cache

In previous Gradle releases, incremental compilation analysis was only stored locally.
This meant that when the compile task's outputs were fetched from the [build cache](userguide/build_cache.html), a subsequent build could not do incremental compilation and always required a full recompilation.

In Gradle 7.1, the result of incremental analysis is now stored in the build cache and the first compilation after fetching from the build cache will be incremental.

#### Incremental compilation analysis is faster, uses less memory and disk space

Incremental compilation analysis requires Gradle to extract symbols from class files and analyze a transitive graph of dependencies to determine the consumers of a particular symbol. This can consume lots of memory and time.

Gradle 7.1 significantly reduces the cost of incremental compilation analysis, as well as the size of the analysis.

The impact of this change will vary by project but can be very noticeable. On the Gradle project itself, we were able to make incremental compilation up to twice as fast!

#### Changes to constants do not trigger a full recompilation anymore

Lastly, because of the way the Java compiler works, previous Gradle releases were forced to perform a full recompilation as soon as _any_ constant was changed in an upstream dependency.

Gradle 7.1 introduces a compiler plugin that performs constant usage tracking, and only recompiles the consumers of constants when those constants change.

This can speedup incremental builds for projects using lots of constants, which is common for generated code from template engines.

<a name="sourcesets"></a>
### Easier source set configuration in the Kotlin DSL

When using the Kotlin DSL, a special construct was required when configuring source locations for languages other than Java. For example, here's how you would configure `groovy` sources:

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

Gradle 7.1 defines an extension to source sets for each language in the following plugins:

- `groovy`
- `antlr`
- `scala`

This means that the Kotlin DSL will have access to auto-generated accessors and `withConvention` is no longer necessary:

```kotlin
sourceSets {
    main {
        groovy {
            setSrcDirs(listOf("src/groovy"))
        }
    }
}
```

<a name="cli-compiler-args"></a>
### Build cache friendly command-line arguments for compilation tasks

When declaring arguments for a compiler daemon using [jvmArgs](javadoc/org/gradle/api/tasks/compile/BaseForkOptions.html#getJvmArgs--), these arguments are always treated as `String` inputs to the compile task.

Sometimes these arguments represent paths to files that need to be captured as part of the build cache key. Modeling these arguments as input files can improve the incrementality of the compile task and avoid unnecessary cache misses.

Previously, arguments for the Java compiler invocation could be declared in a using [compiler argument providers](javadoc/org/gradle/api/tasks/compile/CompileOptions.html#getCompilerArgumentProviders--), but there was no way to do this for the command-line arguments to the compiler daemon process itself.
You can now provide command-line arguments to the compiler daemon for [JavaCompile](javadoc/org/gradle/api/tasks/compile/JavaCompile.html), [GroovyCompile](javadoc/org/gradle/api/tasks/compile/GroovyCompile.html), and [ScalaCompile](javadoc/org/gradle/api/tasks/scala/ScalaCompile.html) tasks using [jvmArgumentProviders](javadoc/org/gradle/api/tasks/compile/ProviderAwareForkOptions.html#getJvmArgumentProviders--).

[CommandLineArgumentProvider](javadoc/org/gradle/process/CommandLineArgumentProvider.html) objects configured via `jvmArgumentProviders` will be interrogated for input and/or output annotations and Gradle will add these to the respective task.

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

## Default JaCoCo version upgraded

[The JaCoCo plugin](userguide/jacoco_plugin.html) has been upgraded to the most recent [JaCoCo version 0.8.7](http://www.jacoco.org/jacoco/trunk/doc/changes.html) which includes experimental support for Java 17.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
