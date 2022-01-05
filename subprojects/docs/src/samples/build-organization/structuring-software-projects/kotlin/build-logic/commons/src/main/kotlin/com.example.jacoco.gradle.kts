plugins {
    id("java")
    id("jacoco")
}

// Do not generate reports for individual projects
tasks.jacocoTestReport.configure {
    enabled = false
}
