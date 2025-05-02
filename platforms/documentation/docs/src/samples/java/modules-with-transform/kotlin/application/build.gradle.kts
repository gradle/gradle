plugins {
    application
    id("extra-java-module-info") // apply my own plugin written in buildSrc
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    mavenCentral()
}

tasks.compileJava {
    options.javaModuleVersion = provider({ version as String })
}

// tag::extraModuleInfo[]
extraJavaModuleInfo {
    // This does not have to be a complete description (e.g. here 'org.apache.commons.collections' does not export anything here).
    // It only needs to be good enough to work in the context of this application we are building.
    module("commons-beanutils-1.9.4.jar", "org.apache.commons.beanutils", "1.9.4") {
        exports("org.apache.commons.beanutils")

        requires("org.apache.commons.logging")
        requires("java.sql")
        requires("java.desktop")
    }
    module("commons-cli-1.4.jar", "org.apache.commons.cli", "3.2.2") {
        exports("org.apache.commons.cli")
    }
    module("commons-collections-3.2.2.jar", "org.apache.commons.collections", "3.2.2")
    automaticModule("commons-logging-1.2.jar", "org.apache.commons.logging")
}
// end::extraModuleInfo[]

dependencies {
    implementation("com.google.code.gson:gson:2.13.0")          // real module
    implementation("org.apache.commons:commons-lang3:3.10")     // automatic module
    implementation("commons-beanutils:commons-beanutils:1.9.4") // plain library (also brings in other libraries transitively)
    implementation("commons-cli:commons-cli:1.4")               // plain library
}

application {
    mainModule = "org.gradle.sample.app"
    mainClass = "org.gradle.sample.app.Main"
}
