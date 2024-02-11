package configurations

import jetbrains.buildServer.configs.kotlin.BuildType
import model.Stage

open class BaseGradleBuildType(
    val stage: Stage? = null,
    val failStage: Boolean = true,
    init: BaseGradleBuildType.() -> Unit = {}
) : BuildType() {
    init {
        this.init()
    }
}
