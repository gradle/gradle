plugins {
    id("gradlebuild.incubation-report-aggregation")
}

dependencies {
    reports(platform("org.gradle:distributions-full"))
}
