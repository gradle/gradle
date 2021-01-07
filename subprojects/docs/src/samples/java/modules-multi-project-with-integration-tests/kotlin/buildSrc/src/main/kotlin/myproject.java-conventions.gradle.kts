plugins {
    java
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    jcenter()
}

val integrationTest by sourceSets.creating

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.test {
    useJUnitPlatform()
}

val integrationTestJarTask = tasks.register<Jar>(integrationTest.jarTaskName) {
    archiveClassifier.set("integration-tests")
    from(integrationTest.output)
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    useJUnitPlatform()

    testClassesDirs = integrationTest.output.classesDirs
    // Make sure we run the 'Jar' containing the tests (and not just the 'classes' folder) so that test resources are also part of the test module
    classpath = configurations[integrationTest.runtimeClasspathConfigurationName] + files(integrationTestJarTask)

    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTestTask)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    "integrationTestImplementation"(project)
}
