/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild

import gradlebuild.basics.PublicApi
import gradlebuild.basics.GradleModuleApiAttribute
import gradlebuild.basics.tasks.ClasspathManifest
import gradlebuild.docs.GradleUserManualPlugin
import gradlebuild.docs.dsl.source.ExtractDslMetaDataTask
import gradlebuild.docs.dsl.source.GenerateApiMapping
import gradlebuild.docs.dsl.source.GenerateDefaultImports
import gradlebuild.packaging.GradleDistributionSpecs
import gradlebuild.packaging.GradleDistributionSpecs.allDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.binDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.docsDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.srcDistributionSpec
import gradlebuild.packaging.tasks.PluginsManifest
import org.gradle.api.internal.runtimeshaded.PackageListGenerator
import java.util.jar.Attributes

/**
 * Apply this plugin to let a project build 'Gradle distributions'.
 * When the plugin is applied, the project will offer four different distributions (see also [GradleDistributionSpecs]):
 *
 * - Bin
 * - Normalized (Bin without timestamped version)
 * - All (Bin + Src + Docs)
 * - Docs (only Docs)
 * - Src (Snapshot of the gradle/gradle repo to build from source)
 *
 * While the content of the Docs and Src distribution can be no further controlled,
 * the content of the Bin (as well as Normalized and All) distribution are controlled
 * by the dependencies defined on other projects in the build. This allows the
 * definition of reduced distributions (e.g. a Core distribution without Native plugins)
 * to be used for testing.
 *
 * Other projects may depend on distributions using on their test runtime classpath
 * or as additional test data (see DistributionTest).
 */
plugins {
    id("gradlebuild.module-identity")
    `java-base`
}

// Name of the Jar a Gradle distributions project produces as part of the distribution.
// This Jar contains metadata required by Gradle at runtime. The data may vary
// based on which Gradle module Jars are part of the distribution.
val runtimeApiJarName = "gradle-runtime-api-info"

// Ignore the build receipt as it is not relevant for API list and manifest generation
normalization {
    runtimeClasspath {
        ignore("org/gradle/build-receipt.properties")
    }
}

// Configurations to define dependencies
val coreRuntimeOnly = jvm.utilities.registerDependencyBucket("coreRuntimeOnly", "To define dependencies to the Gradle modules that make up the core of the distributions (lib/*.jar)")
val pluginsRuntimeOnly = jvm.utilities.registerDependencyBucket("pluginsRuntimeOnly", "To define dependencies to the Gradle modules that represent additional plugins packaged in the distributions (lib/plugins/*.jar)")

coreRuntimeOnly.get().withDependencies {
    // use 'withDependencies' to not attempt to find platform project during script compilation
    add(project.dependencies.create(dependencies.platform(project(":distributionsDependencies"))))
}

// Configurations to resolve dependencies
val runtimeClasspath = jvm.createResolvableConfiguration("runtimeClasspath") {
    withDescription("Resolves to all Jars that need to be in the distribution including all transitive dependencies")
    extendsFrom(coreRuntimeOnly)
    extendsFrom(pluginsRuntimeOnly)
    requiresAttributes {
        library().asJar()
    }
}
val coreRuntimeClasspath = jvm.createResolvableConfiguration("coreRuntimeClasspath") {
    withDescription("Resolves to all Jars, including transitives, that make up the core of the distribution (needed to decide if a Jar goes into 'plugins' or not)")
    extendsFrom(coreRuntimeOnly)
    requiresAttributes {
        library().asJar()
    }
}

val gradleScriptPath = jvm.createResolvableConfiguration("gradleScriptPath") {
    withDescription("Resolves to the Gradle start scripts (bin/*) - automatically adds dependency to the :launcher project")
    requiresAttributes {
        library("start-scripts")
    }
}
gradleScriptPath.withDependencies {
    add(project.dependencies.create(project(":launcher")))
}

val sourcesPath = jvm.createResolvableConfiguration("sourcesPath") {
    withDescription("Resolves the source code of all Gradle modules Jars (required for the All distribution)")
    extendsFrom(coreRuntimeOnly)
    extendsFrom(pluginsRuntimeOnly)
    requiresAttributes {
        documentation("gradle-source-folders")
    }
}

val docsPath = jvm.createResolvableConfiguration("docsPath") {
    withDescription("Resolves to the complete Gradle documentation - automatically adds dependency to the :docs project")
    requiresAttributes {
        documentation("gradle-documentation")
    }
}
docsPath.withDependencies {
    add(project.dependencies.create(project(":docs")))
}

// Tasks to generate metadata about the distribution that is required at runtime

// List of relocated packages that will be used at Gradle runtime to generate the runtime shaded jars
val generateRelocatedPackageList by tasks.registering(PackageListGenerator::class) {
    classpath = runtimeClasspath
    outputFile = file(generatedTxtFileFor("api-relocated"))
}

// Extract pubic API metadata from source code of Gradle module Jars packaged in the distribution (used by the two tasks below to handle default imports in build scripts)
val dslMetaData by tasks.registering(ExtractDslMetaDataTask::class) {
    source(sourcesPath.incoming.artifactView { lenient(true) }.files.asFileTree.matching {
        include(PublicApi.includes)
        exclude(PublicApi.excludes)
    })
    destinationFile.set(generatedBinFileFor("dsl-meta-data.bin"))
}

// List of packages that are imported by default in Gradle build scripts
val defaultImports = tasks.register("defaultImports", GenerateDefaultImports::class) {
    metaDataFile.set(dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile))
    importsDestFile.set(generatedTxtFileFor("default-imports"))
    excludedPackages.set(GradleUserManualPlugin.getDefaultExcludedPackages())
}

// Mapping of default imported types to their fully qualified name
val apiMapping by tasks.registering(GenerateApiMapping::class) {
    metaDataFile.set(dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile))
    mappingDestFile.set(generatedTxtFileFor("api-mapping"))
    excludedPackages.set(GradleUserManualPlugin.getDefaultExcludedPackages())
}

// Which plugins are in the distribution and which are part of the public API? Required to generate API and Kotlin DSL Jars
val pluginsManifest by pluginsManifestTask(runtimeClasspath, coreRuntimeClasspath, GradleModuleApiAttribute.API)
val implementationPluginsManifest by pluginsManifestTask(runtimeClasspath, coreRuntimeClasspath, GradleModuleApiAttribute.IMPLEMENTATION)

// At runtime, Gradle expects each Gradle jar to have a classpath manifest
val emptyClasspathManifest by tasks.registering(ClasspathManifest::class) {
    this.manifestFile.set(generatedPropertiesFileFor("$runtimeApiJarName-classpath"))
}

// Jar task to package all metadata in 'gradle-runtime-api-info.jar'
val runtimeApiInfoJar by tasks.registering(Jar::class) {
    archiveVersion.set(moduleIdentity.version.map { it.baseVersion.version })
    manifest.attributes(mapOf(
        Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
        Attributes.Name.IMPLEMENTATION_VERSION.toString() to moduleIdentity.version.map { it.baseVersion.version }))
    archiveBaseName.set(runtimeApiJarName)
    into("org/gradle/api/internal/runtimeshaded") {
        from(generateRelocatedPackageList)
    }
    from(apiMapping)
    from(defaultImports)
    from(pluginsManifest)
    from(implementationPluginsManifest)
    from(emptyClasspathManifest)
}

// A standard Java runtime variant for embedded integration testing
jvm.createOutgoingElements("runtime") {
    extendsFrom(coreRuntimeOnly)
    extendsFrom(pluginsRuntimeOnly)
    providesAttributes {
        library().asJar()
    }
    artifact(runtimeApiInfoJar)
}
// To make all source code of a distribution accessible transitively
jvm.createOutgoingElements("transitiveSourcesElements") {
    extendsFrom(coreRuntimeOnly)
    extendsFrom(pluginsRuntimeOnly)
    providesAttributes {
        documentation("gradle-source-folders")
    }
}
// A platform variant without 'runtime-api-info' artifact such that distributions can depend on each other
jvm.createOutgoingElements("runtimePlatform") {
    extendsFrom(coreRuntimeOnly)
    extendsFrom(pluginsRuntimeOnly)
    providesAttributes {
        platform()
    }
}

// A lifecycle task to build all the distribution zips for publishing
val buildDists by tasks.registering

configureDistribution("normalized", binDistributionSpec(), buildDists, true)
configureDistribution("bin", binDistributionSpec(), buildDists)
configureDistribution("all", allDistributionSpec(), buildDists)
configureDistribution("docs", docsDistributionSpec(), buildDists)
configureDistribution("src", srcDistributionSpec(), buildDists)

fun pluginsManifestTask(runtimeClasspath: Configuration, coreRuntimeClasspath: Configuration, api: GradleModuleApiAttribute) =
    tasks.registering(PluginsManifest::class) {
        pluginsClasspath.from(runtimeClasspath.incoming.artifactView {
            lenient(true)
            attributes.attribute(GradleModuleApiAttribute.attribute, api)
        }.files)
        coreClasspath.from(coreRuntimeClasspath)
        manifestFile.set(generatedPropertiesFileFor("gradle${if (api == GradleModuleApiAttribute.API) "" else "-implementation"}-plugins"))
    }

fun configureDistribution(name: String, distributionSpec: CopySpec, buildDistLifecycleTask: TaskProvider<Task>, normalized: Boolean = false) {
    val disDir = if (normalized) "normalized-distributions" else "distributions"
    val zipRootFolder = if (normalized) {
        moduleIdentity.version.map { "gradle-${it.baseVersion.version}" }
    } else {
        moduleIdentity.version.map { "gradle-${it.version}" }
    }
    val installation = tasks.register<Sync>("${name}Installation") {
        group = "distribution"
        into(layout.buildDirectory.dir("$name distribution"))
        with(distributionSpec)
    }

    val distributionZip = tasks.register<Zip>("${name}DistributionZip") {
        archiveBaseName.set("gradle")
        archiveClassifier.set(name)
        archiveVersion.set(moduleIdentity.version.map { it.baseVersion.version })

        destinationDirectory.set(project.layout.buildDirectory.dir(disDir))

        into(zipRootFolder) {
            with(distributionSpec)
        }
    }

    if (!normalized) {
        buildDistLifecycleTask.configure {
            dependsOn(distributionZip)
        }
    }

    // A 'installation' variant providing a folder where the distribution is present in the final format for forked integration testing
    jvm.createOutgoingElements("${name}Installation") {
        providesAttributes {
            library("gradle-$name-installation")
        }
        artifact(installation)
    }
    // A variant providing the zipped distribution as additional input for tests that test the final distribution or require a distribution as test data
    jvm.createOutgoingElements("${name}DistributionZip") {
        providesAttributes {
            library("gradle-$name-distribution-zip")
        }
        artifact(distributionZip)
    }
}

fun generatedBinFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.bin")

fun generatedTxtFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.txt")

fun generatedPropertiesFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.properties")
