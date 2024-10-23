package configurations

import common.Os
import jetbrains.buildServer.configs.kotlin.BuildType
import model.Stage

open class BaseGradleBuildType(
    open val stage: Stage? = null,
    open val failStage: Boolean = true,
    init: BaseGradleBuildType.() -> Unit = {}
) : BuildType() {
    init {
        this.init()
    }
}

open class OsAwareBaseGradleBuildType(
    val os: Os,
    override val stage: Stage? = null,
    override val failStage: Boolean = true,
    init: BaseGradleBuildType.() -> Unit = {}
) : BaseGradleBuildType(stage, failStage, init)
