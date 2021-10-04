plugins {
    id("gradlebuild.internal.java")
}

dependencies {
    implementation(project(":base-services"))
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
    implementation(libs.spock)
    implementation(libs.spockJUnit4)
    implementation(libs.jsoup)
    implementation(libs.testcontainersSpock)

    runtimeOnly(libs.bytebuddy)
}

tasks.withType<GroovyCompile>().configureEach {
    groovyOptions.forkOptions.jvmArgs = listOf("-Dspock.iKnowWhatImDoing.disableGroovyVersionCheck=true")
}

tasks.withType<Test>().configureEach {
    systemProperty("spock.iKnowWhatImDoing.disableGroovyVersionCheck", "true")
}
