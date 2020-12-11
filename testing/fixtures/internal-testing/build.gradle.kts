plugins {
    id("gradlebuild.internal.java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:native")

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
    implementation(libs.spock)
    implementation(libs.spockJUnit4)
    implementation(libs.jsoup)
    implementation(libs.testcontainersSpock)

    runtimeOnly(libs.bytebuddy)
}
