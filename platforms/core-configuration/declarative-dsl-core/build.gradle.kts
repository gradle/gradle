import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.publish-public-libraries")
    id("gradlebuild.shaded-jar")

    embeddedKotlin("plugin.serialization")
}

description = "Common shared classes used by the Declarative DSL"

shadedJar {
    shadedConfiguration.exclude(mapOf("group" to "org.gradle", "module" to "declarative-dsl-api"))
    shadedConfiguration.exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-compiler-embeddable"))
    shadedConfiguration.exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib"))
    shadedConfiguration.exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-reflect"))
    keepPackages = listOf("org.gradle.internal.declarativedsl")
    unshadedPackages = listOf("org.gradle", "gnu", "kotlin", "org.jetbrains", "org.gradle.declarative")
}

configurations {
    jarsToShade {
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
            // without adding these additional attribute, the wrong variant of the kotlinx-serialization
            // library gets selected for the shading configuration
        }
    }
    /*shadedRuntimeElements {
        attributes {
            attribute(GradleModuleApiAttribute.attribute, GradleModuleApiAttribute.IMPLEMENTATION)
        }
    }*/
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_1_9)
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}

dependencies {
    api(libs.futureKotlin("compiler-embeddable"))
    api(libs.futureKotlin("stdlib"))

    implementation(project(":declarative-dsl-api"))
    implementation(libs.futureKotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation(libs.futureKotlin("test-junit5"))
    testImplementation("org.jetbrains:annotations:24.0.1")
}
