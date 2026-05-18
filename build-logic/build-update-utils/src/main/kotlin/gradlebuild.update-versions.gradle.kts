import gradlebuild.basics.releasedVersionsFile
import gradlebuild.buildutils.model.ReleasedVersion
import gradlebuild.buildutils.tasks.FixProjectHealthTask
import gradlebuild.buildutils.tasks.PreparePatchRelease
import gradlebuild.buildutils.tasks.UpdateAgpVersions
import gradlebuild.buildutils.tasks.UpdateKotlinVersions
import gradlebuild.buildutils.tasks.UpdateReleasedVersions
import gradlebuild.buildutils.tasks.UpdateSmokeTestedIdeVersions
import gradlebuild.buildutils.tasks.UpdateSmokeTestedPluginsVersions

plugins {
    id("gradlebuild.module-identity")
}

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
    currentGradleVersion = gradleModule.identity.version
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

val updateSmokeTestedIdeVersions = tasks.register<UpdateSmokeTestedIdeVersions>("updateSmokeTestedIdeVersions") {
    comment = " Generated - Update by running `./gradlew updateSmokeTestedIdeVersions`"
    propertiesFile = layout.projectDirectory.file("gradle/dependency-management/smoke-tested-ides.properties")
    verificationMetadataFile = layout.projectDirectory.file("gradle/verification-metadata.xml")
}

tasks.register("updateSmokeTestedVersions") {
    dependsOn(updateKotlinVersions, updateAgpVersions, updateSmokeTestedPluginsVersions, updateSmokeTestedIdeVersions)
}

tasks.register<FixProjectHealthTask>("fixProjectHealth")

tasks.register<PreparePatchRelease>("preparePatchRelease") {
    group = "Versioning"
    description = "Prepares the repository for a patch release: bumps version.txt, updates released-versions.json, and clears accepted API changes."
    versionFile = layout.projectDirectory.file("version.txt")
    releasedVersionsFile = releasedVersionsFile()
    dependsOn(":architecture-test:cleanAcceptedApiChanges")
}
