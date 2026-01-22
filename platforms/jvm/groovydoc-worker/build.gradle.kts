plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implements generation of groovydoc"

dependencies {
    api(projects.antWorker)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)

    implementation(projects.daemonServerWorker)
    implementation(libs.groovy)
    implementation(libs.guava)

    compileOnly(libs.jspecify)
}
