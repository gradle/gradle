plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implements generation of groovydoc"

dependencies {
    api(projects.ant)
    api(projects.antWorker)
    api(projects.baseServices)
    api(projects.coreApi)

    implementation(projects.daemonServerWorker)
    implementation(libs.groovy)
    implementation(libs.guava)

    compileOnly(libs.jspecify)
}
