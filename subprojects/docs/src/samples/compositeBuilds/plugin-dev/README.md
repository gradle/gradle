# Composite build to develop a Gradle plugin

This sample demonstrates a composite build used to develop a Gradle plugin in conjunction with a consuming build.

The plugin could be in the same repository (only used by this build) or it could be in a different repository (used by many other builds).
 
This removes the need for the special `buildSrc` project and makes prototyping plugins even easier. 

### Buildscript dependencies are substituted

In a composite build, dependencies declared for the `buildscript` `classpath` configuration are substituted in the same way as other dependencies. In this sample, the `consumer` build declares a `buildscript` dependency on "org.sample:greeting-plugin:1.0-SNAPSHOT", and this dependency is substituted by the `greeting-plugin` included build.

Without ever publishing the `greeting-plugin` project to a repository, it is possible to build the `consumer` project with the locally developed 'org.sample.greeting' plugin.

```
> gradle --include-build ../greeting-plugin greetBob
[composite-build] Configuring build: /home/user/gradle/sample/compositeBuilds/plugin-dev/greeting-plugin
:greeting-plugin:compileJava
:greeting-plugin:pluginDescriptors
:greeting-plugin:processResources
:greeting-plugin:classes
:greeting-plugin:jar
:greetBob
Hi Bob!!!
```

### Plugin changes can be tested

This sample can be used to demonstrate the development lifecycle of a Gradle plugin. Edit the file `greeting-plugin/src/main/java/org/sample/GreetingTask.java` to change the greeting, and re-execute the consumer build:

```
> gradle --include-build ../greeting-plugin greetBob
[composite-build] Configuring build: /home/user/gradle/sample/compositeBuilds/plugin-dev/greeting-plugin
:greeting-plugin:compileJava
:greeting-plugin:pluginDescriptors
:greeting-plugin:processResources
:greeting-plugin:classes
:greeting-plugin:jar
:greetBob
G'day Bob!!!
```

The change to the plugin source can be seen immediately in the consumer build.

### Warning: Builds are configured unless substitutions are manually specified

In order to automatically determine the substitutions provided by an included build, that build must be configured. To configure a build, the `buildscript` dependencies must be resolved. This results in a chicken-and-egg situation if one of the `buildscript` dependencies is also part of the composite. (Gradle does not yet automatically handle the build dependency graph in this situation).

The current sample avoids this scenario because the plugin consumer is also the top-level composite build. Since the top-level build does not contribute any dependency substitutions, it doesn't need to be configured up-front.

If you want to include a plugin and its consumer next to each other in a composite, then you need to explicitly define the substitutions provided by the *consumer*. By explicitly defining the substitutions, Gradle no longer needs to configure the consumer up-front, removing the chicken-and-egg situation.

```
includeBuild ('consumer') {
    dependencySubstitution {
        substitute module('org.sample:consumer') with project(':')
    }
}
includeBuild 'greeting-plugin'
```
