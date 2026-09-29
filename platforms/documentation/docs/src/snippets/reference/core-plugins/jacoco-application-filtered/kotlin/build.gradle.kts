plugins {
    application
    jacoco
}

application {
    mainClass = "org.gradle.MyMain"
}

// tag::filter-single-task[]
val runTasks = tasks.withType<JavaExec>().matching { it.name == "run" }

jacoco {
    applyTo(runTasks)
}

val runCoverageData = files()
runTasks.configureEach {
    val jacocoExt = extensions.getByType<JacocoTaskExtension>()
    runCoverageData.from(provider { jacocoExt.destinationFile })
}

tasks.register<JacocoReport>("applicationCodeCoverageReport") {
    dependsOn(runTasks)
    executionData(runCoverageData)
    sourceSets(sourceSets.main.get())
}
// end::filter-single-task[]

repositories {
    mavenCentral()
}
