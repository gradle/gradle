plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures for both unit tests and integration tests, internal use only"

sourceSets {
    main {
        // Incremental Groovy joint-compilation doesn't work with the Error Prone annotation processor
        errorprone.enabled = false
    }
}

dependencies {
    api(project(":base-services"))
    api(project(":concurrent"))
    api(project(":hashing"))
    api(project(":java-language-extensions"))
    api(project(":native"))

    api(libs.groovy)
    api(libs.groovyXml)
    api(libs.hamcrest)
    api(libs.hamcrestCore)
    api(libs.junit)
    api(libs.junit5JupiterApi)
    api(libs.spock)
    api(libs.spockJUnit4)

    implementation(project(":build-operations"))
    implementation(project(":functional"))
    implementation(project(":serialization"))

    implementation(libs.ant)
    implementation(libs.asm)
    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.jsoup)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)
    implementation(libs.testcontainers)

    runtimeOnly(libs.groovyJson)
    runtimeOnly(libs.bytebuddy)
}
