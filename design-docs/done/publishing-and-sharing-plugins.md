## ~~Story: Introduce plugins DSL block~~

Adds the `plugins {}` DSL to build scripts (settings, init or arbitrary script not supported at this point). Plugin specs can be specified in the DSL, but they don't do anything yet.

### Implementation

1. Add a `PluginSpecDsl` service to all script service registries (i.e. “delegate” of `plugins {}`)
1. Add a compile transform that rewrites `plugins {}` to be `ConfigureUtil.configure(services.get(PluginSpecDsl), {})` or similar - we don't want to add a `plugins {}` method to any API
    - This should probably be added to the existing transform that extracts `buildscript {}`
1. Add an `id(String)` method to `PluginSpecDsl` that returns `PluginSpec`, that has a `version(String)` method that returns `PluginSpecDsl` (self)
1. Update the `plugin {}` transform to disallow everything except calling `id(String)` and optionally `version(String)` on the result
1. Update the transform to error if encountering any statement other than a `buildscript {}` statement before a `plugins {}` statement
1. Update the transform to error if encountering a `plugins {}` top level statement in a script plugin
1. `PluginSpecDsl` should validate plugin ids (see format specification above)

### Test cases

- ~~`plugins {}` block is available to build scripts~~
- ~~`plugins {}` block in init, settings and arbitrary scripts yields suitable 'not supported' method~~
- ~~Statement other than `buildscript {}` before `plugins {}` statement causes compile error, with correct line number of offending statement~~
- ~~`buildscript {}` is allowed before `plugins {}` statement~~
- ~~multiple `plugins {}` blocks in a single script causes compile error, with correct line number of first offending plugin statement~~
- ~~`buildscript {}` after `plugins {}` statement causes compile error, with correct line number of offending buildscript statement~~
- ~~Disallowed syntax/constructs cause compile errors, with correct line number of offending statement and suitable explanation of what is allowed (following list is not exhaustive)~~
  - ~~Cannot access `Script` api~~
  - ~~Cannot access script target API (e.g. `Gradle` for init scripts, `Settings` for settings script, `Project` for build)~~
  - ~~Cannot use if statement~~
  - ~~Cannot define local variable~~
  - ~~Cannot use GString values as string arguments to `id()` or `version()`~~
- ~~Plugin ids contain only valid characters~~
- ~~Plugin id cannot begin or end with '.'~~
- ~~Plugin id cannot be empty string~~
- ~~Plugin version cannot be empty string~~

