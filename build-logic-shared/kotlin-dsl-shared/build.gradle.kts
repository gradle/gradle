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

dependencies {
    compileOnly(gradleApi())
    compileOnly("org.ow2.asm:asm-tree:9.4")
}

// TODO this is a workaround for making the configuration cache work
val minified = Attribute.of("minified", Boolean::class.javaObjectType)
configurations.all {
    if (isCanBeConsumed && !isCanBeResolved) {
        if (
            attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name == Category.LIBRARY
            && attributes.getAttribute(Bundling.BUNDLING_ATTRIBUTE)?.name == Bundling.EXTERNAL
        ) {
            attributes.attribute(minified, true)
        }
    }
}
