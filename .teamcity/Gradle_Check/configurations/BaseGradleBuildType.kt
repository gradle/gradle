package configurations

import common.BuildCache
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import model.CIBuildModel
import model.Stage

open class BaseGradleBuildType(model: CIBuildModel, val stage: Stage? = null, usesParentBuildCache: Boolean = false, init: BaseGradleBuildType.() -> Unit = {}) : BuildType() {

    val buildCache: BuildCache = if (usesParentBuildCache) model.parentBuildCache else model.childBuildCache

    init {
        this.init()
    }
}
