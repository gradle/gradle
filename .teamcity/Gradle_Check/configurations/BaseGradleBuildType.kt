package configurations

import common.BuildCache
import common.NoBuildCache
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import model.CIBuildModel
import model.Stage

open class BaseGradleBuildType(model: CIBuildModel, val stage: Stage? = null, usesParentBuildCache: Boolean = false, init: BaseGradleBuildType.() -> Unit = {}) : BuildType() {

    val buildCache: BuildCache =
        if (stage != null && stage.disablesBuildCache) NoBuildCache
        else if (usesParentBuildCache) model.parentBuildCache
        else model.childBuildCache

    init {
        this.init()
    }
}
