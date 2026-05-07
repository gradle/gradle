plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::disableModulePath[]
java {
    modularity.inferModulePath = false
}

tasks.compileJava {
    modularity.inferModulePath = false
}
// end::disableModulePath[]

dependencies {
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.apache.commons:commons-lang3:3.10")
    implementation("commons-cli:commons-cli:1.4")
}
