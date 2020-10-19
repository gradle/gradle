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
    jcenter()
}

dependencies {
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
    "integrationTestImplementation"("org.spockframework:spock-core:1.3-groovy-2.5")
    "functionalTestImplementation"("org.spockframework:spock-core:1.3-groovy-2.5")
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
