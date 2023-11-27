plugins {
    java
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    mavenCentral()
}

val integrationTest by sourceSets.creating

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.test {
    useJUnitPlatform()
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    useJUnitPlatform()

    testClassesDirs = integrationTest.output.classesDirs
    classpath = configurations[integrationTest.runtimeClasspathConfigurationName] + integrationTest.output

    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(integrationTestTask)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "integrationTestImplementation"(project)
}
