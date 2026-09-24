// tag::init-og[]
// tag::init[]
// tag::init-plugins[]
plugins {   // <1>
    // Apply the application plugin to add support for building a CLI application in Java.
    application
    // Apply the maven publish plugin
    id("maven-publish")
}
// end::init[]
// end::init-plugins[]

// tag::init-declarations[]
repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

// tag::init[]
// tag::init-dep[]
dependencies {  // <2>
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)
}
// end::init[]
// end::init-dep[]
// end::init-declarations[]

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// tag::init[]
// tag::init-app[]
application {   // <3>
    // Define the main class for the application.
    mainClass = "org.example.App"
}
// end::init[]
// end::init-app[]

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
// end::init-og[]

// tag::init-task[]
tasks.register<Copy>("copyTask") {
    from("source")
    into("target")
    include("*.war")
}
// end::init-task[]

// tag::init-hello[]
tasks.register("hello") {
    doLast {
        println("Hello!")
    }
}

tasks.register("greet") {
    doLast {
        println("How are you?")
    }
    dependsOn("hello")
}
// end::init-hello[]

// tag::init-publish[]
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.gradle.tutorial"
            artifactId = "tutorial"
            version = "1.0"

            from(components["java"])
        }
    }
}
// end::init-publish[]
