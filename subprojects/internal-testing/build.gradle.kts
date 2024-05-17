plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures for both unit tests and integration tests, internal use only"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":functional"))
    implementation(project(":native"))

    implementation(libs.groovy)
    implementation(libs.groovyJson)
    implementation(libs.groovyXml)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.ant)
    implementation(libs.asm)
    implementation(libs.asmTree)
    implementation(libs.junit)
    implementation(libs.junit5JupiterApi)
    implementation(libs.spock)
    implementation(libs.spockJUnit4)
    implementation(libs.jsoup)
    implementation(libs.testcontainersSpock)

    runtimeOnly(libs.bytebuddy)
}
