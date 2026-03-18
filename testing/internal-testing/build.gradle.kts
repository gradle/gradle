plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures for both unit tests and integration tests, internal use only"

dependencies {
    api(projects.baseServices)
    api(projects.concurrent)
    api(projects.hashing)
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)

    api(libs.groovy)
    api(testLibs.hamcrest)
    api(libs.jspecify)
    api(libs.jsr305)
    api(testLibs.junit)
    api(testLibs.junit5JupiterApi)
    api(testLibs.spock)

    implementation(projects.baseAsm)
    implementation(projects.buildOperations)
    implementation(projects.buildProcessServices)
    implementation(projects.functional)
    implementation(projects.native)
    implementation(projects.serialization)

    implementation(libs.ant)
    implementation(libs.asm)
    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.kotlinCompilerEmbeddable)
    implementation(libs.slf4jApi)
    implementation(testLibs.testcontainers)
    implementation(testLibs.dockerJavaApi)

    compileOnly(libs.kotlinStdlib)

    runtimeOnly(libs.groovyJson)
    runtimeOnly(testLibs.bytebuddy)
}

jvmCompile {
    compilations {
        named("main") {
            // These test fixtures are used by the tooling API tests, which still run on JVM 8
            targetJvmVersion = 8
        }
    }
}

sourceSets {
    main {
        // Incremental Groovy joint-compilation doesn't work with the Error Prone annotation processor
        errorprone.enabled = false
    }
}
