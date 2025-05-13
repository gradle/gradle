import com.gradle.develocity.agent.gradle.test.DevelocityTestConfiguration
import gradlebuild.basics.FlakyTestStrategy
import gradlebuild.basics.PublicApi
import gradlebuild.basics.PublicKotlinDslApi
import gradlebuild.basics.flakyTestStrategy

plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.binary-compatibility")
}

description = """Verifies that Gradle code complies with architectural rules.
    | For example that nullable annotations are used consistently or that or that public api classes do not extend internal types.
""".trimMargin()

dependencies {
    currentClasspath(projects.distributionsFull)
    testImplementation(projects.baseServices)
    testImplementation(projects.modelCore)
    testImplementation(projects.fileTemp)
    testImplementation(projects.core)
    testImplementation(libs.kotlinStdlib)
    testImplementation(libs.inject)

    testImplementation(libs.archunitJunit5)
    testImplementation(libs.guava)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj)

    testRuntimeOnly(projects.distributionsFull)
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

val ruleStoreDir = layout.projectDirectory.dir("src/changes/archunit-store")

tasks {
    val reorderRuleStore by registering(ReorderArchUnitRulesTask::class) {
        ruleFile = ruleStoreDir.file("stored.rules").asFile
    }

    test {
        // Looks like loading all the classes requires more than the default 512M
        maxHeapSize = "1g"

        // Only use one fork, so freezing doesn't have concurrency issues
        maxParallelForks = 1

        inputs.dir(ruleStoreDir).withPathSensitivity(PathSensitivity.RELATIVE)

        systemProperty("org.gradle.public.api.includes", (PublicApi.includes + PublicKotlinDslApi.includes).joinToString(":"))
        systemProperty("org.gradle.public.api.excludes", (PublicApi.excludes + PublicKotlinDslApi.excludes).joinToString(":"))
        jvmArgumentProviders.add(
            ArchUnitFreezeConfiguration(
                ruleStoreDir.asFile,
                providers.gradleProperty("archunitRefreeze").map { true })
        )

        dependsOn(verifyAcceptedApiChangesOrdering)
        enabled = flakyTestStrategy != FlakyTestStrategy.ONLY

        extensions.findByType<DevelocityTestConfiguration>()?.apply {
            // PTS doesn't work well with architecture tests which scan all classes
            predictiveTestSelection.enabled = false
        }

        finalizedBy(reorderRuleStore)
    }
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
