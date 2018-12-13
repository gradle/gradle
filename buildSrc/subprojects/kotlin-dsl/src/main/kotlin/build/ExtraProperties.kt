package build

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra


val Project.kotlinVersion
    get() = rootProject.extra["kotlinVersion"] as String


fun Project.futureKotlin(module: String) =
    "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"
