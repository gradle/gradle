import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version embeddedKotlinVersion
}

group = "org.gradle"
version = file("../../version.txt").readText().trim()

base {
    archivesName = "gradle-${project.name}"
}


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
        vendor = JvmVendorSpec.ADOPTIUM
    }
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType(KotlinCompile::class).configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}
