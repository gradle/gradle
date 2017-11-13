package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType
import model.CIBuildModel

open class BaseGradleBuildType(model: CIBuildModel, init: BaseGradleBuildType.() -> Unit = {}, val usesParentBuildCache: Boolean = false) : BuildType() {

    val buildCacheParameters: String

    init {
        buildCacheParameters = if (model.buildCacheActive) {
            val buildCacheUrl = if (usesParentBuildCache) model.parentBuildCache else model.childBuildCache
            gradleBuildCacheParameters(buildCacheUrl).joinToString(separator = " ")
        } else {
            ""
        }
        this.init()
    }
}
