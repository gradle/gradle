plugins {
    java
    jacoco
}

allprojects {
    group = "org.gradle.multi.coverage"
    version = "1.0"
    repositories {
        mavenCentral()
    }

}

subprojects {
    apply(plugin = "java")

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    }

    // configure code coverage
    apply(plugin = "jacoco")
    val reportTask = task<JacocoReport>("codeCoverageReport")
    reportTask.sourceSets(sourceSets.main.get())
    tasks.test {
        useJUnitPlatform()
        reportTask.executionData(this)
    }
}

tasks.register<JacocoReport>("codeCoverageReport") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Generates code coverage report for all test tasks in all subprojects."
    reports() {
        xml.isEnabled = false
        html.isEnabled = true
        csv.isEnabled = false
    }
    subprojects.forEach { sub ->
        dependsOn(sub.getTasksByName("test", true))
    }
}
