package configurations

import common.Os
import common.requiresOs
import model.CIBuildModel
import model.Stage

/**
 * The performance tests use commit version as baseline, like `7.5-commit-1a2b3c`. It usually takes 1~2min to build the distribution on different OSes.
 * To save some time, we build the commit baseline distribution in the early stage and make it artifact dependency.
 */
class BuildCommitBaselineDistribution(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(stage = stage, init = {
    id(buildTypeId(model))
    name = "Build Commit Baseline Distribution"
    description = "Build the commit baseline distribution for performance tests"

    requirements.requiresOs(Os.LINUX)

    applyDefaults(
        model,
        this,
        "performance:buildCommitDistribution"
    )

    artifactRules = """
            |intTestHomeDir/commit-distributions/gradle-*.zip => intTestHomeDir/commit-distributions
            |intTestHomeDir/commit-distributions/gradle-tooling-api-*.jar => intTestHomeDir/commit-distributions
            |""".trimMargin()
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectId}_BuildCommitBaselineDistribution"
    }
}
