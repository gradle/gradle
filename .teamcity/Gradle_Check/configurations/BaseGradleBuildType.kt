package configurations

import common.BuildCache
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import model.CIBuildModel
import model.Stage

open class BaseGradleBuildType(model: CIBuildModel, val stage: Stage? = null, usesParentBuildCache: Boolean = false, init: BaseGradleBuildType.() -> Unit = {}) : BuildType() {

    val buildCache: BuildCache =
        if (usesParentBuildCache) model.parentBuildCache
        else model.childBuildCache

    init {
        this.init()
        params {
            param("env.BOT_TEAMCITY_GITHUB_TOKEN", "%github.bot-teamcity.token%")
            param("env.GRADLE_CACHE_REMOTE_PASSWORD", "%gradle.cache.remote.password%")
        }
    }
}
