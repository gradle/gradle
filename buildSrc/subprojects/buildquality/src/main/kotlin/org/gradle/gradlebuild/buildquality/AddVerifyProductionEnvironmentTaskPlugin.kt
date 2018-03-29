package org.gradle.gradlebuild.buildquality

import availableJavaInstallations
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.nio.charset.Charset


open class AddVerifyProductionEnvironmentTaskPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        tasks.create("verifyIsProductionBuildEnvironment") {
            doLast {
                rootProject.availableJavaInstallations {
                    validateProductionEnvironment()
                }
                val systemCharset = Charset.defaultCharset().name()
                assert(systemCharset == "UTF-8") {
                    "Platform encoding must be UTF-8. Is currently $systemCharset. Set -Dfile.encoding=UTF-8"
                }
            }
        }
    }
}
