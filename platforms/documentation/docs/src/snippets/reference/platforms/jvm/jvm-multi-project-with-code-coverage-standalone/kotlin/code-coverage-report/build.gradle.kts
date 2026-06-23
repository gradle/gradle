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
        create<JacocoCoverageReport>("testCodeCoverageReport") { // <.>
            testSuiteName = "test"
        }
    }
}
// end::create_report[]

tasks.check {
    dependsOn(tasks.named<JacocoReport>("testCodeCoverageReport")) // <.>
}
