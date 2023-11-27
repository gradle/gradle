plugins {
    base
    id("jacoco-report-aggregation")
}

repositories {
    mavenCentral()
}

dependencies {
    jacocoAggregation(project(":application")) // <.>
}

// tag::create_report[]
reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) { // <.>
            testType = TestSuiteType.UNIT_TEST
        }
    }
}
// end::create_report[]

tasks.check {
    dependsOn(tasks.named<JacocoReport>("testCodeCoverageReport")) // <.>
}
