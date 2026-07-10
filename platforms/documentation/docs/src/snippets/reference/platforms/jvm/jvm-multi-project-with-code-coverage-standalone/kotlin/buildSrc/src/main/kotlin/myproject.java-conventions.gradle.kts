plugins {
    java
    jacoco
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    mavenCentral()
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
    }
}

// Do not generate reports for individual projects
tasks.jacocoTestReport {
    enabled = false
}
