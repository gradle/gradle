plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.binary-compatibility")
}

dependencies {
    currentClasspath(project(":distributions-full"))
    testImplementation(project(":base-services"))
    testImplementation(project(":model-core"))
    testImplementation(project(":file-temp"))

    testImplementation(libs.archunitJunit5)
    testImplementation(libs.guava)

    testRuntimeOnly(project(":distributions-full"))
}

tasks.test {
    // Looks like loading all the classes requires more than the default 512M
    maxHeapSize = "900M"

    // Only use one fork, so freezing doesn't have concurrency issues
    maxParallelForks = 1
    useJUnitPlatform()

    systemProperty("org.gradle.public.api.includes", gradlebuild.basics.PublicApi.includes.joinToString(":"))
    systemProperty("org.gradle.public.api.excludes", gradlebuild.basics.PublicApi.excludes.joinToString(":"))
    jvmArgumentProviders.add(ArchUnitFreezeConfiguration(
        project.file("src/changes/archunit_store"),
        providers.gradleProperty("archunitRefreeze").map { true })
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
