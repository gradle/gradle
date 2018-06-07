import build.*

plugins {
    id("public-kotlin-dsl-module")
    `java-gradle-plugin`
}

base {
    archivesBaseName = "gradle-kotlin-dsl-build-plugins"
}

gradlePlugin {
    (plugins) {
        "java-api-extensions" {
            id = "org.gradle.kotlin.dsl.build.java-api-extensions"
            implementationClass = "org.gradle.kotlin.dsl.build.plugins.KotlinDslJavaApiExtensionsPlugin"
        }
    }
}

dependencies {

    compileOnly(gradleApi())
    compileOnly(project(":provider"))

    implementation("com.thoughtworks.qdox:qdox:2.0-M8")
    implementation(futureKotlin("gradle-plugin"))

    testImplementation(project(":provider"))
    testImplementation(project(":test-fixtures"))
}


val processResources: ProcessResources by tasks
val writeKotlinDslProviderVersion by tasks.creating(WriteProperties::class) {
    outputFile = processResources.destinationDir.resolve("${base.archivesBaseName}-versions.properties")
    property("kotlin-dsl", version)
}
processResources.dependsOn(writeKotlinDslProviderVersion)


val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(customInstallation)
    }
}


withParallelTests()
