// tag::test-report[]
tasks.register<TestReport>("testReport") {
    destinationDir = file("$buildDir/reports/allTests")

    // Include the results from the `xcTest` task in all subprojects
    reportOn(subprojects.map { it.tasks.withType<XCTest>() })
}
// end::test-report[]
