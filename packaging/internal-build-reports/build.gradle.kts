plugins {
    id("gradlebuild.incubation-report-aggregation")
    id("gradlebuild.removal-report-aggregation")
}

description = "The project to aggregate incubation reports from all subprojects"

dependencies {
    reports(platform(projects.distributionsFull))
    nextMajorRemovalReports(platform(projects.distributionsFull))
}
