plugins {
    id("gradlebuild.incubation-report-aggregation")
}

description = "The project to aggregate incubation reports from all subprojects"

dependencies {
    reports(platform(project(":distributions-full")))
}
