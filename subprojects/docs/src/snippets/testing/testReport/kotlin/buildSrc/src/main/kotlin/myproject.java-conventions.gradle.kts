// tag::test-report[]
plugins {
    id("java")
}

// Disable the test report for the individual test task
tasks.named<Test>("test") {
    reports.html.isEnabled = false
}

// Share the test report data to be aggregated for the whole project
jvm.createOutgoingElements("binaryTestRuntimeElements") {
    providesAttributes {
        documentation("test-report-data")
    }
    artifact(tasks.test.map { task -> task.getBinaryResultsDirectory().get() })
}
// end::test-report[]

repositories {
    mavenCentral()
}
