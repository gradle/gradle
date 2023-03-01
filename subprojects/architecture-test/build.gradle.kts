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
    currentClasspath(project(":distributions-full"))
    testImplementation(project(":base-services"))
    testImplementation(project(":model-core"))
    testImplementation(project(":file-temp"))
    testImplementation(project(":core"))
    testImplementation(libs.futureKotlin("stdlib"))
    testImplementation(libs.inject)

    testImplementation(libs.archunitJunit5)
    testImplementation(libs.guava)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj)

    testRuntimeOnly(project(":distributions-full"))
}

val acceptedApiChangesFile = layout.projectDirectory.file("src/changes/accepted-public-api-changes.json")

val verifyAcceptedApiChangesOrdering = tasks.register<gradlebuild.binarycompatibility.AlphabeticalAcceptedApiChangesTask>("verifyAcceptedApiChangesOrdering") {
    group = "verification"
    description = "Ensures the accepted api changes file is kept alphabetically ordered to make merging changes to it easier"
    apiChangesFile = acceptedApiChangesFile
}

val sortAcceptedApiChanges = tasks.register<gradlebuild.binarycompatibility.SortAcceptedApiChangesTask>("sortAcceptedApiChanges") {
    group = "verification"
    description = "Sort the accepted api changes file alphabetically"
    apiChangesFile = acceptedApiChangesFile
}

tasks.test {
    // Looks like loading all the classes requires more than the default 512M
    maxHeapSize = "1g"

    // Only use one fork, so freezing doesn't have concurrency issues
    maxParallelForks = 1

    systemProperty("org.gradle.public.api.includes", (PublicApi.includes + PublicKotlinDslApi.includes).joinToString(":"))
    systemProperty("org.gradle.public.api.excludes", (PublicApi.excludes + PublicKotlinDslApi.excludes).joinToString(":"))
    jvmArgumentProviders.add(ArchUnitFreezeConfiguration(
        project.file("src/changes/archunit_store"),
        providers.gradleProperty("archunitRefreeze").map { true })
    )

    dependsOn(verifyAcceptedApiChangesOrdering)
    enabled = flakyTestStrategy !=  FlakyTestStrategy.ONLY

    predictiveSelection {
        // PTS doesn't work well with architecture tests which scan all classes
        enabled = false
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
