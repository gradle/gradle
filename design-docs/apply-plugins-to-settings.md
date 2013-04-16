## Story: Allow plugins to be applied to `Settings` and `Gradle` instances

This is GRADLE-2066

1. Extract a `org.gradle.api.plugins.PluginAware` interface out of `Project` with the following methods:
    - `apply(Closure)`
    - `apply(Map)`
    - `getPlugins()`
2. Extract an abstract superclass out of `AbstractProject` to implement this interface.
3. Change `Settings` and `Gradle` to extend `PluginAware`.
4. Change `DefaultObjectConfigurationAction` to allow plugins to be applied to any target object that is-a `PluginAware`.

### Test coverage

- Verify that a compiled plugin can be applied to a `Settings` instance.
- Verify that a compiled plugin can be applied to a `Gradle` instance.
- Existing coverage for script plugins.
