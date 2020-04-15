package org.gradle.gradlebuild.buildquality

import buildJvms
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.gradlebuild.java.AvailableJavaInstallationsPlugin
import java.nio.charset.Charset


open class VerifyBuildEnvironmentPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        project.plugins.apply(AvailableJavaInstallationsPlugin::class.java)
        validateForProductionEnvironments(project)
    }

    private
    fun validateForProductionEnvironments(project: Project) =
        project.tasks.register("verifyIsProductionBuildEnvironment") {
            val buildJvms = project.buildJvms
            doLast {
                buildJvms.validateForProductionEnvironment()
                val systemCharset = Charset.defaultCharset().name()
                assert(systemCharset == "UTF-8") {
                    "Platform encoding must be UTF-8. Is currently $systemCharset. Set -Dfile.encoding=UTF-8"
                }
            }
        }
}
