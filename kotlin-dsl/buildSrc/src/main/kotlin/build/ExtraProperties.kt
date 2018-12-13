package build

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra


fun loadExtraPropertiesOf(project: Project) = project.run {
    require(this == rootProject) {
        "Properties should be loaded by the root project only!"
    }
    extra["kotlinVersion"] = file("kotlin-version.txt").readText().trim()
}


val Project.kotlinVersion
    get() = rootProject.extra["kotlinVersion"] as String


fun Project.futureKotlin(module: String) =
    "org.jetbrains.kotlin:kotlin-$module:$kotlinVersion"
