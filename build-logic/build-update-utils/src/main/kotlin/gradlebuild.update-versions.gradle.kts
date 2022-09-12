
import com.google.gson.Gson
import gradlebuild.basics.releasedVersionsFile
import gradlebuild.buildutils.model.ReleasedVersion
import gradlebuild.buildutils.tasks.UpdateAgpVersions
import gradlebuild.buildutils.tasks.UpdateReleasedVersions
import java.net.URL


tasks.withType<UpdateReleasedVersions>().configureEach {
    releasedVersionsFile.set(releasedVersionsFile())
    group = "Versioning"
}

tasks.register<UpdateReleasedVersions>("updateReleasedVersions") {
    currentReleasedVersion.set(
        ReleasedVersion(
            project.findProperty("currentReleasedVersion").toString(),
            project.findProperty("currentReleasedVersionBuildTimestamp").toString()
        )
    )
}

tasks.register<UpdateReleasedVersions>("updateReleasedVersionsToLatestNightly") {
    currentReleasedVersion.set(
        project.provider {
            val jsonText = URL("https://services.gradle.org/versions/nightly").readText()
            println(jsonText)
            val versionInfo = Gson().fromJson(jsonText, VersionBuildTimeInfo::class.java)
            ReleasedVersion(versionInfo.version, versionInfo.buildTime)
        }
    )
}

tasks.register<UpdateAgpVersions>("updateAgpVersions") {
    comment.set(" Generated - Update by running `./gradlew updateAgpVersions`")
    minimumSupportedMinor.set("7.3")
    fetchNightly.set(false)
    propertiesFile.set(layout.projectDirectory.file("gradle/dependency-management/agp-versions.properties"))
}

data class VersionBuildTimeInfo(val version: String, val buildTime: String)
