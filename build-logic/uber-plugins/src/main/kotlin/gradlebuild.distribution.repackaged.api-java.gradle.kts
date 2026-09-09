/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import gradlebuild.incubation.tasks.IncubatingApiReportTask
import gradlebuild.removal.tasks.NextMajorRemovalReportTask
import gradlebuild.repackaging.ExtractRepackagedArchives
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult

/**
 * A Gradle API module whose classes are compiled ELSEWHERE and repackaged here.
 *
 * The module declares the jars it repackages on the `repackaged` configuration — modules of an
 * included build, typically; one or several — and has no sources of its own. Its jar is those jars'
 * classes re-archived, its advertised sources are those modules' sources jars unpacked; the classes
 * are compiled exactly once, by their owning build. Everything downstream then reads what a compiled
 * module would have produced, through the same seams the hand-written modules fill:
 *
 * - the extracted classes are a class directory of the main source set, which the `jar` task
 *   packages and the ABI extractor (`gradlebuild.distribution-module`, feeding the public API jar
 *   and its stubs) reads;
 * - the extracted sources are a root of the `gradle-source-folders` variant — resolved by the docs,
 *   the DSL metadata / default imports, the Kotlin DSL extension generator and the binary
 *   compatibility `@since` lookup — and an input of the incubation and removal reports. They are
 *   deliberately NOT a source directory of the main source set, which would compile them again.
 *
 * Every repackaged module must expose a sources jar (`withSourcesJar()` in its build): the
 * derivative API artifacts above are only complete with the sources, so a module without them is
 * reported by name instead of silently shipping undocumented, `@since`-less API. Two modules
 * contributing the same class or resource path is reported too ([ExtractRepackagedArchives]).
 *
 * What repackaging does not give a module: the compile-time checks of this build (error-prone,
 * NullAway, checkstyle) never see its sources — the owning build's conventions apply there. The
 * public API gates (binary compatibility, `@Incubating`/`@since`, architecture tests) apply to the
 * repackaged classes and extracted sources exactly as they do to compiled ones.
 *
 * The repackaged jars must be the only copies of their classes in the distribution: whatever else
 * pulls an original into a distribution graph has to exclude it — at the dependency, where the
 * original's owning modules enter (a configuration-wide exclusion would also empty the `repackaged`
 * resolution below, since configuration-level excludes apply to first-level dependencies as well).
 */

plugins {
    id("gradlebuild.distribution.uninstrumented.api-java")
}

val repackaged = configurations.dependencyScope("repackaged") {
    description = "The modules whose compiled classes and sources this project repackages as its own"
}

// Non-transitive on purpose: only the declared jars are repackaged, never their dependencies (the
// module declares those itself, like any other module).
val repackagedClasspath = configurations.resolvable("repackagedClasspath") {
    description = "Resolves the jars this project repackages"
    extendsFrom(repackaged.get())
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

val repackagedJars: Provider<Set<ResolvedArtifactResult>> = repackagedClasspath.get().incoming.artifacts.resolvedArtifacts

// The sources jars of the same modules, by variant reselection off the same resolution. Lenient so
// that a module WITHOUT a sources variant surfaces in the check below, by name, rather than as a
// variant-matching failure.
val repackagedSourcesJars: Provider<Set<ResolvedArtifactResult>> = repackagedClasspath.get().incoming.artifactView {
    withVariantReselection()
    lenient(true)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
    }
}.artifacts.resolvedArtifacts

// Both checks run when the task inputs below are resolved — at task-graph time — so a misconfigured
// module fails with its name and the remedy, before anything is unpacked. The mapping lambdas are
// serialized into the configuration cache with the tasks, so they must reference LOCALS only: a
// script-level value or function would drag the script instance in, which the cache rejects.
val repackagedJarFiles: Provider<List<File>> = run {
    val path = project.path
    repackagedJars.map { jars ->
        if (jars.isEmpty()) {
            throw GradleException(
                "Project '$path' applies gradlebuild.distribution.repackaged.api-java but declares nothing on its " +
                    "'repackaged' configuration. Declare the module(s) whose classes and sources it repackages."
            )
        }
        jars.map { it.file }
    }
}

val repackagedSourcesJarFiles: Provider<List<File>> = run {
    val path = project.path
    repackagedSourcesJars.zip(repackagedJars) { sources, jars ->
        val components = { artifacts: Set<ResolvedArtifactResult> -> artifacts.mapTo(linkedSetOf<ComponentIdentifier>()) { it.id.componentIdentifier } }
        val withoutSources = components(jars) - components(sources)
        if (withoutSources.isNotEmpty()) {
            throw GradleException(
                "Project '$path' repackages ${withoutSources.joinToString { "'${it.displayName}'" }}, which " +
                    "${if (withoutSources.size == 1) "exposes" else "expose"} no sources variant. A repackaged Gradle API module " +
                    "must provide its sources (`java { withSourcesJar() }` in the owning build): the docs, the DSL metadata, " +
                    "the Kotlin DSL extensions and the binary compatibility @since checks are generated from them."
            )
        }
        sources.map { it.file }
    }
}

val extractRepackagedClasses = tasks.register<ExtractRepackagedArchives>("extractRepackagedClasses") {
    description = "Unpacks the classes of the jars this project repackages"
    archives.from(repackagedJarFiles)
    outputDirectory = layout.buildDirectory.dir("repackaged/classes")
}

val extractRepackagedSources = tasks.register<ExtractRepackagedArchives>("extractRepackagedSources") {
    description = "Unpacks the sources jars of the modules this project repackages"
    archives.from(repackagedSourcesJarFiles)
    outputDirectory = layout.buildDirectory.dir("repackaged/sources")
}

// `classesDirs` is documented as safely castable to a configurable collection; the task provider
// carries the build dependency.
(sourceSets.main.get().output.classesDirs as ConfigurableFileCollection).from(extractRepackagedClasses)

configurations.named("transitiveSourcesElements") {
    outgoing.artifact(extractRepackagedSources.flatMap { it.outputDirectory })
}
tasks.named<IncubatingApiReportTask>("incubationReport") {
    sources.from(extractRepackagedSources)
}
tasks.named<NextMajorRemovalReportTask>("nextMajorRemovalReport") {
    sources.from(extractRepackagedSources)
}
