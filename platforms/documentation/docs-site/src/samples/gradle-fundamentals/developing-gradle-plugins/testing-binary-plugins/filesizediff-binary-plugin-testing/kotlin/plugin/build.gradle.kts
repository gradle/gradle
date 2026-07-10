// tag::simple[]
plugins {
    `java-gradle-plugin`    // <1>
}

// tag::plugin-id[]
group = "org.example"   // <3>
version = "1.0.0"
// end::plugin-id[]

repositories {
    mavenCentral()
}

dependencies {  // <2>
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// tag::plugin-id[]
gradlePlugin {  // <3>
    plugins {
        create("filesizediff") {
            id = "org.example.filesizediff"
            implementationClass = "org.example.FileSizeDiffPlugin"
        }
    }
}
// end::plugin-id[]
// end::simple[]

// Created by gradle init

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {
}

configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Add a task to run the functional tests
val functionalTest by tasks.registering(Test::class) {
    description = "Runs functional tests."
    group = "verification"
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
