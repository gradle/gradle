plugins {
    `kotlin-dsl`
}

description = "Provides plugins for configuring miscellaneous things (repositories, reproducibility, minify)"

group = "gradlebuild"

dependencies {
    api("gradlebuild:build-environment")
    api(platform(projects.buildPlatform))

    implementation(buildLibs.guava) {
        because("Used by class analysis")
    }
    implementation(libs.asm) {
        because("Used by class analysis")
    }
    implementation(libs.asmCommons) {
        because("Used by class analysis")
    }

    compileOnly(buildLibs.kotlinCompilerEmbeddable) {
        because("Required by KotlinSourceParser")
    }
    implementation(buildLibs.kgp) {
        because("For manually defined KotlinSourceSet accessor - sourceSets.main.get().kotlin")
    }

    testImplementation(buildLibs.kotlinCompilerEmbeddable) {
        because("KotlinSourceParserTest parses Kotlin sources with the embeddable compiler")
    }
}

configurations.testRuntimeClasspath {
    // https://youtrack.jetbrains.com/issue/KT-87492/KGP-jar-bundles-a-partial-and-unshaded-copy-of-org.jetbrains.kotlin.com.intellij.-classes-that-collide-with-kotlin-compiler
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-gradle-plugin")
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(buildLibs.versions.junit)
        }
    }
}
