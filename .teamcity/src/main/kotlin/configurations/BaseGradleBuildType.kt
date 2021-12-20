package configurations

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import model.CIBuildModel
import model.Stage

open class BaseGradleBuildType(model: CIBuildModel, val stage: Stage? = null, usesParentBuildCache: Boolean = false, init: BaseGradleBuildType.() -> Unit = {}) : BuildType() {
    init {
        this.init()
        params {
            param("env.BOT_TEAMCITY_GITHUB_TOKEN", "%github.bot-teamcity.token%")
            param("env.GRADLE_CACHE_REMOTE_PASSWORD", "%gradle.cache.remote.password%")
            param("env.GRADLE_CACHE_REMOTE_URL", "%gradle.cache.remote.url%")
            param("env.GRADLE_CACHE_REMOTE_USERNAME", "%gradle.cache.remote.username%")
            param("env.GRADLE_INTERNAL_REPO_URL", "%gradle.internal.repository.url%")
        }
        features {
            enablePullRequestFeature()
        }
    }
}
