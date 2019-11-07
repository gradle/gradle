package org.gradle.gradlebuild.buildquality

import availableJavaInstallations
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.gradlebuild.java.AvailableJavaInstallationsPlugin
import org.gradle.kotlin.dsl.*
import java.nio.charset.Charset


open class VerifyBuildEnvironmentPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        project.plugins.apply(AvailableJavaInstallationsPlugin::class.java)
        validateForProductionEnvironments(project)
        validateForAllCompileTasks(project)
    }

    private
    fun validateForProductionEnvironments(project: Project) =
        project.tasks.register("verifyIsProductionBuildEnvironment") {
            val availableJavaInstallations = project.availableJavaInstallations
            doLast {
                availableJavaInstallations.get().validateForProductionEnvironment()
                val systemCharset = Charset.defaultCharset().name()
                assert(systemCharset == "UTF-8") {
                    "Platform encoding must be UTF-8. Is currently $systemCharset. Set -Dfile.encoding=UTF-8"
                }
            }
        }

    private
    fun validateForAllCompileTasks(project: Project) {
        val verifyBuildEnvironment = project.tasks.register("verifyBuildEnvironment") {
            val availableJavaInstallations = project.availableJavaInstallations
            doLast {
                availableJavaInstallations.get().validateForCompilation()
            }
        }

        project.subprojects {
            tasks.withType<AbstractCompile>().configureEach {
                dependsOn(verifyBuildEnvironment)
            }
        }
    }
}
