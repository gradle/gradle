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
            param("enableIsolatedProjects", "false") // TODO: remove as soon as CI is compatible with enabling IP
            param("env.GRADLE_OPTS", "-Dorg.gradle.unsafe.isolated-projects=false")
        }
    })
