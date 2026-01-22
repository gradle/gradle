plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api("javax.inject:javax.inject")

    implementation(projects.baseServices)
}
