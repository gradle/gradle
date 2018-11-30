package configurations_g4

import jetbrains.buildServer.configs.kotlin.v2018_1.BuildType
import model_g4.BuildCache
import model_g4.CIBuildModel
import model_g4.Stage

open class BaseGradleBuildType(model: CIBuildModel, val stage: Stage? = null, usesParentBuildCache: Boolean = false, init: BaseGradleBuildType.() -> Unit = {}) : BuildType() {

    val buildCache: BuildCache = if (usesParentBuildCache) model.parentBuildCache else model.childBuildCache

    init {
        this.init()
    }
}
