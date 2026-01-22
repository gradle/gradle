plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Implements the work to generate scaladoc"

dependencies {
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(libs.inject)

    implementation(projects.baseServices)
}
