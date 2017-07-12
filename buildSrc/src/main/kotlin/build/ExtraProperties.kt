package build

import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension


fun loadExtraPropertiesOf(project: Project) = project.run {
    require(this == rootProject) {
        "Properties should be loaded by the root project only!"
    }
    val kotlinVersion = file("kotlin-version.txt").readText().trim()
    val kotlinRepo = "https://repo.gradle.org/gradle/repo"
    extra["kotlinVersion"] = kotlinVersion
    extra["kotlinRepo"] = kotlinRepo
}


val Project.kotlinVersion get() = rootProject.extra["kotlinVersion"] as String


val Project.kotlinRepo get() = rootProject.extra["kotlinRepo"] as String


fun Project.futureKotlin(module: String) = "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"


private
val Project.extra: ExtraPropertiesExtension get() = extensions.extraProperties

