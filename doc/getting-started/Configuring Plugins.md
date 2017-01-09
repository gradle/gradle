# Configuring Plugins in Gradle Script Kotlin

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

In order to configure plugins using gradle script kotlin you must know the type of the extension
that the plugin adds in order to configure it.

The above groovy block of code would now become:

```kotlin
configure<GreetingPluginExtension> {
    // Various config options here...
}
```

If `GreetingPluginExtension` is not in the base package you will need to import the class.

In order to determine what class you need to use in your `configure<...>` call you may need to 
examine the plugins source code to determine which object is being used to configure the plugin.
There may be more than one object for some plugins.

