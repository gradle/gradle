
import com.google.gson.Gson
import gradlebuild.basics.releasedVersionsFile
import gradlebuild.buildutils.model.ReleasedVersion
import gradlebuild.buildutils.tasks.UpdateAgpVersions
import gradlebuild.buildutils.tasks.UpdateKotlinEmbeddedVersion
import gradlebuild.buildutils.tasks.UpdateKotlinVersions
import gradlebuild.buildutils.tasks.UpdateReleasedVersions
import java.net.URL


tasks.withType<UpdateReleasedVersions>().configureEach {
    releasedVersionsFile = releasedVersionsFile()
    group = "Versioning"
}

tasks.register<UpdateReleasedVersions>("updateReleasedVersions") {
    currentReleasedVersion = ReleasedVersion(
        project.findProperty("currentReleasedVersion").toString(),
        project.findProperty("currentReleasedVersionBuildTimestamp").toString()
    )
}

tasks.register<UpdateReleasedVersions>("updateReleasedVersionsToLatestNightly") {
    currentReleasedVersion = project.provider {
        val jsonText = URL("https://services.gradle.org/versions/nightly").readText()
        println(jsonText)
        val versionInfo = Gson().fromJson(jsonText, VersionBuildTimeInfo::class.java)
        ReleasedVersion(versionInfo.version, versionInfo.buildTime)
    }
}

tasks.register<UpdateAgpVersions>("updateAgpVersions") {
    comment = " Generated - Update by running `./gradlew updateAgpVersions`"
    minimumSupportedMinor = "7.3"
    fetchNightly = false
    propertiesFile = layout.projectDirectory.file("gradle/dependency-management/agp-versions.properties")
}

tasks.register<UpdateKotlinVersions>("updateKotlinVersions") {
    comment = " Generated - Update by running `./gradlew updateKotlinVersions`"
    minimumSupported = "1.6.10"
    propertiesFile = layout.projectDirectory.file("gradle/dependency-management/kotlin-versions.properties")
}

tasks.register<UpdateKotlinEmbeddedVersion>("updateKotlinEmbeddedVersion") {
    rootDir = rootProject.layout.projectDirectory
    nextVersion = providers.gradleProperty("nextVersion")
    ignorePathsThatContain = listOf(
        // Smoke tests often have some Kotlin version checks
        "/smoke-test/",
        // Don't update compatibility and migration guide
        "compatibility.adoc",
        "userguide/migration/upgrading_version_",
        // Version here has to be upgraded with the kotlin-dsl plugin
        "build-logic-commons/gradle-plugin/build.gradle.kts"
    )
    ignoreLinesThatContain = listOf(
        // Dokka lags a bit behind
        "org.jetbrains.dokka:dokka-core:",
        "// Kotlin 1.8.0 has wrong warning message for assign plugin"
    )
}

data class VersionBuildTimeInfo(val version: String, val buildTime: String)
