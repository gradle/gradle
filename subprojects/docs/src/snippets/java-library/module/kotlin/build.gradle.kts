plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::declareVersion[]
version = "1.1"

tasks.compileJava {
    // if set, the following takes precedence over the version defined above
    options.javaModuleVersion.set("1.2")
}
// end::declareVersion[]

// tag::inferModulePath[]
java {
    modularity.inferModulePath.set(true)
}
// end::inferModulePath[]

// tag::dependencies[]
dependencies {
    implementation("com.google.code.gson:gson:2.8.6")       // real module
    implementation("org.apache.commons:commons-lang3:3.10") // automatic module
    implementation("commons-cli:commons-cli:1.4")           // plain library
}
// end::dependencies[]

// tag::automaticModuleName[]
tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "org.gradle.sample")
    }
}
// end::automaticModuleName[]
