plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Implements a worker which can execute ant tasks in a separate worker process"

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(libs.inject)

    implementation(libs.slf4jApi)
}
