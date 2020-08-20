// Define conventions for service projects this organization.
// Service projects need to use the organization's Java conventions and pass some additional checks

// tag::plugins[]
plugins {
    id("myproject.java-conventions")
}
// end::plugins[]

// The organization requires integration tests
val integrationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    shouldRunAfter(tasks.named("test"))

    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
}

configurations {
    val testImplementation by getting
    val integrationTestImplementation by getting
    integrationTestImplementation.extendsFrom(testImplementation)

    val testRuntimeOnly by getting
    val integrationTestRuntimeOnly by getting
    integrationTestRuntimeOnly.extendsFrom(testRuntimeOnly)
}

// The organization requires additional documentation in the README for this project
// tag::use-java-class[]
val readmeCheck by tasks.registering(com.example.ReadmeVerificationTask::class) {
    readme.set(layout.projectDirectory.file("README.md"))
    readmePatterns.set(listOf("^## Service API$"))
}
// end::use-java-class[]

tasks.named("check") { dependsOn(integrationTestTask, readmeCheck) }
