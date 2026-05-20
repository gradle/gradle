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
import gradlebuild.basics.buildVersionQualifier
import gradlebuild.basics.kotlindsl.configureKotlinCompilerForGradleBuild
import gradlebuild.basics.repoRoot
import gradlebuild.basics.tasks.PackageListGenerator
import gradlebuild.configureAsApiElements
import gradlebuild.configureAsRuntimeElements
import gradlebuild.docs.GradleUserManualPlugin
import gradlebuild.docs.dsl.source.ExtractDslMetaDataTask
import gradlebuild.docs.dsl.source.GenerateApiMapping
import gradlebuild.docs.dsl.source.GenerateDefaultImports
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension.Companion.INSTRUMENTED_METADATA_EXTENSION
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension.Companion.INSTRUMENTED_SUPER_TYPES_MERGE_TASK
import gradlebuild.instrumentation.extensions.InstrumentationMetadataExtension.Companion.UPGRADED_PROPERTIES_MERGE_TASK
import gradlebuild.kotlindsl.generator.tasks.GenerateKotlinExtensionsForGradleApi
import gradlebuild.packaging.GradleDistributionSpecs.allDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.binDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.docsDistributionSpec
import gradlebuild.packaging.GradleDistributionSpecs.srcDistributionSpec
import gradlebuild.packaging.tasks.GenerateClasspathModuleProperties
import gradlebuild.packaging.tasks.GenerateEmptyModuleProperties
import gradlebuild.packaging.support.PomLicenseUtils
import gradlebuild.packaging.tasks.GenerateLicenseFile
import gradlebuild.packaging.tasks.PluginsManifest
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
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
val coreRuntimeOnly = bucket("coreRuntimeOnly")
coreRuntimeOnly.description = "To define dependencies to the Gradle modules that make up the core of the distributions (lib/*.jar)"
val pluginsRuntimeOnly = bucket("pluginsRuntimeOnly")
pluginsRuntimeOnly.description = "To define dependencies to the Gradle modules that represent additional plugins packaged in the distributions (lib/plugins/*.jar)"
val agentsRuntimeOnly = bucket("agentsRuntimeOnly")
agentsRuntimeOnly.description = "To define dependencies to the Gradle modules that represent Java agents packaged in the distribution (lib/agents/*.jar)"

// Use lazy API to not attempt to find platform project during script compilation
coreRuntimeOnly.dependencies.addLater(provider {
    dependencies.platform(dependencies.create(project(":distributions-dependencies")))
})

// Configurations to resolve dependencies
val runtimeClasspath = libraryResolver("runtimeClasspath", listOf(coreRuntimeOnly, pluginsRuntimeOnly))
runtimeClasspath.description = "Resolves to all Jars that need to be in the distribution including all transitive dependencies"
val coreRuntimeClasspath = libraryResolver("coreRuntimeClasspath", listOf(coreRuntimeOnly))
coreRuntimeClasspath.description = "Resolves to all Jars, including transitives, that make up the core of the distribution (needed to decide if a Jar goes into 'plugins' or not)"
val agentsRuntimeClasspath = libraryResolver("agentsRuntimeClasspath", listOf(agentsRuntimeOnly))
agentsRuntimeClasspath.description = "Resolves to all Jars that need to be added as agents"
val gradleScriptPath = startScriptResolver("gradleScriptPath", ":gradle-cli-main")
gradleScriptPath.description = "Resolves to the Gradle start scripts (bin/*) - automatically adds dependency to the :launcher project"
val sourcesPath = sourcesResolver("sourcesPath", listOf(coreRuntimeOnly, pluginsRuntimeOnly))
sourcesPath.description = "Resolves the source code of all Gradle modules Jars (required for the All distribution)"
val docsPath = docsResolver("docsPath", ":docs")
docsPath.description = "Resolves to the complete Gradle documentation - automatically adds dependency to the :docs project"

// Gradle API Sources
val gradleApiSources = sourcesPath.incoming.artifactView { lenient(true) }.files.asFileTree.matching {
    include(PublicApi.includes)
    exclude(PublicApi.excludes)
}

// Tasks to generate metadata about the distribution that is required at runtime

// List of relocated packages that will be used at Gradle runtime to generate the runtime shaded jars
val generateRelocatedPackageList = tasks.register<PackageListGenerator>("generateRelocatedPackageList") {
    classpath.from(runtimeClasspath)
    outputFile = generatedTxtFileFor("api-relocated")
}

// Extract public API metadata from source code of Gradle module Jars packaged in the distribution (used by the two tasks below to handle default imports in build scripts)
val dslMetaData = tasks.register<ExtractDslMetaDataTask>("dslMetaData") {
    source(gradleApiSources)
    destinationFile = generatedBinFileFor("dsl-meta-data.bin")
}

// List of packages that are imported by default in Gradle build scripts
val defaultImports = tasks.register("defaultImports", GenerateDefaultImports::class) {
    metaDataFile = dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile)
    importsDestFile = generatedTxtFileFor("default-imports")
    excludedPackages = GradleUserManualPlugin.getDefaultExcludedPackages()
}

// Mapping of default imported types to their fully qualified name
val apiMapping = tasks.register<GenerateApiMapping>("apiMapping") {
    metaDataFile = dslMetaData.flatMap(ExtractDslMetaDataTask::getDestinationFile)
    mappingDestFile = generatedTxtFileFor("api-mapping")
    excludedPackages = GradleUserManualPlugin.getDefaultExcludedPackages()
}

// Which plugins are in the distribution and which are part of the public API? Required to generate API and Kotlin DSL Jars
val pluginsManifest = pluginsManifestTask("pluginsManifest", runtimeClasspath, coreRuntimeClasspath, GradleModuleApiAttribute.API)
val implementationPluginsManifest = pluginsManifestTask("implementationPluginsManifest", runtimeClasspath, coreRuntimeClasspath, GradleModuleApiAttribute.IMPLEMENTATION)

// At runtime, Gradle expects to have instrumentation metadata
val instrumentedSuperTypesMergeTask = tasks.named(INSTRUMENTED_SUPER_TYPES_MERGE_TASK)
val upgradedPropertiesMergeTask = tasks.named(UPGRADED_PROPERTIES_MERGE_TASK)
extensions.configure<InstrumentationMetadataExtension>(INSTRUMENTED_METADATA_EXTENSION) {
    classpathToInspect = runtimeClasspath.toInstrumentationMetadataView()
    superTypesOutputFile = generatedPropertiesFileFor("instrumented-super-types")
    upgradedPropertiesFile = generatedJsonFileFor("upgraded-properties")
}

// Jar task to package all metadata in 'gradle-runtime-api-info.jar'
val runtimeApiInfoJar = tasks.register<Jar>("runtimeApiInfoJar") {
    archiveVersion = gradleModule.identity.version.map { it.baseVersion.version }
    manifest.attributes(
        mapOf(
            Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
            Attributes.Name.IMPLEMENTATION_VERSION.toString() to gradleModule.identity.version.map { it.baseVersion.version }
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
    from(instrumentedSuperTypesMergeTask)
    from(upgradedPropertiesMergeTask)
}

val kotlinDslSharedRuntime = configurations.dependencyScope("kotlinDslSharedRuntime")
val kotlinDslSharedRuntimeClasspath = configurations.resolvable("kotlinDslSharedRuntimeClasspath") {
    extendsFrom(kotlinDslSharedRuntime.get())
}
val libs = project.versionCatalogs.named("libs")
dependencies {
    kotlinDslSharedRuntime(platform("gradlebuild:build-platform"))
    kotlinDslSharedRuntime("org.gradle:kotlin-dsl-shared-runtime")
    kotlinDslSharedRuntime(kotlin("stdlib", embeddedKotlinVersion))
    kotlinDslSharedRuntime(libs.findLibrary("asmTree").get())
    kotlinDslSharedRuntime(libs.findLibrary("jsr305").get())
    kotlinDslSharedRuntime(libs.findLibrary("jspecify").get())
}
val gradleApiKotlinExtensions = tasks.register<GenerateKotlinExtensionsForGradleApi>("gradleApiKotlinExtensions") {
    sharedRuntimeClasspath.from(kotlinDslSharedRuntimeClasspath)
    classpath.from(runtimeClasspath)
    sources.from(gradleApiSources)
    destinationDirectory = layout.buildDirectory.dir("generated-sources/kotlin-dsl-extensions")
}


apply<KotlinBaseApiPlugin>()
plugins.withType(KotlinBaseApiPlugin::class) {
    @Suppress("DEPRECATION")
    registerKotlinJvmCompileTask(
        "compileGradleApiKotlinExtensions",
        "gradle-kotlin-dsl-extensions"
    )
}

tasks.register<GenerateClasspathModuleProperties>("generateCoreRuntimeModuleProperties") {
    configureFrom(coreRuntimeClasspath)
    outputDir = layout.buildDirectory.dir("classpathProperties/$name")
}

tasks.register<GenerateClasspathModuleProperties>("generateRuntimeModuleProperties") {
    configureFrom(runtimeClasspath)
    outputDir = layout.buildDirectory.dir("classpathProperties/$name")
}

tasks.register<GenerateClasspathModuleProperties>("generateAgentsRuntimeModuleProperties") {
    configureFrom(agentsRuntimeClasspath)
    outputDir = layout.buildDirectory.dir("classpathProperties/$name")
}

// Generate the component license section for the distribution LICENSE file.
// The output is used by GradleDistributionSpecs instead of the static root LICENSE.
val generateLicenseFile = tasks.register<GenerateLicenseFile>("generateLicenseFile") {
    baseLicenseFile = repoRoot().file("LICENSE")

    // Lazily resolve all POM files needed for license extraction, including parent POMs.
    // The provider is evaluated at configuration-cache fingerprinting time (part of the
    // configuration phase), where project.configurations access is permitted.
    pomFiles.from(provider {
        collectExternalPomFiles(listOf(runtimeClasspath, agentsRuntimeClasspath))
    })

    // Use the module properties output to get the authoritative list of components
    // that are actually present in the distribution (external ones have alias.group set)
    modulePropertiesFiles.from(
        tasks.named("generateRuntimeModuleProperties", GenerateClasspathModuleProperties::class)
            .map { it.outputDir.asFileTree }
    )
    modulePropertiesFiles.from(
        tasks.named("generateAgentsRuntimeModuleProperties", GenerateClasspathModuleProperties::class)
            .map { it.outputDir.asFileTree }
    )

    outputLicenseFile = layout.buildDirectory.file("generated-license/LICENSE")
}

val compileGradleApiKotlinExtensions = tasks.named("compileGradleApiKotlinExtensions", KotlinCompile::class) {
    configureKotlinCompilerForGradleBuild()
    multiPlatformEnabled = false
    compilerOptions.moduleName = "gradle-kotlin-dsl-extensions"
    source(gradleApiKotlinExtensions)
    libraries.from(runtimeClasspath)
    destinationDirectory = layout.buildDirectory.dir("classes/kotlin-dsl-extensions")
}

val gradleApiKotlinExtensionsJar = tasks.register<Jar>("gradleApiKotlinExtensionsJar") {
    archiveVersion = gradleModule.identity.version.map { it.baseVersion.version }
    manifest.attributes(
        mapOf(
            Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
            Attributes.Name.IMPLEMENTATION_VERSION.toString() to gradleModule.identity.version.map { it.baseVersion.version }
        )
    )
    archiveBaseName = "gradle-kotlin-dsl-extensions"
    from(gradleApiKotlinExtensions)
    from(compileGradleApiKotlinExtensions.flatMap { it.destinationDirectory })
}

fun generateModulePropertiesFor(moduleJar: TaskProvider<Jar>, moduleName: String): TaskProvider<GenerateEmptyModuleProperties> {
    return tasks.register<GenerateEmptyModuleProperties>(moduleJar.name + "ModuleProperties") {
        artifactFileName = moduleJar.flatMap { it.archiveFileName }
        outputFile = moduleJar.flatMap { it.archiveBaseName }.flatMap { generatedPropertiesFileFor(moduleName) }
    }
}

// At runtime, the module registry expects each module jar to have corresponding module properties file next to it.
// We generate synthetic properties files with no dependencies to allow these jars to be loaded from the distribution
generateModulePropertiesFor(runtimeApiInfoJar, runtimeApiJarName)
generateModulePropertiesFor(gradleApiKotlinExtensionsJar, "gradle-kotlin-dsl-extensions")

// A standard Java runtime variant for embedded integration testing
consumableVariant("runtime", listOf(coreRuntimeOnly, pluginsRuntimeOnly), listOf(runtimeApiInfoJar, gradleApiKotlinExtensionsJar)) {
    configureAsRuntimeElements(objects)
}

consumableVariant("api", listOf(coreRuntimeOnly, pluginsRuntimeOnly), listOf(runtimeApiInfoJar, gradleApiKotlinExtensionsJar)) {
    configureAsApiElements(objects)
}

// To make all source code of a distribution accessible transitively
consumableSourcesVariant("transitiveSources", listOf(coreRuntimeOnly, pluginsRuntimeOnly), gradleApiKotlinExtensions.map { it.destinationDirectory })
// A platform variant without 'runtime-api-info' artifact such that distributions can depend on each other
consumablePlatformVariant("runtimePlatform", listOf(coreRuntimeOnly, pluginsRuntimeOnly))

// A lifecycle task to build all the distribution zips for publishing
val buildDists = tasks.register("buildDists")

configureDistribution("normalized", binDistributionSpec(), buildDists, true)
configureDistribution("bin", binDistributionSpec(), buildDists)
configureDistribution("all", allDistributionSpec(), buildDists)
configureDistribution("docs", docsDistributionSpec(), buildDists)
configureDistribution("src", srcDistributionSpec(), buildDists)

fun pluginsManifestTask(name: String, runtimeClasspath: Configuration, coreRuntimeClasspath: Configuration, api: GradleModuleApiAttribute) =
    tasks.register<PluginsManifest>(name) {
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
        gradleModule.identity.version.map { "gradle-${it.baseVersion.version}" }
    } else {
        gradleModule.identity.version.map { "gradle-${it.version}" }.map {
            if (buildVersionQualifier.isPresent) it.replace("-${buildVersionQualifier.get()}", "")
            else it
        }
    }

    val installation = tasks.register<Sync>("${name}Installation") {
        group = "distribution"
        into(layout.buildDirectory.dir("$name distribution"))
        with(distributionSpec)
    }

    val distributionZip = tasks.register<Zip>("${name}DistributionZip") {
        archiveBaseName = "gradle"
        archiveClassifier = name
        archiveVersion = gradleModule.identity.version.map { it.baseVersion.version }

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
    consumableVariant("${name}Installation", emptyList(), listOf(installation)) {
        configureAsRuntimeElements(objects)
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("gradle-$name-installation"))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EMBEDDED))
        }
    }
    // A variant providing the zipped distribution as additional input for tests that test the final distribution or require a distribution as test data
    consumableVariant("${name}DistributionZip", emptyList(), listOf(distributionZip)) {
        configureAsRuntimeElements(objects)
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("gradle-$name-distribution-zip"))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EMBEDDED))
        }
    }
}

fun generatedBinFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.bin")

fun generatedTxtFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.txt")

fun generatedPropertiesFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.properties")

fun generatedJsonFileFor(name: String) =
    layout.buildDirectory.file("generated-resources/$name/$name.json")

fun bucket(name: String) =
    configurations.create(name) {
        isCanBeResolved = false
        isCanBeConsumed = false
    }

fun libraryResolver(name: String, extends: List<Configuration>) =
    configurations.create(name) {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        }
        isCanBeResolved = true
        isCanBeConsumed = false
        extends.forEach { extendsFrom(it) }
    }

fun startScriptResolver(name: String, defaultDependency: String) =
    configurations.create(name) {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named("start-scripts"))
        }
        isCanBeResolved = true
        isCanBeConsumed = false
        dependencies.addLater(provider {
            project.dependencies.create(project(defaultDependency))
        })
    }

fun sourcesResolver(name: String, extends: List<Configuration>) =
    configurations.create(name) {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-source-folders"))
        }
        isCanBeResolved = true
        isCanBeConsumed = false
        extends.forEach { extendsFrom(it) }
    }

fun docsResolver(name: String, defaultDependency: String) =
    configurations.create(name) {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-documentation"))
        }
        isCanBeResolved = true
        isCanBeConsumed = false
        dependencies.addLater(provider {
            project.dependencies.create(project(defaultDependency))
        })
    }

fun consumableVariant(name: String, extends: List<Configuration>, artifacts: List<Any>, configure: Action<Configuration> = Action {}) =
    configurations.create("${name}Elements") {
        isCanBeResolved = false
        isCanBeConsumed = true
        extends.forEach { extendsFrom(it) }
        artifacts.forEach { outgoing.artifact(it) }
        configure(this)
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
        extends.forEach { extendsFrom(it) }
    }

/**
 * Resolves POM files for all external Maven components reachable from the given configurations,
 * walking up the parent POM chain for any component whose direct POM has no [<licenses>] element.
 *
 * This function is invoked inside a lazy [provider] used as a task input, so it runs during the
 * configuration-cache fingerprinting phase (part of the configuration phase). At that point all
 * project dependencies are declared and [project.configurations] access is permitted.
 *
 * All resolutions hit the Gradle module cache — no network traffic is expected because Gradle
 * already downloaded these POMs during normal dependency resolution.
 */
fun collectExternalPomFiles(configs: List<Configuration>): List<File> {
    val toResolve = ArrayDeque<Triple<String, String, String>>()
    val resolved = mutableSetOf<String>()
    val pomFiles = mutableListOf<File>()

    // Seed with all external (Maven) components from each configuration
    for (config in configs) {
        config.incoming.resolutionResult.allComponents
            .filter { it.id is ModuleComponentIdentifier }
            .mapNotNull { it.moduleVersion }
            .forEach { mv -> toResolve.add(Triple(mv.group, mv.name, mv.version)) }
    }

    // BFS: resolve each POM and, if it has no <licenses>, also enqueue its <parent>
    while (toResolve.isNotEmpty()) {
        val (g, a, v) = toResolve.removeFirst()
        val key = "$g:$a:$v"
        if (resolved.add(key)) {
            val pomFile = runCatching {
                project.configurations
                    .detachedConfiguration(project.dependencies.create("$g:$a:$v@pom"))
                    .apply { isTransitive = false }
                    .singleFile
            }.getOrNull()
            if (pomFile != null) {
                pomFiles.add(pomFile)
                // Only follow the parent chain if the direct POM has no license — keeps the
                // number of resolved POMs small for components with inline license declarations.
                val parsed = PomLicenseUtils.parsePom(pomFile)
                if (parsed != null && parsed.licenseName == null) {
                    parsed.parent?.let { p -> toResolve.add(Triple(p.groupId, p.artifactId, p.version)) }
                }
            }
        }
    }

    return pomFiles
}
