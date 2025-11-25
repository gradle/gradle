package com.example

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle

class BaselineInitPlugin implements Plugin<Gradle> {

    void apply(Gradle gradle) {
        println("[INIT-PLUGIN] apply() called")

        // Project-time: apply lightweight conventions
        gradle.beforeProject { project ->
            project.repositories.with { repo ->
                if (repo.isEmpty()) {
                    println("🔧 [INIT-PLUGIN] Adding default repositories to ${project.name}")
                    mavenCentral()
                    google()
                }
            }
        }
    }
}