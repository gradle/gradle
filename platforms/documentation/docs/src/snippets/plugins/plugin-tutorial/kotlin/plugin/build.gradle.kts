// tag::plugins[]
// tag::plugin-init[]
plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`

    // Apply the Kotlin JVM plugin to add support for Kotlin.
    // end::plugin-init[]
    /*
    // tag::plugin-init[]
    alias(libs.plugins.kotlin.jvm)
    // end::plugin-init[]
     */
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    // tag::plugin-init[]
}
// end::plugin-init[]
// end::plugins[]

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

// tag::gradle-plugin[]
gradlePlugin {
    plugins {
        create("slack") {
            id = "org.example.slack"
            implementationClass = "org.example.SlackPlugin"
        }
    }
}
// end::gradle-plugin[]

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
