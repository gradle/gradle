plugins {
    id("gradlebuild.incubation-report-aggregation")
}

dependencies {
    reports(platform(project(":distributions-full")))
}
