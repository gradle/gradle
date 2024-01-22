plugins {
    id("java-library")
    id("jacoco")
}

// Do not generate reports for individual projects
tasks.jacocoTestReport.configure {
    enabled = false
}
