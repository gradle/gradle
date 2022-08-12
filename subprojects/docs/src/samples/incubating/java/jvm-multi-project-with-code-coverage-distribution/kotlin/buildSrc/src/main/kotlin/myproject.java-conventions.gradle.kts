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
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

// Do not generate reports for individual projects
tasks.jacocoTestReport {
    enabled = false
}
