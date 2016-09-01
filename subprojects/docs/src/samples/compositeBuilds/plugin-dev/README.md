# Composite build to develop a Gradle plugin

This sample demonstrates a composite build used to develop a Gradle plugin in conjunction with a consuming build. This plugin can then easily be published to be shared with other Gradle builds.

This setup removes the need for a `buildSrc` project for developing a plugin.

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

### Warning: included builds must be configured to discover substitutions

In order to determine the substitutions provided by an included build, that build must be configured. To configure a build, the `buildscript` dependencies must be resolved, resulting in a bit of a chicken-and-egg situation. (Gradle does not yet automatically handle the build dependency graph in this situation).

The current sample avoids this scenario because the plugin consumer is also the top-level composite build. Since the composite build does not contribute and dependency substitutions, it doesn't need to be configured until all of the included builds are configured. By this time the substitutions have been configured and the `buildscript` dependencies can be successfully resolved.

An alternative way to work around this issue is to explicitly declare the substitutions of the _consuming_ build. When substitutions are explicitly declared for an included build, then there is no need for Gradle to configure that build early to determine the substitutions, deferring configuration until such a time that the `buildscript` dependencies can be resolved.
