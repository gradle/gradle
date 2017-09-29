# Configuring Plugins in the Gradle Kotlin DSL

When writing your build logic in groovy you will often see code like this:
```groovy
pmd {
    consoleOutput = true
    sourceSets = [sourceSets.main, sourceSets.test]
    reportsDir = file("$project.buildDir/reports/pmd")
    ruleSetFiles = files(new File(rootDir, "pmd-ruleset.xml"))
    ruleSets = []
}

findbugs {
    sourceSets = [sourceSets.main, sourceSets.test]
    excludeFilter = new File(rootDir, "findBugsSuppressions.xml")
    effort = "max"
}
```

These configuration blocks are used by plugins to configure tasks that they add to your build.

They are added as extensions like this:
```groovy
project.extensions.create("greeting", GreetingPluginExtension)
```

Now, in your `build.gradle` you can use the config like this:
```groovy
greeting {
    // Various config options here...
}
```

You can read more about this part of the gradle API [here](https://docs.gradle.org/current/userguide/custom_plugins.html).

When using the Gradle Kotlin DSL it is heavily recommended to apply Gradle plugins declaratively using the `plugins {}`
block. This will enable type-safe extension accessors you will use to configure plugins. 

```kotlin
plugins {
    // Gradle built-in
    `application`
    // From the Gradle Plugin Portal
    id("com.bmuschko.docker-java-application") version "3.1.0"
}

// Type-safe accessor for the extension contributed by the `application` plugin
application {
    mainClassName = "samples.HelloWorld"
}

// Type-safe accessor for the extension contributed by the Docker plugin
registryCredentials {
    url = "https://docker.acme.com/v1/"
}
```

Plugins fetched from another source than the [Gradle Plugin Portal](https://plugins.gradle.org) may or may not be usable
with the `plugins {}` block depending on how they have been published. If you're publishing plugins, please use
the Gradle built-in `java-gradle-plugin` plugin that automate publication of supplementary data to make your plugins
usable with the `plugins {}` block.

For example, the Android Gradle Plugin 2.x plugins are not published to the Gradle Plugin Portal and the metadata
required to resolve plugin identifiers to resolvable artifacts
[is not published](https://issuetracker.google.com/issues/64551265).
The following snippets will use the Android Gradle Plugin to demonstrate how to enable the use of the `plugins {}` block
anyway.

The goal here is to instruct your build how to map the `com.android.application` plugin identifier to a resolvable
artifact.
This is done in two steps.

First add a plugin repository in your `settings.gradle` file for the whole build: 
```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url "https://jcenter.bintray.com/" }
    }
}
```

Then, map the plugin `id` to the corresponding artifact coordinates, still in your `settings.gradle` file:

```groovy
pluginManagement {
    // ...
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }    
}
```

You can now apply the `com.android.application` plugin using the `plugins {}` block and benefit from the type-safe
plugin extension accessors, in your `build.gradle.kts` file:

```kotlin
plugins {
    id("com.android.application") version "2.3.3"
}

android {
    buildToolsVersion("25.0.0")
    compileSdkVersion(23)    
}
```

See the [Plugin Management](https://docs.gradle.org/current/userguide/plugins.html#sec:plugin_management) section of
the Gradle documentation for more information.

However, it is not yet possible to use the `plugins {}` block in the following situations:
- [plugins already applied to a parent project](https://github.com/gradle/kotlin-dsl/issues/426)
- [plugins from `buildSrc`](https://github.com/gradle/kotlin-dsl/issues/426)
- [plugins from composite builds](https://github.com/gradle/gradle/issues/2528)

If you are in any of those cases, you need to apply the plugin imperatively (using the `buildscript` block and
`apply { from("") }`) and to know the type of the extension.

The following groovy block of code:

```groovy
greeting {
    // Various config options here...
}
```

would now become:

```kotlin
configure<GreetingPluginExtension> {
    // Various config options here...
}
```

If `GreetingPluginExtension` is not in the base package you will need to import the class.

In order to determine what class you need to use in your `configure<...>` call you may need to 
examine the plugins source code to determine which object is being used to configure the plugin.
There may be more than one object for some plugins.

