// tag::test-report[]
subprojects {
    apply(plugin = "xctest")

    // Disable the test report for the individual test task
    tasks.named<Test>("test") {
        reports.html.isEnabled = false
    }
}

tasks.register<TestReport>("testReport") {
    destinationDir = file("$buildDir/reports/allTests")

    // Include the results from the `xcTest` task in all subprojects
    reportOn(subprojects.map { it.tasks["xcTest"] })
}
// end::test-report[]
