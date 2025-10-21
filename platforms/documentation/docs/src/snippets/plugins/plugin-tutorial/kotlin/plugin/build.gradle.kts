// tag::publish[]
// tag::plugins[]
// tag::plugin-init[]
plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    // end::plugin-init[]
    // end::plugins[]
    // Apply the Maven Publishing plugin
    id("maven-publish")
    // tag::plugins[]
    // tag::plugin-init[]
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    // end::plugin-init[]
    // end::publish[]
    /*
    // tag::plugin-init[]
    alias(libs.plugins.kotlin.jvm)
    // end::plugin-init[]
     */
    // tag::publish[]
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    // tag::plugin-init[]
}
// end::plugin-init[]
// end::plugins[]

group = "org.example"
version = "1.0.0"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

// tag::slack-api[]
dependencies {
    // end::slack-api[]
    // Use the Kotlin JUnit 5 integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // tag::slack-api[]
    // Use the Java Slack Client APIs
    implementation("com.slack.api:slack-api-client:1.45.3")
}
// end::slack-api[]
// end::publish[]

/*
// tag::plugin-init[]

gradlePlugin {
    // Define the plugin
    val greeting by plugins.creating {
        id = "org.example.greeting"
        implementationClass = "org.example.PluginTutorialPlugin"
    }
}
// end::plugin-init[]
*/

// tag::publish[]

// tag::gradle-plugin[]
gradlePlugin {
    // Define the plugin
    plugins {
        create("slack") {
            id = "org.example.slack"
            implementationClass = "org.example.SlackPlugin"
        }
    }
}
// end::gradle-plugin[]
// end::publish[]

// tag::repo[]
publishing {
    repositories {
        maven {
            name = "localRepo"
            url = layout.buildDirectory.dir("local-repo").get().asFile.toURI()
        }
    }
}
// end::repo[]

// tag::test-config[]
// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {
}

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Add a task to run the functional tests
val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

gradlePlugin.testSourceSets.add(functionalTestSourceSet)

tasks.named<Task>("check") {
    // Run the functional tests as part of `check`
    dependsOn(functionalTest)
}

tasks.named<Test>("test") {
    // Use JUnit Jupiter for unit tests.
    useJUnitPlatform()
}
// end::test-config[]
