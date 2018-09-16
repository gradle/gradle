plugins {
    `java-gradle-plugin`
}

apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    // TODO remove dependency once docs has publications
    implementation(project(":docs"))
    implementation("org.jsoup:jsoup:1.11.3")
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
