dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    // TODO remove dependency once docs has publications
    implementation(project(":docs"))
    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:0.4.2")
}

gradlePlugin {
    plugins {
        register("ide") {
            id = "gradlebuild.ide"
            implementationClass = "org.gradle.gradlebuild.ide.IdePlugin"
        }
    }
}
