plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Services and types used to setup a build process from a Gradle distribution."

jvmCompile {
    compilations {
        named("testFixtures") {
            // The TAPI cross version tests depend on these test fixtures
            targetJvmVersion = 8
        }
    }
}

dependencies {
    api(projects.classloaders)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)

    implementation(projects.baseServices)

    testImplementation(libs.asm)
    testImplementation(libs.asmTree)

    testRuntimeOnly(projects.resources)
}

errorprone {
    nullawayEnabled = true
}
