
## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

### More work avoidance when using `@Classpath` task properties

For built-in and custom tasks that use the `@Classpath` annotation, Gradle now performs deeper inspection of the classpath to filter out some differences that do not affect task execution.  Gradle will ignore changes to timestamps within a jar file and the order of entries inside a jar file.
 
In previous versions, for tasks like `Javadoc`, `Checkstyle` and `Test`, Gradle would consider the task out-of-date if the content of the classpath changed in any way (order of classes in a jar, timestamps of class files, etc). 

### Extensions now have a public type

Extensions can now be registered in `ExtensionContainer`s with an explicit public type.
 This allows plugin authors to hide their implementation type from build scripts and
 allow `ExtensionContainer`s to expose a schema of all the registered extensions.

For example, if you have a `FancyExtension` type, implemented by some `DefaultFancyExtension` type, here is how
 you should register it:

    // If you want to delegate the extension instance creation to Gradle:
    project.extensions.create FancyExtension, 'fancy', DefaultFancyExtension

    // Or if you need to create the extension instance yourself:
    FancyExtension fancyInstance = new DefaultFancyExtension(...)
    project.extensions.add FancyExtension, 'fancy', fancyInstance

### BuildActionExecutor supports running tasks

Tooling API clients can now run tasks before running a build action. This allows them to fetch tooling models which depend on the result of
executing some task. This mirrors the existing `ModelBuilder.forTasks()` API.

### Support for multi-value Javadoc options

Gradle has added support for command-line options to doclets that can appear [multiple times and have multiple values](javadoc/org/gradle/external/javadoc/CoreJavadocOptions.html#addMultilineMultiValueOption-java.lang.String-).

In previous versions of Gradle, it was not possible to supply command-line options like:

    -myoption 'foo' 'bar'
    -myoption 'baz'
    
Gradle would produce a single `-myoption` or combine the option's value into a single argument.

    javadoc {
        options {
            def myoption = addMultilineMultiValueOption("myoption")
            myoption.setValue([
                [ "foo", "bar" ],
                [ "baz" ]
            ])
        }
    }

### Plugin resolution rules

Gradle now allows you to adjust how plugins are resolved by providing plugin resolution rules. For instance, you could
specify a default version for a plugin so you don't have to repeat it in every project. Or you could tell Gradle what implementation artifact it should
look for in case the plugin is not published with plugin markers.

    pluginManagement {
        repositories {
            maven { url = 'someUrl'}
        }
        resolutionStrategy {
            eachPlugin {
                if (requested.id.namespace = 'my.plugins') {
                    useVersion('1.3')
                }
            }
        }
    }

The `pluginManagement` block supersedes the existing `pluginRepositories` block. Moreover, you now have full access to the `Settings` DSL inside that block,
so you can make decisions e.g. based on start parameters. You can also configure plugin management from an init script by using the `settingsEvaluated {}` hook.

### Use Java home to choose toolchain for cross compilation

For selecting a Java toolchain for cross compilation you can now use [ForkOptions.javaHome](javadoc/org/gradle/api/tasks/compile/ForkOptions.html#getJavaHome\(\)).
Gradle will detect the version of the Java installation and use the right compiler from the installation.
Setting `ForkOptions.executable` has been deprecated in favor of this new way of choosing the Java compiler for cross-compilation. 

For more information on how to use this feature see the [documentation on cross-compilation](userguide/java_plugin.html#sec:java_cross_compilation).

### Kotlin Build Scripts

Gradle Script Kotlin v0.8.0, included in Gradle 3.5, greatly improves the user experience and parity with Groovy build scripts.

Updates since v0.5.1:

- Uses the great [Kotlin 1.1](https://blog.jetbrains.com/kotlin/2017/03/kotlin-1-1/) release which in itself brings a lot of fixes, including the ability to use the `kotlin-gradle-plugin` 1.0.x again.
- Better error reporting with location of compilation errors and clickable links.
- Plugins can be applied by string id and version within the newly introduced plugins block.
- Builtin plugins can be applied via a type-safe and tooling-friendly DSL.
- Type-safe accessors for project extensions and conventions enable content-assist, quick documentation and code navigation.
- Creation and configuration of objects within Gradle collections is now pretty and convenient.
- The [dreaded `it` problem](https://www.youtube.com/watch?v=vv4zh_oPBTw&feature=youtu.be&t=1387) is now solved, that means a consistent DSL across core and community plugins.
- Many methods in the Gradle API previously only available to Groovy have been overloaded with versions better suited to Kotlin.
- Groovy closures can now be invoked using regular function invocation syntax.
- IDEA now receives the correct classpath for build scripts from sub-projects in a multi-project build.

Full details are available in the Gradle Script Kotlin
[v0.6.0](https://github.com/gradle/gradle-script-kotlin/releases/tag/v0.6.0), 
[v0.7.0](https://github.com/gradle/gradle-script-kotlin/releases/tag/v0.7.0) and 
[v0.8.0](https://github.com/gradle/gradle-script-kotlin/releases/tag/v0.8.0)
release notes.

<!--
### Example new and noteworthy
-->

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

### Wrapper Plugin
The [Gradle Wrapper](userguide/gradle_wrapper.html) has been unchanged for quite some time and is the most popular way to manage Gradle versions.

### Build Init Plugin
[gradle init](userguide/build_init_plugin.html) has been stable for quite some time as well and has been promoted from incubating.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### pluginRepositories block superseded

The `pluginRepositories` method in `settings.gradle` is superseded by the new `pluginManagement.repositories` method.

### Specifying the executable for forking Java compilation is deprecated

The `ForkOptions.executable` property has been deprecated.
You should [set the Java home to choose a toolchain for cross compilation](#use-java-home-to-choose-toolchain-for-cross-compilation).

<!--
### Example deprecation
-->

## Potential breaking changes

### Groovy upgraded from 2.4.7 to 2.4.9

The version of Groovy bundled with Gradle has been upgraded to [2.4.9](http://www.groovy-lang.org/changelogs/changelog-2.4.9.html).

### Core extensions should be addressed by their public type

Now that extensions implementation type is hidden from plugins and build scripts that extensions can only be
 addressed by their public type, some Gradle core extensions are not addressable by their implementation type anymore:

- `DefaultExtraPropertiesExtension`, use `ExtraPropertiesExtension` instead
- `DefaultDistributionContainer`, use `DistributionContainer` instead
- `DefaultPublishingExtension`, use `PublishingExtension` instead
- `DefaultPlatformContainer`, use `PlatformContainer` instead
- `DefaultBuildTypeContainer`, use `BuildTypeContainer` instead
- `DefaultFlavorContainer`, use `FlavorContainer` instead
- `DefaultNativeToolChainRegistry`, use `NativeToolChainRegistry` instead

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->
 - [Ethan Hall](https://github.com/ethankhall) - Plugin resolution rules ([gradle/gradle#1343](https://github.com/gradle/gradle/pull/1343))
 - [Lucas Smaira](https://github.com/lsmaira) - BuildActionExecutor.forTasks() support ([gradle/gradle#1442](https://github.com/gradle/gradle/pull/1442))
 - [Vladislav Soroka](https://github.com/vladsoroka) - Allow environment variables to be configured through Tooling API ([gradle/gradle#1029](https://github.com/gradle/gradle/pull/1029))
 - [Jacob Beardsley](https://github.com/jacobwu) - Fix `NullPointerException` when excluding transitive dependencies in dependency configuration ([gradle/gradle#1113](https://github.com/gradle/gradle/pull/1113))
 - [Attila Kelemen](https://github.com/kelemen) - Project.file supports java.nio.file.Path instances ([gradle/gradle#813](https://github.com/gradle/gradle/pull/813))
 - [Kevin Page](https://github.com/kpage) - Eclipse resource filters ([gradle/gradle#846](https://github.com/gradle/gradle/pull/846))
 - [Björn Kautler](https://github.com/Vampire) - Update user guide for build comparison about supported builds ([gradle/gradle#1266](https://github.com/gradle/gradle/pull/1266))
 - [Joshua Street](https://github.com/jjstreet) - Align usage of `groovy-all` dependency across multiple example in user guide ([gradle/gradle#1446](https://github.com/gradle/gradle/pull/1446))
 - [Thomas Broyer](https://github.com/tbroyer) - Fix SourceSet.compileClasspath default value documentation ([gradle/gradle#1329](https://github.com/gradle/gradle/pull/1329))
 - [Erhan Karakaya](https://github.com/er-han) - Fix bug in generating distributionUrl in Thurkish locale ([gradle/gradle#1408](https://github.com/gradle/gradle/pull/1408))
 - [Endre Fejes](https://github.com/fejese) - Fixing EOL to be platform specific for `--version` ([gradle/gradle#1462](https://github.com/gradle/gradle/pull/1462))
 - [Eitan Adler](https://github.com/grimreaper) - Minor tests cleanup ([gradle/gradle#1219](https://github.com/gradle/gradle/pull/1219))

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
