// tag::test-report[]
plugins {
    id("java")
}

// A resolvable configuration to collect test report data
val testReportData = jvm.createResolvableConfiguration("testReportData") {
    requiresAttributes {
        documentation("test-report-data")
    }
}

dependencies {
    testReportData(project(":core"))
    testReportData(project(":util"))
}

tasks.register<TestReport>("testReport") {
    destinationDir = file("$buildDir/reports/allTests")
    // Use test results from testReportData configuration
    (getTestResultDirs() as ConfigurableFileCollection).from(testReportData)
}
// end::test-report[]
