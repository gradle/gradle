plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Implements a worker which can execute ant tasks in a separate worker process"

dependencies {
    api(projects.ant)             // IsolatedAntBuilder, AntBuilderDelegate
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(libs.inject)

    implementation(libs.slf4jApi)
}
