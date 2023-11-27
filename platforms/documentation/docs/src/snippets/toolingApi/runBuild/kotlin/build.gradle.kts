plugins {
    application
}

val toolingApiVersion = gradle.gradleVersion

// tag::use-tooling-api[]
repositories {
    maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
}

dependencies {
    implementation("org.gradle:gradle-tooling-api:$toolingApiVersion")
    // The tooling API need an SLF4J implementation available at runtime, replace this with any other implementation
    runtimeOnly("org.slf4j:slf4j-simple:1.7.10")
}
// end::use-tooling-api[]

application {
    mainClass = "org.gradle.sample.Main"
}
