import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Provides a plugin for configuring japicmp-gradle-plugin to detect binary incompatible changes"

dependencies {
    api("me.champeau.gradle:japicmp-gradle-plugin")

    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation("com.google.code.gson:gson")
    implementation("com.google.guava:guava")
    implementation("org.javassist:javassist")
    implementation("com.github.javaparser:javaparser-core")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm")
    implementation(kotlin("compiler-embeddable"))

    testImplementation("org.jsoup:jsoup")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
}

tasks.compileGroovy.configure {
    classpath += files(tasks.compileKotlin)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // Usage of deprecated kotlinx-metadata APIs
        allWarningsAsErrors = false
    }
}
