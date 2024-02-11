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
        val testAggregateTestReport by creating(AggregateTestReport::class) { // <.>
            testType = TestSuiteType.UNIT_TEST
        }
    }
}
// end::create_report[]

tasks.check {
    dependsOn(tasks.named<TestReport>("testAggregateTestReport")) // <.>
}
