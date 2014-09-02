## Story: Add a core `java-gradle-plugin` plugin

This story adds the necessary bits to our build to include a new core plugin.

1. Add a new `plugin-development` subproject
    1. Add to settings.gradle
    1. Add to `pluginProjects` in root `build.gradle`
    1. Add build script for new project
1. Add a plugin implementation
    1. add incubating `org.gradle.api.plugins.devel.JavaGradlePluginPlugin`
    1. implementation adds `java` plugin and compile dependency on `gradleApi()`.
    1. add META-INF plugin descriptor
    1. `jar` task warns if jar contains no plugin descriptors

### Test Coverage

1. Builds can apply `java-gradle-plugin` plugin (add a subclass of `WellBehavedPluginTest` to verify this).
1. Applying `java-gradle-plugin` causes project to be a `java` project
1. `gradle jar` produces usable plugin jar (assuming `src/main/java` contains valid plugin impl)
1. `gradle jar` issues warning if built jar does not contain any plugin descriptors

