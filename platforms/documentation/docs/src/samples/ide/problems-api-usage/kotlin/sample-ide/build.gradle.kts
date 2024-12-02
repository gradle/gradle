import java.net.URI

plugins {
    application
}

repositories {
    mavenCentral()
    maven {
        url = URI("https://repo.gradle.org/artifactory/libs-releases")
    }
}

application {
    mainClass.set("org.gradle.sample.SampleIde")
}

dependencies {
    implementation("reporters:model-builder-plugin")
    implementation("org.gradle:gradle-tooling-api:8.12")
}

tasks.run.configure {
    args = listOf(
        project.gradle.rootBuild().rootProject.projectDir.absolutePath, // The path of the project (this project's root)
        ":sample-project:assemble"  // The executed task
    )
}

tasks.register("importBuild") {
    dependsOn(tasks.run)
}

fun Gradle.rootBuild(): Gradle = parent?.rootBuild() ?: this
