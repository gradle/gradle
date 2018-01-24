import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {

    val kotlinVersion = file("../kotlin-version.txt").readText().trim()

    dependencies {
        classpath(kotlin("gradle-plugin", version = kotlinVersion))
    }

    configure(listOf(repositories, project.repositories)) {
        maven(url = "https://repo.gradle.org/gradle/repo")
    }
}

plugins {
    `java-gradle-plugin`
}

apply {
      plugin("kotlin")
}

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
    compile("org.ow2.asm:asm-all:5.1")
    testCompile("junit:junit:4.12")
    testCompile(gradleTestKit())
}
