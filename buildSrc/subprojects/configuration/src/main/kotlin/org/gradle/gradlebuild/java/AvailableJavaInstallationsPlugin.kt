package org.gradle.gradlebuild.java

import org.gradle.api.Plugin
import org.gradle.api.Project


// TODO We should not add this to the root project but a single instance to every subproject
open class AvailableJavaInstallationsPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val availableInstallations = gradle.sharedServices.maybeRegister("availableJavaInstallations", AvailableJavaInstallations::class.java) {
            parameters.testJavaProperty = project.findProperty(testJavaHomePropertyName)?.toString()
        }
        extensions.add("availableJavaInstallations", availableInstallations)
    }
}
