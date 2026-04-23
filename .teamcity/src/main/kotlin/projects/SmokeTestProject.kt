package projects

import common.uuidPrefix
import configurations.OsAwareBaseGradleBuildType
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.Project
import model.CIBuildModel
import model.SpecificBuild
import model.Stage

/**
 * Subproject under [StageProject] that groups smoke test build configurations.
 */
class SmokeTestProject(
    model: CIBuildModel,
    stage: Stage,
    smokeBuildTypes: List<OsAwareBaseGradleBuildType>,
) : Project({
        id("${model.projectId}_Stage_${stage.stageName.id}_SmokeTest")
        uuid = "${DslContext.uuidPrefix}_${model.projectId}_Stage_${stage.stageName.uuid}_SmokeTest"
        name = "Smoke Test"
        description = "Smoke tests against third-party plugins, Gradle build, and IDE sync"
    }) {
    init {
        smokeBuildTypes.forEach(this::buildType)
    }

    companion object {
        /**
         * Specific builds shown under the "Smoke Test" subproject in Pull Request Feedback.
         */
        val pullRequestFeedbackSmokeBuilds: Set<SpecificBuild> =
            setOf(
                SpecificBuild.SmokeTestsMaxJavaVersion,
                SpecificBuild.ConfigCacheAndroidProjectSmokeTests,
                SpecificBuild.GradleBuildSmokeTests,
                SpecificBuild.ConfigCacheSmokeTestsMaxJavaVersion,
                SpecificBuild.ConfigCacheSmokeTestsMinJavaVersion,
                SpecificBuild.SmokeIdeTests,
                SpecificBuild.Gradleception,
            )
    }
}
