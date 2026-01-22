plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implements generation of groovydoc"

dependencies {
    api(projects.antWorker)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api("org.apache.groovy:groovy")

    implementation(projects.daemonServerWorker)
    implementation("com.google.guava:guava")
    implementation("org.jspecify:jspecify")
}
