// Define conventions for service projects this organization.
// Service projects need to use the organization's Java conventions and pass some additional checks

// tag::plugins[]
plugins {
    id("com.myorg.java-conventions")
}
// end::plugins[]

val integrationTest by sourceSets.creating

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

val integrationTestTask = tasks.register<Test>("integrationTest") {
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath

    shouldRunAfter(tasks.test)
}

dependencies {
    "integrationTestImplementation"(project)
}

// The organization requires additional documentation in the README for this project
// tag::use-java-class[]
val readmeCheck by tasks.registering(com.example.ReadmeVerificationTask::class) {
    readme = layout.projectDirectory.file("README.md")
    readmePatterns = listOf("^## Service API$")
}
// end::use-java-class[]

tasks.check { dependsOn(integrationTestTask, readmeCheck) }
