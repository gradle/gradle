package org.gradle.plugins.config

import org.gradle.api.Project
import org.gradle.build.ReleasedVersionsFromVersionControl
import java.io.File

open class ConfigExtension(project: Project) {
    val isCiServer = System.getenv().containsKey("CI")
    val useAllDistribution = project.hasProperty("useAllDistribution")
    val releasedVersions = ReleasedVersionsFromVersionControl(File(project.rootDir, "released-versions.json"))
}
