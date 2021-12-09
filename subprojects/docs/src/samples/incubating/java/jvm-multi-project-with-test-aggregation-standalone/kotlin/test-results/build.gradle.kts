plugins {
    base
    id("test-report-aggregation")
}

dependencies {
    testReportAggregation(project(":application")) // <1>
}

reporting {
    reports {
        val testAggregateTestReport by creating(AggregateTestReport::class) { // <2>
            testType.set(TestSuiteType.UNIT_TEST)
        }
    }
}

tasks.check {
    dependsOn(tasks.named<TestReport>("testAggregateTestReport")) // <3>
}
