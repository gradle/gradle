package configurations

import common.Os
import common.requiresNotEc2Agent
import jetbrains.buildServer.configs.kotlin.BuildType
import model.Stage

open class BaseGradleBuildType(
    open val stage: Stage? = null,
    open val failStage: Boolean = true,
    init: BaseGradleBuildType.() -> Unit = {},
) : BuildType() {
    init {
        this.init()
        requirements {
            requiresNotEc2Agent() // FIXME: This is to avoid running on EC2 agents for comparison purposes
        }
    }
}

open class OsAwareBaseGradleBuildType(
    val os: Os,
    override val stage: Stage? = null,
    override val failStage: Boolean = true,
    init: BaseGradleBuildType.() -> Unit = {},
) : BaseGradleBuildType(stage, failStage, init)
