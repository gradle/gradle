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
    useJUnitPlatform()
}

tasks.check {
    dependsOn(functionalTestTask)
}

gradlePlugin {
    testSourceSets(functionalTest)
}

dependencies {
    "functionalTestImplementation"("org.spockframework:spock-core:2.0-M5-groovy-3.0") {
        exclude(group = "org.codehaus.groovy")
    }
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter-api")
}
// end::custom-test-source-set[]

repositories {
    mavenCentral()
}
