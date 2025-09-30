
import gradlebuild.basics.releasedVersionsFile
import gradlebuild.buildutils.model.ReleasedVersion
import gradlebuild.buildutils.tasks.FixProjectHealthTask
import gradlebuild.buildutils.tasks.UpdateAgpVersions
import gradlebuild.buildutils.tasks.UpdateKotlinVersions
import gradlebuild.buildutils.tasks.UpdateReleasedVersions
import gradlebuild.buildutils.tasks.UpdateSmokeTestedPluginsVersions

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(17)
}

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

val updateAgpVersions = tasks.register<UpdateAgpVersions>("updateAgpVersions") {
    comment = " Generated - Update by running `./gradlew updateAgpVersions`"
    minimumSupported = "8.4.0"
    propertiesFile = layout.projectDirectory.file("gradle/dependency-management/agp-versions.properties")
    compatibilityDocFile = layout.projectDirectory.file("platforms/documentation/docs/src/docs/userguide/releases/compatibility.adoc")
}

val updateKotlinVersions = tasks.register<UpdateKotlinVersions>("updateKotlinVersions") {
    comment = " Generated - Update by running `./gradlew updateKotlinVersions`"
    minimumSupported = "2.0.0"
    propertiesFile = layout.projectDirectory.file("gradle/dependency-management/kotlin-versions.properties")
    compatibilityDocFile = layout.projectDirectory.file("platforms/documentation/docs/src/docs/userguide/releases/compatibility.adoc")
}

val updateSmokeTestedPluginsVersions = tasks.register<UpdateSmokeTestedPluginsVersions>("updateSmokeTestedPluginsVersions") {
    comment = " Generated - Update by running `./gradlew updateSmokeTestedPluginsVersions`"
    propertiesFile = layout.projectDirectory.file("gradle/dependency-management/smoke-tested-plugins.properties")
}

tasks.register("updateSmokeTestedVersions") {
    dependsOn(updateKotlinVersions, updateAgpVersions, updateSmokeTestedPluginsVersions)
}

tasks.register<FixProjectHealthTask>("fixProjectHealth")
