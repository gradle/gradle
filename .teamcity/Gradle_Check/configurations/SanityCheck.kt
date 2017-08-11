package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.buildFeatures.commitStatusPublisher
import model.CIBuildModel

class SanityCheck(model: CIBuildModel) : BuildType({
    uuid = "${model.projectPrefix}SanityCheck"
    extId = uuid
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    features {
        commitStatusPublisher {
            vcsRootExtId = "Gradle_Branches_GradlePersonalBranches"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:5306bfc7-041e-46e8-8d61-1d49424e7b04"
                }
            }
        }
    }

    applyDefaults(model, this, "compileAll sanityCheck", extraParameters = "-DenableCodeQuality=true --parallel")
})
