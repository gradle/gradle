package org.gradle.gradlebuild.java

import org.gradle.api.Plugin
import org.gradle.api.Project


open class AvailableJavaInstallationsPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val availableInstallations = gradle.sharedServices.registerIfAbsent("availableJavaInstallations", AvailableJavaInstallations::class.java) {
            // TODO:instant-execution - this should be marked as a build input in some way
            parameters.testJavaProperty = project.findProperty(testJavaHomePropertyName)?.toString()
        }
        extensions.create("buildJvms", BuildJvms::class.java, availableInstallations)
    }
}
