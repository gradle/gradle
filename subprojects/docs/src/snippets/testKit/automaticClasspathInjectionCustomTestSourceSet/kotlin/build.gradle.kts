// tag::custom-test-source-set[]
plugins {
    groovy
    `java-gradle-plugin`
}

val functionalTest = sourceSets.create("functionalTest")
val functionalTestTask = tasks.register<Test>("functionalTest") {
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    // useJUnitPlatform() // TODO The project is currently broken but fails silently (no tests found). When changing to JUnit Platform the build fails because @TempDir doesn't work in Spock 2.0-M4. We expect that this will be fixed in the next milestone.
}

tasks.check {
    dependsOn(functionalTestTask)
}

gradlePlugin {
    testSourceSets(functionalTest)
}

dependencies {
    "functionalTestImplementation"("org.spockframework:spock-core:2.0-M4-groovy-3.0") {
        exclude(group = "org.codehaus.groovy")
    }
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter-api")
}
// end::custom-test-source-set[]

repositories {
    mavenCentral()
}
