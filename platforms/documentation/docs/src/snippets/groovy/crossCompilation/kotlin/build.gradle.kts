plugins {
    groovy
}
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:2.4.15")
    testImplementation("junit:junit:4.13")
}

// tag::groovy-cross-compilation[]
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(7)
    }
}
// end::groovy-cross-compilation[]

