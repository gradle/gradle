package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType

open class BaseGradleBuildType(init: BaseGradleBuildType.() -> Unit = {}) : BuildType() {
    init {
        this.init()
    }
}
