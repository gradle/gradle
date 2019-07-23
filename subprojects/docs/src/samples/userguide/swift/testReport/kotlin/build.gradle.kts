// tag::test-report[]
subprojects { 
    apply(plugin = "xctest")

    extensions.configure<SwiftXCTestSuite>() {
        binaries.configureEach {
            // Disable the test report for the individual test task
            runTask.get().reports.html.isEnabled = false
        }
    }
}

tasks.register<TestReport>("testReport") {
    destinationDir = file("$buildDir/reports/allTests")

    // Include the results from the `xcTest` task in all subprojects
    reportOn(subprojects.map { it.tasks.withType<XCTest>() })
}
// end::test-report[]
