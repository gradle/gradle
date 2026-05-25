plugins {
    base
    id("test-report-aggregation")
}

dependencies {
    testReportAggregation(project(":application")) // <.>
}

// tag::create_report[]
reporting {
    reports {
        create<AggregateTestReport>("testAggregateTestReport") { // <.>
            testSuiteName = "test"
        }
    }
}
// end::create_report[]

tasks.check {
    dependsOn(tasks.named<TestReport>("testAggregateTestReport")) // <.>
}
