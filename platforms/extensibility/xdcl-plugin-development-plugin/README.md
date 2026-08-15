# The plugin-development ecosystem — an XDCL face for the XDCL plugin itself

`plugin-development-ecosystem` makes *authoring an XDCL plugin* declarative: a project declares `xdclGradlePlugin { }` (plus the common
dependency/repository traits) and gets a working plugin build. It demonstrates a pattern the other built-in ecosystems deliberately avoid —
the carrier's reaction registers no model of its own but applies the **real** machinery by id: `java-library`, `java-gradle-plugin`,
and the bundled `xdcl-gradle-plugin` (which is itself a distribution module, so `ModuleRegistry` resolves it like any other).

The schema (and the generated facades) live in the published sibling `:xdcl-plugin-development`; this module is the
distribution-only carrier binding `XdclGradlePluginReaction`. See `integrations/gradle/doc/builtin-ecosystem-schemas.md` in the
`xdcl` (xdcl-scripting-language) repository for the built-in-ecosystem design this follows.

## Usage

```
// settings.gradle.xdcl
settings {
  plugins [
    { id "plugin-development-ecosystem" }
  ]
}

// build.gradle.xdcl
xdclGradlePlugin {
  repositories [:mavenCentral]
  dependencies {
    implementation ["org.gradle:gradle-xdcl-jvm-ecosystem:9.7.0"]
  }
}
```

Everything else is single-sourced from the project's own `src/main/xdcl/` files: the schemas generate the facades, and the
`<plugin-id>.xdcl` plugin block feeds the `gradlePlugin` registration (role 1), from which `java-gradle-plugin` generates the
descriptor. So the declarative surface only has to mark the project as an XDCL plugin build and carry
`dependencies`/`repositories` (needed e.g. to depend on a published ecosystem schema library).
