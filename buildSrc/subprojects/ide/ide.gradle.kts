dependencies {
    implementation(project(":configuration"))
    // TODO remove dependency once docs has publications
    implementation(project(":docs"))
    implementation(project(":kotlinDsl"))

    implementation("gradle.plugin.org.jetbrains.gradle.plugin.idea-ext:gradle-idea-ext:0.7")
}

gradlePlugin {
    plugins {
        register("ide") {
            id = "gradlebuild.ide"
            implementationClass = "org.gradle.gradlebuild.ide.IdePlugin"
        }
    }
}
