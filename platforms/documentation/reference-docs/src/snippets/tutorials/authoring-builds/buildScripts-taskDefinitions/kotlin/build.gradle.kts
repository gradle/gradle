plugins {
    id("java")
}

val functionalTestSourceSet = sourceSets.create("functionalTest")

// tag::task-definitions[]
tasks.register<Test>("functionalTest") {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    // Use JUnit Jupiter for unit tests.
    useJUnitPlatform()
}
// end::task-definitions[]
