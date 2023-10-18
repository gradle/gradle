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

import gradlebuild.basics.GradleModuleApiAttribute
import gradlebuild.basics.PublicApi
import gradlebuild.basics.kotlindsl.configureKotlinCompilerForGradleBuild
import gradlebuild.basics.tasks.ClasspathManifest
import gradlebuild.basics.tasks.PackageListGenerator
import gradlebuild.docs.GradleUserManualPlugin
import gradlebuild.docs.dsl.source.ExtractDslMetaDataTask
import gradlebuild.docs.dsl.source.GenerateApiMapping
import gradlebuild.docs.dsl.source.GenerateDefaultImports
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension.Companion.INSTRUMENTED_METADATA_EXTENSION
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension.Companion.INSTRUMENTED_SUPER_TYPES_MERGE_TASK
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension.Companion.UPGRADED_PROPERTIES_MERGE_TASK
import gradlebuild.kotlindsl.generator.tasks.GenerateKotlinExtensionsForGradleApi
import gradlebuild.packaging.GradleDistributionSpecs
import gradlebuild.packaging.GradleDistributionSpecs.allDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.binDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.docsDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.srcDistributionSpec
import gradlebuild.packaging.tasks.PluginsManifest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
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
    id("gradlebuild.instrumentation-metadata")
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
val coreRuntimeOnly by bucket()
coreRuntimeOnly.description = "To define dependencies to the Gradle modules that make up the core of the distributions (lib/*.jar)"
val pluginsRuntimeOnly by bucket()
pluginsRuntimeOnly.description = "To define dependencies to the Gradle modules that represent additional plugins packaged in the distributions (lib/plugins/*.jar)"
val agentsRuntimeOnly by bucket()
agentsRuntimeOnly.description = "To define dependencies to the Gradle modules that represent Java agents packaged in the distribution (lib/agents/*.jar)"

coreRuntimeOnly.withDependencies {
    // use 'withDependencies' to not attempt to find platform project during script compilation
    add(project.dependencies.create(dependencies.platform(project(":distributions-dependencies"))))
}

// Configurations to resolve dependencies
val runtimeClasspath by libraryResolver(listOf(coreRuntimeOnly, pluginsRuntimeOnly))
runtimeClasspath.description = "Resolves to all Jars that need to be in the distribution including all transitive dependencies"
val coreRuntimeClasspath by libraryResolver(listOf(coreRuntimeOnly))
coreRuntimeClasspath.description = "Resolves to all Jars, including transitives, that make up the core of the distribution (needed to decide if a Jar goes into 'plugins' or not)"
val agentsRuntimeClasspath by libraryResolver(listOf(agentsRuntimeOnly))
agentsRuntimeClasspath.description = "Resolves to all Jars that need to be added as agents"
val gradleScriptPath by startScriptResolver(":launcher")
gradleScriptPath.description = "Resolves to the Gradle start scripts (bin/*) - automatically adds dependency to the :launcher project"
val sourcesPath by sourcesResolver(listOf(coreRuntimeOnly, pluginsRuntimeOnly))
sourcesPath.description = "Resolves the source code of all Gradle modules Jars (required for the All distribution)"
val docsPath by docsResolver(":docs")
docsPath.description = "Resolves to the complete Gradle documentation - automatically adds dependency to the :docs project"

// Tasks to generate metadata about the distribution that is required at runtime

// List of relocated packages that will be used at Gradle runtime to generate the runtime shaded jars
val generateRelocatedPackageList by tasks.registering(PackageListGenerator::class) {
    classpath.from(runtimeClasspath)
    outputFile = generatedTxtFileFor("api-relocated")
}

// Extract pubic API metadata from source code of Gradle module Jars packaged in the distribution (used by the two tasks below to handle default imports in build scripts)
val dslMetaData by tasks.registering(ExtractDslMetaDataTask::class) {
    source(
        sourcesPath.incoming.artifactView { lenient(true) }.files.asFileTree.matching {
            include(PublicApi.includes)
            exclude(PublicApi.excludes)
        }
    )
    destinationFile = generatedBinFileFor("dsl-meta-data.bin")
}

// List of packages that are imported by default in Gradle build scripts
val defaultImports = tasks.register("defaultImports", GenerateDefaultImports::class) {
    metaDataFile = dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile)
    importsDestFile = generatedTxtFileFor("default-imports")
    excludedPackages = GradleUserManualPlugin.getDefaultExcludedPackages()
}

// Mapping of default imported types to their fully qualified name
val apiMapping by tasks.registering(GenerateApiMapping::class) {
    metaDataFile = dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile)
    mappingDestFile = generatedTxtFileFor("api-mapping")
    excludedPackages = GradleUserManualPlugin.getDefaultExcludedPackages()
}

// Which plugins are in the distribution and which are part of the public API? Required to generate API and Kotlin DSL Jars
val pluginsManifest by pluginsManifestTask(runtimeClasspath, coreRuntimeClasspath, GradleModuleApiAttribute.API)
val implementationPluginsManifest by pluginsManifestTask(runtimeClasspath, coreRuntimeClasspath, GradleModuleApiAttribute.IMPLEMENTATION)

// At runtime, Gradle expects each Gradle jar to have a classpath manifest
val emptyClasspathManifest by tasks.registering(ClasspathManifest::class) {
    this.manifestFile = generatedPropertiesFileFor("$runtimeApiJarName-classpath")
}

// At runtime, Gradle expects to have instrumentation metadata
val instrumentedSuperTypesMergeTask = tasks.named(INSTRUMENTED_SUPER_TYPES_MERGE_TASK)
val upgradedPropertiesMergeTask = tasks.named(UPGRADED_PROPERTIES_MERGE_TASK)
extensions.configure<InstrumentationMetadataExtension>(INSTRUMENTED_METADATA_EXTENSION) {
    classpathToInspect = runtimeClasspath.toInstrumentationMetadataView()
    superTypesOutputFile = generatedPropertiesFileFor("instrumented-super-types")
    upgradedPropertiesFile = generatedJsonFileFor("upgraded-properties")
}

// Jar task to package all metadata in 'gradle-runtime-api-info.jar'
val runtimeApiInfoJar by tasks.registering(Jar::class) {
    archiveVersion = moduleIdentity.version.map { it.baseVersion.version }
    manifest.attributes(
        mapOf(
            Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
            Attributes.Name.IMPLEMENTATION_VERSION.toString() to moduleIdentity.version.map { it.baseVersion.version }
        )
    )
    archiveBaseName = runtimeApiJarName
    into("org/gradle/api/internal/runtimeshaded") {
        from(generateRelocatedPackageList)
    }
    from(apiMapping)
    from(defaultImports)
    from(pluginsManifest)
    from(implementationPluginsManifest)
    from(emptyClasspathManifest)
    from(instrumentedSuperTypesMergeTask)
    from(upgradedPropertiesMergeTask)
}

val gradleApiKotlinExtensions by tasks.registering(GenerateKotlinExtensionsForGradleApi::class) {
    classpath.from(runtimeClasspath)
    destinationDirectory = layout.buildDirectory.dir("generated-sources/kotlin-dsl-extensions")
}


apply<org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin>()
plugins.withType(org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin::class) {
    registerKotlinJvmCompileTask("compileGradleApiKotlinExtensions", "gradle-kotlin-dsl-extensions")
}

val compileGradleApiKotlinExtensions = tasks.named("compileGradleApiKotlinExtensions", KotlinCompile::class) {
    configureKotlinCompilerForGradleBuild()
    multiPlatformEnabled = false
    moduleName = "gradle-kotlin-dsl-extensions"
    source(gradleApiKotlinExtensions)
    libraries.from(runtimeClasspath)
    destinationDirectory = layout.buildDirectory.dir("classes/kotlin-dsl-extensions")

    @Suppress("DEPRECATION")
    ownModuleName = "gradle-kotlin-dsl-extensions"
}

val gradleApiKotlinExtensionsClasspathManifest by tasks.registering(ClasspathManifest::class) {
    manifestFile = generatedPropertiesFileFor("gradle-kotlin-dsl-extensions-classpath")
}

val gradleApiKotlinExtensionsJar by tasks.registering(Jar::class) {
    archiveVersion = moduleIdentity.version.map { it.baseVersion.version }
    manifest.attributes(
        mapOf(
            Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
            Attributes.Name.IMPLEMENTATION_VERSION.toString() to moduleIdentity.version.map { it.baseVersion.version }
        )
    )
    archiveBaseName = "gradle-kotlin-dsl-extensions"
    from(gradleApiKotlinExtensions)
    from(compileGradleApiKotlinExtensions.flatMap { it.destinationDirectory })
    from(gradleApiKotlinExtensionsClasspathManifest)
}

// A standard Java runtime variant for embedded integration testing
consumableVariant("runtime", LibraryElements.JAR, Bundling.EXTERNAL, listOf(coreRuntimeOnly, pluginsRuntimeOnly), runtimeApiInfoJar, gradleApiKotlinExtensionsJar)
// To make all source code of a distribution accessible transitively
consumableSourcesVariant("transitiveSources", listOf(coreRuntimeOnly, pluginsRuntimeOnly), gradleApiKotlinExtensions.map { it.destinationDirectory })
// A platform variant without 'runtime-api-info' artifact such that distributions can depend on each other
consumablePlatformVariant("runtimePlatform", listOf(coreRuntimeOnly, pluginsRuntimeOnly))

// A lifecycle task to build all the distribution zips for publishing
val buildDists by tasks.registering

configureDistribution("normalized", binDistributionSpec(), buildDists, true)
configureDistribution("bin", binDistributionSpec(), buildDists)
configureDistribution("all", allDistributionSpec(), buildDists)
configureDistribution("docs", docsDistributionSpec(), buildDists)
configureDistribution("src", srcDistributionSpec(), buildDists)

fun pluginsManifestTask(runtimeClasspath: Configuration, coreRuntimeClasspath: Configuration, api: GradleModuleApiAttribute) =
    tasks.registering(PluginsManifest::class) {
        pluginsClasspath.from(
            runtimeClasspath.incoming.artifactView {
                lenient(true)
                attributes.attribute(GradleModuleApiAttribute.attribute, api)
            }.files
        )
        coreClasspath.from(coreRuntimeClasspath)
        manifestFile = generatedPropertiesFileFor("gradle${if (api == GradleModuleApiAttribute.API) "" else "-implementation"}-plugins")
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
        archiveBaseName = "gradle"
        archiveClassifier = name
        archiveVersion = moduleIdentity.version.map { it.baseVersion.version }

        destinationDirectory = project.layout.buildDirectory.dir(disDir)

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
    consumableVariant("${name}Installation", "gradle-$name-installation", Bundling.EMBEDDED, emptyList(), installation)
    // A variant providing the zipped distribution as additional input for tests that test the final distribution or require a distribution as test data
    consumableVariant("${name}DistributionZip", "gradle-$name-distribution-zip", Bundling.EMBEDDED, emptyList(), distributionZip)
}

fun generatedBinFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.bin")

fun generatedTxtFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.txt")

fun generatedPropertiesFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.properties")

fun generatedJsonFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.json")

fun bucket() =
    configurations.creating {
        isCanBeResolved = false
        isCanBeConsumed = false
        isVisible = false
    }

fun libraryResolver(extends: List<Configuration>) =
    configurations.creating {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        }
        isCanBeResolved = true
        isCanBeConsumed = false
        isVisible = false
        extends.forEach { extendsFrom(it) }
    }

fun startScriptResolver(defaultDependency: String) =
    configurations.creating {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named("start-scripts"))
        }
        isCanBeResolved = true
        isCanBeConsumed = false
        isVisible = false
        withDependencies {
            add(project.dependencies.create(project(defaultDependency)))
        }
    }

fun sourcesResolver(extends: List<Configuration>) =
    configurations.creating {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-source-folders"))
        }
        isCanBeResolved = true
        isCanBeConsumed = false
        isVisible = false
        extends.forEach { extendsFrom(it) }
    }

fun docsResolver(defaultDependency: String) =
    configurations.creating {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-documentation"))
        }
        isCanBeResolved = true
        isCanBeConsumed = false
        isVisible = false
        withDependencies {
            add(project.dependencies.create(project(defaultDependency)))
        }
    }

fun consumableVariant(name: String, elements: String, bundling: String, extends: List<Configuration>, vararg artifacts: Any) =
    configurations.create("${name}Elements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(elements))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(bundling))
        }
        isCanBeResolved = false
        isCanBeConsumed = true
        isVisible = false
        extends.forEach { extendsFrom(it) }
        artifacts.forEach { outgoing.artifact(it) }
    }

fun consumableSourcesVariant(name: String, extends: List<Configuration>, vararg artifacts: Any) =
    configurations.create("${name}Elements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-source-folders"))
        }
        isCanBeResolved = false
        isCanBeConsumed = true
        isVisible = false
        extends.forEach { extendsFrom(it) }
        artifacts.forEach { outgoing.artifact(it) }
    }

fun consumablePlatformVariant(name: String, extends: List<Configuration>) =
    configurations.create("${name}Elements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.REGULAR_PLATFORM))
        }
        isCanBeResolved = false
        isCanBeConsumed = true
        isVisible = false
        extends.forEach { extendsFrom(it) }
    }
