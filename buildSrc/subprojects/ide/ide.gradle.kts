dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":docs")) {
        because("we need to access the release notes location for the JUnit run configuration")
    }

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
