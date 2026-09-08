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

/**
 * A Gradle API module whose classes are compiled ELSEWHERE and repackaged here.
 *
 * The module declares the jar it repackages on the `repackaged` configuration — a module of an
 * included build, typically — and has no sources of its own. Its jar is that jar's classes
 * re-archived, its advertised sources are that module's sources jar unpacked; the classes are
 * compiled exactly once, by their owning build. Everything downstream then reads what a compiled
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
 * What repackaging does not give a module: the compile-time checks of this build (error-prone,
 * NullAway, checkstyle) never see its sources — the owning build's conventions apply there. The
 * public API gates (binary compatibility, `@Incubating`/`@since`, architecture tests) apply to the
 * repackaged classes and extracted sources exactly as they do to compiled ones.
 *
 * The repackaged jar must be the only copy of its classes in the distribution: whatever else pulls
 * the original into a distribution graph has to exclude it — at the dependency, where the original's
 * owning modules enter (a configuration-wide exclusion would also empty the `repackaged` resolution
 * below, since configuration-level excludes apply to first-level dependencies as well).
 */

plugins {
    id("gradlebuild.distribution.uninstrumented.api-java")
}

val repackaged = configurations.dependencyScope("repackaged") {
    description = "The module whose compiled classes and sources this project repackages as its own"
}

// Non-transitive on purpose: only the declared jar is repackaged, never its dependencies (the module
// declares those itself, like any other module).
val repackagedClasspath = configurations.resolvable("repackagedClasspath") {
    description = "Resolves the jar this project repackages"
    extendsFrom(repackaged.get())
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

val repackagedJar: Provider<File> = repackagedClasspath.get().incoming.files.elements.map { it.single().asFile }

// The sources jar of the same module, by variant reselection off the same resolution.
val repackagedSourcesJar: Provider<File> = repackagedClasspath.get().incoming.artifactView {
    withVariantReselection()
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
    }
}.files.elements.map { it.single().asFile }

val extractedClasses = layout.buildDirectory.dir("repackaged/classes")
val extractedSources = layout.buildDirectory.dir("repackaged/sources")

val extractRepackagedClasses = tasks.register<Sync>("extractRepackagedClasses") {
    description = "Unpacks the classes of the jar this project repackages"
    from(zipTree(repackagedJar)) {
        exclude("META-INF/**")
    }
    into(extractedClasses)
}

val extractRepackagedSources = tasks.register<Sync>("extractRepackagedSources") {
    description = "Unpacks the sources jar of the module this project repackages"
    from(zipTree(repackagedSourcesJar)) {
        exclude("META-INF/**")
    }
    into(extractedSources)
}

// `classesDirs` is documented as safely castable to a configurable collection; the task provider
// carries the build dependency.
(sourceSets.main.get().output.classesDirs as ConfigurableFileCollection).from(extractRepackagedClasses)

configurations.named("transitiveSourcesElements") {
    outgoing.artifact(extractedSources) {
        builtBy(extractRepackagedSources)
    }
}
tasks.named<IncubatingApiReportTask>("incubationReport") {
    sources.from(extractRepackagedSources)
}
tasks.named<NextMajorRemovalReportTask>("nextMajorRemovalReport") {
    sources.from(extractRepackagedSources)
}
