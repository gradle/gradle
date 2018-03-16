import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {

    val kotlinVersion = file("../kotlin-version.txt").readText().trim()

    dependencies {
        classpath(kotlin("gradle-plugin", version = kotlinVersion))
    }

    configure(listOf(repositories, project.repositories)) {
        gradlePluginPortal()
    }
}

plugins {
    `java-gradle-plugin`
    id("org.gradle.kotlin.ktlint-convention") version "0.1.2"
}

apply(plugin = "kotlin")

gradlePlugin {
    (plugins) {
        "kotlinLibrary" {
            id = "kotlin-library"
            implementationClass = "plugins.KotlinLibrary"
        }
        "kotlinDslModule" {
            id = "kotlin-dsl-module"
            implementationClass = "plugins.KotlinDslModule"
        }
        "publicKotlinDslModule" {
            id = "public-kotlin-dsl-module"
            implementationClass = "plugins.PublicKotlinDslModule"
        }
        "kotlinDslPluginBundle" {
            id = "kotlin-dsl-plugin-bundle"
            implementationClass = "plugins.KotlinDslPluginBundle"
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-Xskip-runtime-version-check"
        )
    }
}

dependencies {
    compile(gradleApi())
    compile(kotlin("gradle-plugin"))
    compile(kotlin("stdlib-jdk8"))
    compile(kotlin("reflect"))
    compile("gradle.plugin.org.gradle.kotlin:gradle-kotlin-dsl-plugins-experiments:0.1.2")
    compile("com.gradle.publish:plugin-publish-plugin:0.9.10")
    compile("org.ow2.asm:asm-all:5.1")
    testCompile("junit:junit:4.12")
    testCompile(gradleTestKit())
}
