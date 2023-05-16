import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.jar.Attributes

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
    compileOnly("org.ow2.asm:asm-tree:9.4")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("org.jetbrains:annotations:24.0.0")
}

tasks.jar {
    manifest.attributes(
        mapOf(
            Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
            Attributes.Name.IMPLEMENTATION_VERSION.toString() to project.version
        )
    )
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
