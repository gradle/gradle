// tag::test-report[]
subprojects {
    apply(plugin = "java")

// end::test-report[]
    repositories {
        mavenCentral()
    }

    dependencies {
        "testCompile"("junit:junit:4.12")
    }

// tag::test-report[]
    // Disable the test report for the individual test task
    tasks.named<Test>("test") {
        reports.html.isEnabled = false
    }
}

tasks.register<TestReport>("testReport") {
    destinationDir = file("$buildDir/reports/allTests")
    // Include the results from the `test` task in all subprojects
    reportOn(subprojects.map { it.tasks["test"] })
}
// end::test-report[]
