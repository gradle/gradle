# IDE plugin test removal — porting candidates for Tooling API

Context: branch `jb/tide/remove-idea-plugin-tasks`, commit `2f9a2cfd24f` ("Remove a bunch of tests").
Earlier commits (`69df415f22b`, `64fbf9630cb`) deprecated the file-generation tasks of the `idea` and `eclipse` plugins (`idea`, `ideaProject`, `ideaModule`, `ideaWorkspace`, `eclipse`, `eclipseProject`, `eclipseClasspath`, `eclipseJdt`, `eclipseWtp*`, plus `clean*` counterparts) for removal in Gradle 10. The plugins themselves and the `idea { ... }` / `eclipse { ... }` DSL extensions remain — IDEs consume these settings through Tooling API models (`EclipseProject`, `IdeaProject`, `IdeaModule`, etc.).

This document captures which removed integration tests verify behavior that is still relevant via the Tooling API and is **not** obviously covered by an existing cross-version spec.

## Summary

| Category | Count | Action |
|---|---|---|
| (a) Pure file-generation / task-mechanics | ~30 | Skip — dies with the tasks |
| (b) Already covered by an existing crossVersionTest | ~11 | Skip — verify mapping before discarding |
| (c) Settings/model behavior **not** obviously covered | ~17 | Candidate to port |

Existing tooling-API cross-version specs live in `platforms/ide/tooling-api/src/crossVersionTest/groovy/org/gradle/plugins/ide/tooling/r*/` and `org/gradle/integtests/tooling/r*/`.

## Category (C) — porting candidates

Removed test paths are relative to the repo root. Each entry suggests the tooling-API model where an equivalent assertion would live.

### 1. JPMS / Java modules on classpath
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/eclipse/EclipseJavaModulesIntegrationTest.groovy`
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/eclipse/EclipseJavaProjectModulesIntegrationTest.groovy`

Tooling API: `EclipseClasspathEntry` `module` attribute, project-to-project module relationships.

### 2. Custom source / javadoc attachment
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/eclipse/EclipseCustomSourceAndJavadocLocationIntegrationTest.groovy`
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/eclipse/EclipseSourcesAndJavadocJarsIntegrationTest.groovy`
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/idea/IdeaSourcesAndJavadocJarsIntegrationTest.groovy`

Tooling API: `EclipseExternalDependency.sourceJavadoc`; `IdeaModule.dependencies` carrying custom attachments.

### 3. Test scope classification for dependencies
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/eclipse/EclipseTestConfigurationsWithExternalDependenciesIntegrationTest.groovy`
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/eclipse/EclipseTestConfigurationsWithProjectDependenciesIntegrationTest.groovy`

Tooling API: test-scope attribute on `EclipseExternalDependency` and `EclipseProjectDependency`, especially for JVM test suites. Current tooling-API coverage focuses on source directories, not dependencies.

### 4. Per-source-set output locations
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/eclipse/EclipseCustomBuildSourceOutputTest.groovy`

Tooling API: `EclipseSourceDirectory.output`.

### 5. IDEA Java language settings — multi-module + overrides
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/idea/IdeaJavaLanguageSettingsIntegrationTest.groovy`

Tooling API: `IdeaProject.javaLanguageSettings` and per-module overrides on `IdeaModule`. `r64/ToolingApiIdeaModelJavaVersionCrossVersionSpec` covers some, but the removed test is much broader.

### 6. Composite / multi-build classpath
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/idea/IdeaCompositeBuildIntegrationTest.groovy`
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/eclipse/EclipseMultiBuildIntegrationTest.groovy`

Tooling API: project-dependency resolution and classpath across included builds via `IdeaProject.modules` and `EclipseProject.classpath`.

### 7. General classpath composition (lower priority)
- `platforms/ide/ide/src/integTest/groovy/org/gradle/plugins/ide/eclipse/EclipseClasspathIntegrationTest.groovy`

Likely already covered piecemeal across the `r30/*` specs. Worth a closer pass before porting.

## Category (B) — already covered (sanity-check before discarding)

| Removed test | Existing coverage |
|---|---|
| `EclipseTestSourcesIntegrationTest` | `r56/ToolingApiEclipseModelTestSourcesCrossVersionSpec` |
| `EclipseScopeAttributeIntegrationTest` | `r214/ToolingApiEclipseModelWtpClasspathAttributesCrossVersionSpec` |
| `EclipseLinkedResourceIntegrationTest` | `r27/ToolingApiEclipseLinkedResourcesCrossVersionSpec` |
| `IdeaSourceDirTypesIntegrationTests` | `r56/ToolingApiEclipseModelTestSourcesCrossVersionSpec` (test attribute marking) |
| `EclipseProjectNameDeduplicationIntegrationTest` | `r55` composite-deduplication specs |
| `IdeaModuleDeduplicationIntegrationTest` | `r55` composite-deduplication specs |
| `EclipseDependencyLockingIntegrationTest` | Generic dep-resolution coverage in tooling API |
| `IdeaDependencyLockingIntegrationTest` | Generic dep-resolution coverage in tooling API |
| `CompositeBuildIdeaProjectIntegrationTest` | `r31/AdHocCompositeDependencySubstitutionCrossVersionSpec` |
| `EclipseDependencySubstitutionIntegrationTest` | Substitution specs |
| `IdeaDependencySubstitutionIntegrationTest` | Substitution specs |

The exploration was less confident about the dependency-locking/substitution mappings — the existing specs cover substitution generically but may not exercise the IDE-model surface. Verify before treating as fully covered.

## Category (A) — file-generation only (not actionable)

~30 tests asserting on `.classpath` / `.project` / `.iml` / `.ipr` XML, `cleanX` task mechanics, or `beforeMerged` / `whenMerged` hooks. These die with the tasks; not relevant to port.
