package projects

import common.VersionedSettingsBranch
import common.isSecurityFork
import jetbrains.buildServer.configs.kotlin.Project
import model.CIBuildModel
import model.DefaultFunctionalTestBucketProvider
import model.JsonBasedGradleSubprojectProvider
import promotion.PromotionProject
import util.UtilPerformanceProject
import util.UtilProject
import java.io.File

class GradleBuildToolRootProject(
    branch: VersionedSettingsBranch,
) : Project({
        val model =
            CIBuildModel(
                projectId = "Check",
                branch = branch,
                buildScanTags = listOf("Check"),
                subprojects = JsonBasedGradleSubprojectProvider(File("./subprojects.json")),
            )
        val gradleBuildBucketProvider = DefaultFunctionalTestBucketProvider(model, File("./test-buckets.json"))
        subProject(CheckProject(model, gradleBuildBucketProvider))

        if (!isSecurityFork()) {
            subProject(PromotionProject(model.branch))
            subProject(UtilProject)
            subProject(UtilPerformanceProject)
        }

        params {
            // Kept as a parameter so that Isolated Projects can be switched off again for a single build
            // or a whole dependency tree (`reverse.dep.*.enableIsolatedProjects=false`) without a settings change.
            param("enableIsolatedProjects", "true")
            param("env.GRADLE_OPTS", "-Dorg.gradle.isolated-projects=false")
        }
    })
