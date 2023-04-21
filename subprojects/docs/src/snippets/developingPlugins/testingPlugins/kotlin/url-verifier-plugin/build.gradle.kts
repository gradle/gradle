plugins {
    id("groovy")
    id("java-gradle-plugin")
}

group = "org.gradle.sample"
version = "1.0"

// tag::test-source-set[]
val integrationTest by sourceSets.creating

dependencies {
    "integrationTestImplementation"(project)
}
// end::test-source-set[]

// tag::test-task[]
val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs the integration tests."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    mustRunAfter(tasks.test)
}
tasks.check {
    dependsOn(integrationTestTask)
}
// end::test-task[]

val functionalTest by sourceSets.creating
dependencies {
    "functionalTestImplementation"(project)
}
val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    mustRunAfter(tasks.test, integrationTestTask)
}
tasks.check {
    dependsOn(functionalTestTask)
}

// tag::test-framework[]
repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.spockframework:spock-bom:2.2-groovy-3.0"))
    testImplementation("org.spockframework:spock-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "integrationTestImplementation"(platform("org.spockframework:spock-bom:2.2-groovy-3.0"))
    "integrationTestImplementation"("org.spockframework:spock-core")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")

    "functionalTestImplementation"(platform("org.spockframework:spock-bom:2.2-groovy-3.0"))
    "functionalTestImplementation"("org.spockframework:spock-core")
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    // Using JUnitPlatform for running tests
    useJUnitPlatform()
}
// end::test-framework[]

// tag::source-set-config[]
gradlePlugin {
    testSourceSets(functionalTest)
}
// end::source-set-config[]

gradlePlugin {
    plugins {
        create("urlVerifierPlugin") {
            id = "org.myorg.url-verifier"
            implementationClass = "org.myorg.UrlVerifierPlugin"
        }
    }
}
