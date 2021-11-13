plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to configure conventions used by Kotlin, Java and Groovy projects to build Gradle"

dependencies {
    implementation(project(":basics"))
    implementation(project(":dependency-modules"))
    implementation(project(":module-identity"))

    implementation("org.eclipse.jgit:org.eclipse.jgit")
    implementation("org.jsoup:jsoup")
    implementation("com.google.guava:guava")
    implementation("org.ow2.asm:asm")
    implementation("org.ow2.asm:asm-commons")
    implementation("com.google.code.gson:gson")
    implementation("org.gradle:test-retry-gradle-plugin")
    implementation("com.gradle.enterprise:test-distribution-gradle-plugin")

    implementation("com.thoughtworks.qdox:qdox") {
        because("ParameterNamesIndex")
    }
}
