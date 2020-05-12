package org.gradle.gradlebuild.java

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaInstallationRegistry


const val testJavaHomePropertyName = "testJavaHome"


open class AvailableJavaInstallationsPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val testJavaHomePath = providers.gradleProperty(testJavaHomePropertyName).forUseAtConfigurationTime()
            .orElse(providers.systemProperty(testJavaHomePropertyName).forUseAtConfigurationTime())
            .orElse(providers.environmentVariable(testJavaHomePropertyName).forUseAtConfigurationTime())
        val testJavaHome = rootProject.layout.projectDirectory.dir(testJavaHomePath)
        val installationRegistry = extensions.getByType(JavaInstallationRegistry::class.java)
        extensions.create("buildJvms", BuildJvms::class.java, installationRegistry, testJavaHome)
    }
}
