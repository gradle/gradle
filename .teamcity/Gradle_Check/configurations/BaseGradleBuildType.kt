package configurations

import jetbrains.buildServer.configs.kotlin.v2017_2.BuildType
import model.BuildCache
import model.CIBuildModel

open class BaseGradleBuildType(model: CIBuildModel, init: BaseGradleBuildType.() -> Unit = {}, usesParentBuildCache: Boolean = false) : BuildType() {

    val buildCache: BuildCache = if (usesParentBuildCache) model.parentBuildCache else model.childBuildCache

    init {
        this.init()
    }
}
