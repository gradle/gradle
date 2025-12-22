package com.example

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.initialization.Settings

class BaselineInitPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        println("[INIT-PLUGIN] apply() called")

        // Project-time: apply lightweight conventions
        gradle.beforeProject { project ->
            project.repositories.apply {
                if (isEmpty()) {
                    println("🔧 [INIT-PLUGIN] Adding default repositories to ${project.name}")
                    mavenCentral()
                    google()
                }
            }
        }
    }
}
