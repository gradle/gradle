@file:Suppress("UnstableApiUsage")

import com.gradle.develocity.agent.gradle.test.DevelocityTestConfiguration
import gradlebuild.basics.ArchitectureDataType
import gradlebuild.basics.DistributionArtifactScope
import gradlebuild.basics.PublicApi
import gradlebuild.basics.PublicKotlinDslApi
import gradlebuild.packageinfo.support.packageInfoDataVariant
import gradlebuild.packageinfo.support.packageInfoFilesFrom
import gradlebuild.packageinfo.tasks.AggregatePackageInfoDataTask

plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.binary-compatibility")
}

description = """Verifies that Gradle code complies with architectural rules.
    | For example that nullable annotations are used consistently or that or that public api classes do not extend internal types.
""".trimMargin()

val rootProjectDependency = configurations.dependencyScope("rootProjectDependency")
val platformsDataResolvable = configurations.resolvable("platformsDataResolvable") {
    extendsFrom(rootProjectDependency.get())
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(ArchitectureDataType.PLATFORMS))
    }
}

// Bucket for declaring the runtime-only distribution dependency, extended by the resolvable below.
val distributionRuntimeDependencies = configurations.dependencyScope("distributionRuntimeDependencies")

// Resolves to the module JARs of the full distribution WITHOUT triggering the packaging metadata
// pipeline (runtime-api-info jar and its whole-codebase-scanning derivation tasks).
// Selects the `runtimeJarsOnly` variant exposed by gradlebuild.distributions via the
// DistributionArtifactScope attribute. Transitive projects that do not advertise this attribute
// remain compatible via Gradle's default compatibility process.
// Its graph is also the source for the package-info data below, via variant reselection.
val distributionRuntime = configurations.resolvable("distributionRuntime") {
    extendsFrom(distributionRuntimeDependencies)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(Category.LIBRARY))
        attribute(DistributionArtifactScope.attribute, DistributionArtifactScope.RUNTIME_ONLY)
    }
}

dependencies {
    add(rootProjectDependency.name, projects.gradle)

    currentClasspath(projects.distributionsFull)
    testImplementation(projects.baseServices)
    testImplementation(projects.modelCore)
    testImplementation(projects.fileTemp)
    testImplementation(projects.core)
    testImplementation(libs.kotlinStdlib)
    testImplementation(libs.inject)

    testImplementation(testLibs.archunitJunit5)
    testImplementation(libs.guava)
    testImplementation(libs.gson)
    testImplementation(testLibs.junitJupiter)
    testImplementation(testLibs.assertj)

    add(distributionRuntimeDependencies.name, projects.distributionsFull)

    testRuntimeOnly(testLibs.junitPlatform)
}

val acceptedApiChangesDirectory = layout.projectDirectory.dir("src/changes/accepted-changes")

val verifyAcceptedApiChangesOrdering = tasks.register<gradlebuild.binarycompatibility.AlphabeticalAcceptedApiChangesTask>("verifyAcceptedApiChangesOrdering") {
    group = "verification"
    description = "Ensures the accepted api changes file is kept alphabetically ordered to make merging changes to it easier"
    apiChangesDirectory = acceptedApiChangesDirectory
}

val sortAcceptedApiChanges = tasks.register<gradlebuild.binarycompatibility.SortAcceptedApiChangesTask>("sortAcceptedApiChanges") {
    group = "verification"
    description = "Sort the accepted api changes file alphabetically"
    apiChangesDirectory = acceptedApiChangesDirectory
}

// Package-info data is produced per project by gradlebuild.package-info-data. Rather than re-derive which projects
// ship, reselect that variant over the distribution's already-resolved runtime graph: the graph shape then comes
// from `runtimeElements` semantics, so it covers exactly the modules whose bytecode the ArchUnit rules analyze.
// External modules have no such variant and are filtered out up front; a *project* without it is a wiring error
// and fails resolution rather than silently shrinking the data set.
val packageInfoDataFiles = distributionRuntime.map {
    it.incoming.artifactView {
        withVariantReselection()
        componentFilter { it is ProjectComponentIdentifier }
        attributes { packageInfoDataVariant(objects) }
    }.files
}

val aggregatePackageInfoData = tasks.register<AggregatePackageInfoDataTask>("aggregatePackageInfoData") {
    description = "Merges the per-project package-info data of every project in the distribution"
    projectData.from(packageInfoDataFiles)
    outputFile = layout.buildDirectory.file("architecture/package-info.json")
}

val ruleStoreDir = layout.projectDirectory.dir("src/changes/archunit-store")

tasks {
    val reorderRuleStore = register<ReorderArchUnitRulesTask>("reorderRuleStore") {
        ruleFile = ruleStoreDir.file("stored.rules").asFile
    }

    test {
        // Loading all the classes requires more than the default 512M,
        // and the per-module package cycle checks need additional headroom on top of that
        maxHeapSize = "2g"

        // Only use one fork, so freezing doesn't have concurrency issues
        maxParallelForks = 1

        // Provide the whole-distribution module bytecode via the leaner `runtimeJarsOnly`
        // variant. This is the classpath the ArchUnit @AnalyzeClasses(packages = "org.gradle")
        // scan reflects on.
        classpath += files(distributionRuntime)

        inputs.dir(ruleStoreDir).withPathSensitivity(PathSensitivity.RELATIVE)

        systemProperty("org.gradle.public.api.includes", (PublicApi.includes + PublicKotlinDslApi.includes).joinToString(":"))
        systemProperty("org.gradle.public.api.excludes", (PublicApi.excludes + PublicKotlinDslApi.excludes).joinToString(":"))

        jvmArgumentProviders.add(
            ArchUnitPlatformsData(
                layout.settingsDirectory.dir("platforms"),
                files(platformsDataResolvable),
            )
        )

        jvmArgumentProviders.add(
            PackageInfoData(
                layout.settingsDirectory,
                aggregatePackageInfoData.flatMap { it.outputFile },
                packageInfoFilesFrom(objects, layout.settingsDirectory, aggregatePackageInfoData.flatMap { it.outputFile }),
            )
        )

        jvmArgumentProviders.add(
            ArchUnitFreezeConfiguration(
                ruleStoreDir.asFile,
                providers.gradleProperty("archunitRefreeze").map { true })
        )

        dependsOn(verifyAcceptedApiChangesOrdering)

        extensions.findByType<DevelocityTestConfiguration>()?.apply {
            // PTS doesn't work well with architecture tests which scan all classes
            predictiveTestSelection.enabled = false
        }

        finalizedBy(reorderRuleStore)
    }
}

class PackageInfoData(
    @get:Internal
    val basePath: Directory,
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val json: Provider<RegularFile>,
    /**
     * The package-info.java files the test reads. The JSON only records their paths, so without declaring their
     * contents the test would stay UP-TO-DATE when a package-info is edited in place.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val packageInfoFiles: FileCollection,
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> = listOf(
        "-Dorg.gradle.architecture.package-info-base-path=${basePath.asFile.absolutePath}",
        "-Dorg.gradle.architecture.package-info-json=${json.get().asFile.absolutePath}",
    )
}

class ArchUnitPlatformsData(
    @get:Internal
    val basePath: Directory,
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val json: FileCollection,
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> = listOf(
        "-Dorg.gradle.architecture.platforms-base-path=${basePath.asFile.absolutePath}",
        "-Dorg.gradle.architecture.platforms-json=${json.singleFile}",
    )
}

class ArchUnitFreezeConfiguration(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val location: File,
    @get:Optional
    @get:Input
    val refreeze: Provider<Boolean>
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> {
        val refreezeBoolean = refreeze.getOrElse(false)
        return listOf(
            "-Darchunit.freeze.store.default.path=${location.absolutePath}",
            "-Darchunit.freeze.refreeze=${refreezeBoolean}",
            "-Darchunit.freeze.store.default.allowStoreUpdate=${refreezeBoolean}"
        )
    }
}

/**
 * Sorts the stored rules, so we keep a deterministic order when we add new rules.
 */
abstract class ReorderArchUnitRulesTask : DefaultTask() {
    @get:OutputFile
    abstract var ruleFile: File

    @TaskAction
    fun resortStoredRules() {
        val lines = ruleFile.readLines()
        val sortedLines = lines.sortedBy { line ->
            // We sort by the rule name
            line.substringBefore("=")
        }

        if (lines != sortedLines) {
            ruleFile.writeText(sortedLines.joinToString("\n"))
        }
    }
}

errorprone {
    nullawayEnabled = true
}
