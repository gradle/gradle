// tag::app-aggregation[]
plugins {
    id("application")
    id("jacoco")
}

dependencies {
    implementation(project(":lib1"))
    implementation(project(":lib2"))
}

tasks.register("aggregatedTestReport", JacocoAggregatedReport::class)
// end::app-aggregation[]

// tag::combined-test-report[]
tasks.register("combinedTestReport", JacocoAggregatedReport::class) {
    testCategories.set(listOf("test", "integrationTest"))
}
// end::combined-test-report[]
