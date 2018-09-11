// tag::application-configuration[]
plugins {
    application
    jacoco
}

application {
    mainClassName = "org.gradle.MyMain"
}

jacoco {
    applyTo(tasks["run"] as JavaExec)
}

task("applicationCodeCoverageReport", JacocoReport::class) {
    executionData(tasks["run"])
    sourceSets(sourceSets["main"])
}
// end::application-configuration[]

repositories {
    mavenCentral()
}
