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
    apply(plugin = "jacoco")

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
    }

    tasks.test {
        useJUnitPlatform()
    }

}

tasks.register<JacocoReport>("codeCoverageReport") {
    executionData(fileTree(project.rootDir.absolutePath).include("**/build/jacoco/*.exec"))
    subprojects.forEach {
        sourceSets(it.sourceSets.getByName("main"))
    }

    reports() {
        xml.isEnabled = false
        html.isEnabled = true
        csv.isEnabled = false
    }

}


val reportTask = tasks.get("codeCoverageReport")
getTasksByName("test", true).forEach {
    reportTask.dependsOn(it)
}
