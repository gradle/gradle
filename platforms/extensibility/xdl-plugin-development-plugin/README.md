# The declarative-plugins ecosystem — an XDL face for the XDL plugin itself

`declarative-plugins` makes *authoring an XDL plugin* declarative: a project declares `declarativePlugins { }` (plus the common
dependency/repository traits) and gets a working plugin build. It demonstrates a pattern the other built-in ecosystems deliberately avoid —
the carrier's reaction registers no model of its own but applies the **real** machinery by id: `java-library`, `java-gradle-plugin`,
and the bundled `xdl-gradle-plugin` (which is itself a distribution module, so `ModuleRegistry` resolves it like any other).

The schema (and the generated facades) live in the published sibling `:xdl-plugin-development`; this module is the
distribution-only carrier binding `DeclarativePluginsReaction` and `DeclarativePluginsKotlinReaction`. See
`integrations/gradle/doc/builtin-ecosystem-schemas.md` in the `xdl` (xdl-scripting-language) repository for the
built-in-ecosystem design this follows.

## Usage

```
// settings.gradle.xdl
settings {
  plugins [
    { id "declarative-plugins" }
  ]
  dependencyResolutionManagement {
    repositories [:mavenCentral]
  }
}

// build.gradle.xdl
declarativePlugins {
  dependencies {
    implementation ["org.gradle:gradle-xdl-common-ecosystem:9.7.0"]
  }
}
```

Repositories are settings-level (`dependencyResolutionManagement`, Gradle's default `PREFER_PROJECT`
mode) — the project types deliberately expose no repository surface.

Everything else is single-sourced from the project's own `src/main/xdl/` files: the schemas generate the facades, and the
`<plugin-id>.xdl` plugin block feeds the `gradlePlugin` registration (role 1), from which `java-gradle-plugin` generates the
descriptor. So the declarative surface only has to mark the project as an XDL plugin build and carry
`dependencies`/`repositories` (needed e.g. to depend on a published ecosystem schema library).

## Kotlin reactions

`declarativePluginsKotlin { }` is the same project type for a plugin whose reactions are written in Kotlin. The Kotlin
toolchain is not part of the distribution, so the settings must put one on the build classpath — the reaction applies
whichever is there (embedded first):

```
// settings.gradle.xdl
settings {
  plugins [
    { id "declarative-plugins" }
    { id "embedded-kotlin", apply false }
  ]
  dependencyResolutionManagement {
    repositories [:mavenCentral]
  }
}

// build.gradle.xdl
declarativePluginsKotlin {
}
```

`embedded-kotlin` is an XDL front-end alias mirroring the Kotlin DSL accessor of the same name: it desugars to
`org.gradle.kotlin.embedded-kotlin` and — only when the version is omitted — fills in the version the running
distribution expects, keeping the toolchain distribution-matched. An explicit `version` wins. Alternatively declare
`{ id "org.jetbrains.kotlin.jvm", version "…", apply false }` for a specific Kotlin. Either way `apply false` is
required (the toolchain targets projects, not settings), the settings-level repositories must cover the Kotlin
artifacts (neither toolchain configures repositories), and the bundle resolves from the plugin portal — declaring
`pluginManagement.repositories` without the portal makes that resolution fail, loudly. With the embedded toolchain,
a version-skew warning from `EmbeddedKotlinPlugin` is possible when the published bundle lags the distribution's
embedded Kotlin; it is cosmetic.
