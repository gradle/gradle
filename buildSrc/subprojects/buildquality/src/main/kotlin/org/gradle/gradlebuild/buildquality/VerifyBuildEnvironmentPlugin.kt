package org.gradle.gradlebuild.buildquality

import availableJavaInstallations
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.nio.charset.Charset
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.kotlin.dsl.*


open class VerifyBuildEnvironmentPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        validateForProductionEnvironments(project)
        validateForAllCompileTasks(project)
    }

    private
    fun validateForProductionEnvironments(rootProject: Project) =
        rootProject.tasks.register("verifyIsProductionBuildEnvironment") {
            doLast {
                rootProject.availableJavaInstallations.validateForProductionEnvironment()
                val systemCharset = Charset.defaultCharset().name()
                assert(systemCharset == "UTF-8") {
                    "Platform encoding must be UTF-8. Is currently $systemCharset. Set -Dfile.encoding=UTF-8"
                }
            }
        }

    private
    fun validateForAllCompileTasks(rootProject: Project) {
        val availableJavaInstallations = rootProject.availableJavaInstallations
        val verifyBuildEnvironment = rootProject.tasks.register("verifyBuildEnvironment") {
            doLast {
                availableJavaInstallations.validateForCompilation()
            }
        }

        rootProject.subprojects {
            tasks.withType<AbstractCompile>().configureEach {
                dependsOn(verifyBuildEnvironment)
            }
        }
    }
}
