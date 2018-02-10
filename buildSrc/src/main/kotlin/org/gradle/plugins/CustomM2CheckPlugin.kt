package org.gradle.plugins

import config
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.SystemProperties

import org.gradle.kotlin.dsl.*
import java.io.File

open class CustomM2CheckPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val checkForCleanM2Repository by tasks.creating {
            onlyIf { /* 2.10 was released & ci infrastructure was cleaned */ false }
            doLast {
                val m2RepositoryFolder = File("${SystemProperties.getInstance().userHome}/.m2/repository")
                if (m2RepositoryFolder.exists()) {
                    project.delete(m2RepositoryFolder)
                    throw GradleException("Build fails due to polluting user home '~/.m2/repository' directory")
                }
            }
        }

        if (config.isCiServer) {
            allprojects {
                tasks.withType(Test::class.java) {
                    finalizedBy(checkForCleanM2Repository)
                }
            }
        }

    }
}
